package com.locallink.granite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.locallink.model.Skill;
import com.locallink.seed.SeedStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Live GraniteClient — calls IBM watsonx.ai using the Granite instruct model.
 *
 * Activation: GRANITE_MODE=live
 * Auth:       IamTokenService (API-key → IAM bearer token, cached with pre-expiry refresh)
 * Endpoint:   {WATSONX_URL}/ml/v1/text/generation?version=2024-05-31
 *
 * Per-method behaviour (see docs/GRANITE_PROMPTS.md):
 *   decompose  → structured JSON, temperature 0, max 400 tokens, validate + retry + keyword fallback
 *   explain    → plain text,  temperature 0, max 120 tokens, validate length + retry + template
 *   draftIntro → plain text,  temperature 0, max 250 tokens, retry on empty + template
 *
 * Rules (AGENTS.md):
 *   - Never set tier/score/badges/warnings — those come from code.
 *   - Validate model JSON; retry once on bad output; fall back and set source accordingly.
 *   - Never log API keys, tokens, or personal data — only sizes.
 *   - Cache identical decompose and explain calls to speed up demos.
 *   - Input text capped at 1,000 chars.
 */
public class WatsonxGraniteClient implements GraniteClient {

    private static final Logger log = LoggerFactory.getLogger(WatsonxGraniteClient.class);

    // Prompt Lab "View code" shape — text generation endpoint
    private static final String GENERATION_PATH = "/ml/v1/text/generation?version=2024-05-31";
    private static final int MAX_INPUT_CHARS = 1_000;
    private static final int MAX_SKILLS = 5;
    private static final int MAX_REASON_CHARS = 120;
    private static final int MAX_EXPLAIN_CHARS = 220;

    // Simple in-memory cache for identical inputs (key → response)
    private final Map<String, DecomposeResponse> decomposeCache = new ConcurrentHashMap<>();
    private final Map<String, ExplainResponse>   explainCache   = new ConcurrentHashMap<>();

    private final String watsonxUrl;
    private final String projectId;
    private final String modelId;
    private final IamTokenService tokenService;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final SeedStore store;
    // Fallback delegate — reuses keyword logic and template logic from MockGraniteClient
    private final MockGraniteClient fallback;

    public WatsonxGraniteClient(
            String watsonxUrl,
            String projectId,
            String modelId,
            IamTokenService tokenService,
            HttpClient httpClient,
            ObjectMapper mapper,
            SeedStore store) {
        this.watsonxUrl   = watsonxUrl.endsWith("/") ? watsonxUrl.substring(0, watsonxUrl.length() - 1) : watsonxUrl;
        this.projectId    = projectId;
        this.modelId      = modelId;
        this.tokenService = tokenService;
        this.httpClient   = httpClient;
        this.mapper       = mapper;
        this.store        = store;
        this.fallback     = new MockGraniteClient(store, mapper);
    }

    // =========================================================================
    // decompose
    // =========================================================================

    /**
     * Maps owner description → skill ids.
     *
     * Prompt: docs/GRANITE_PROMPTS.md §1.
     * Validation: every skillId in taxonomy, ≤5, reason ≤ 120 chars,
     *             if skills empty → clarifyingQuestion non-empty.
     * On invalid output: retry once, then keyword fallback (source="fallback").
     */
    @Override
    public DecomposeResponse decompose(DecomposeRequest request) {
        String text = sanitize(request.text());
        String city = request.city() == null ? "" : request.city();
        String cacheKey = text + "|" + city;

        DecomposeResponse cached = decomposeCache.get(cacheKey);
        if (cached != null) {
            log.debug("decompose cache hit, chars={}", text.length());
            return cached;
        }

        String prompt = buildDecomposePrompt(text, city);
        String raw = callModel(prompt, 400);
        if (raw == null) {
            log.warn("decompose: model call failed, using keyword fallback");
            return fallback.decompose(request);
        }

        DecomposeResponse result = parseDecompose(raw, request);
        if (result == null) {
            // Retry once
            log.info("decompose: invalid JSON on first attempt, retrying");
            raw = callModel(prompt, 400);
            result = raw != null ? parseDecompose(raw, request) : null;
        }
        if (result == null) {
            log.warn("decompose: retry also invalid, using keyword fallback");
            return fallback.decompose(request);
        }

        // source is already set by parseDecompose ("granite")
        decomposeCache.put(cacheKey, result);
        return result;
    }

    // =========================================================================
    // explain
    // =========================================================================

    /**
     * One sentence explaining why the provider matches the need.
     *
     * Prompt: docs/GRANITE_PROMPTS.md §2.
     * Validation: non-empty and ≤ 220 chars.
     * On invalid: retry once, then template fallback (source="template").
     */
    @Override
    public ExplainResponse explain(ExplainContext ctx) {
        // Include badges and warnings in the key: a provider's trust facts can differ
        // across skills, so a stale cached sentence must never contradict current facts.
        String cacheKey = ctx.skillLabel() + "|" + ctx.providerName() + "|" + ctx.distanceMiles()
                + "|" + ctx.badges()
                + "|" + ctx.warnings();

        ExplainResponse cached = explainCache.get(cacheKey);
        if (cached != null) {
            log.debug("explain cache hit for provider={}", ctx.providerName());
            return cached;
        }

        String prompt = buildExplainPrompt(ctx);
        String raw = callModel(prompt, 120);
        if (raw == null) {
            return fallback.explain(ctx);
        }

        ExplainResponse result = parseExplain(raw, ctx);
        if (result == null) {
            log.info("explain: invalid output on first attempt, retrying");
            raw = callModel(prompt, 120);
            result = raw != null ? parseExplain(raw, ctx) : null;
        }
        if (result == null) {
            log.warn("explain: retry also invalid, using template");
            return fallback.explain(ctx);
        }

        explainCache.put(cacheKey, result);
        return result;
    }

    // =========================================================================
    // draftIntro
    // =========================================================================

    /**
     * Short intro message from one owner to another.
     *
     * Prompt: docs/GRANITE_PROMPTS.md §3.
     * Validation: non-empty text.
     * On invalid: retry once, then template fallback (source="template").
     * Not cached — intros are always personalised.
     */
    @Override
    public IntroResponse draftIntro(IntroRequest request) {
        // Single lookup — avoid iterating the provider list twice
        com.locallink.model.Provider provider = store.providers().stream()
                .filter(p -> p.id().equals(request.providerId()))
                .findFirst()
                .orElse(null);
        String providerName    = provider != null ? provider.name()    : "the provider";
        String providerSummary = provider != null ? provider.summary() : "";
        String skillLabel = store.skill(request.skillId())
                .map(Skill::label)
                .orElse(request.skillId());

        String needText = sanitize(request.needText());
        String prompt = buildIntroPrompt(needText, skillLabel, request.city(), providerName, providerSummary);
        String raw = callModel(prompt, 250);
        if (raw == null) {
            return fallback.draftIntro(request);
        }

        String body = raw.strip();
        if (body.isEmpty()) {
            log.info("draftIntro: empty response on first attempt, retrying");
            raw = callModel(prompt, 250);
            body = raw != null ? raw.strip() : "";
        }
        if (body.isEmpty()) {
            log.warn("draftIntro: retry also empty, using template");
            return fallback.draftIntro(request);
        }

        return new IntroResponse(body, "granite");
    }

    // =========================================================================
    // HTTP layer — calls the watsonx.ai text generation endpoint
    // =========================================================================

    /**
     * Calls the watsonx.ai text/generation endpoint synchronously.
     *
     * Returns the model's generated_text, or null on any error (never throws).
     * Logs only prompt and response sizes — never content.
     */
    private String callModel(String prompt, int maxNewTokens) {
        String token;
        try {
            token = tokenService.getToken();
        } catch (IamTokenException e) {
            log.error("callModel: IAM token unavailable: {}", e.getMessage());
            return null;
        }

        // Build the request body per watsonx.ai Prompt Lab "View code" shape
        Map<String, Object> body = buildRequestBody(prompt, maxNewTokens);
        String bodyJson;
        try {
            bodyJson = mapper.writeValueAsString(body);
        } catch (IOException e) {
            log.error("callModel: failed to serialize request body", e);
            return null;
        }

        String url = watsonxUrl + GENERATION_PATH;
        // 15 s per call: enough for Granite at temperature 0, short prompts.
        // Ensures the fallback runs instead of parking the thread indefinitely.
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                .build();

        log.debug("callModel: POST {} prompt_chars={} max_new_tokens={}", url, prompt.length(), maxNewTokens);

        HttpResponse<String> response;
        try {
            response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.error("callModel: HTTP error: {}", e.getMessage());
            return null;
        }

        if (response.statusCode() != 200) {
            log.error("callModel: HTTP {} from watsonx endpoint", response.statusCode());
            return null;
        }

        return extractGeneratedText(response.body());
    }

    /** Builds the JSON request body matching the watsonx.ai text generation API. */
    private Map<String, Object> buildRequestBody(String prompt, int maxNewTokens) {
        // Shape from Prompt Lab "View code" panel:
        // POST /ml/v1/text/generation?version=2024-05-31
        // { "model_id": "...", "project_id": "...",
        //   "input": "<prompt>",
        //   "parameters": { "decoding_method": "greedy", "max_new_tokens": N,
        //                   "temperature": 0 } }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("decoding_method", "greedy");
        params.put("max_new_tokens", maxNewTokens);
        params.put("temperature", 0);

        Map<String, Object> reqBody = new LinkedHashMap<>();
        reqBody.put("model_id", modelId);
        reqBody.put("project_id", projectId);
        reqBody.put("input", prompt);
        reqBody.put("parameters", params);
        return reqBody;
    }

    /**
     * Extracts the generated text from the watsonx.ai response envelope.
     * Shape: {"results": [{"generated_text": "..."}]}
     */
    private String extractGeneratedText(String responseBody) {
        try {
            JsonNode root = mapper.readTree(responseBody);
            JsonNode results = root.path("results");
            if (results.isArray() && !results.isEmpty()) {
                String text = results.get(0).path("generated_text").asText(null);
                if (text != null) {
                    log.debug("callModel: response generated_text_chars={}", text.length());
                    return text.strip();
                }
            }
            log.error("callModel: unexpected response shape, missing results[0].generated_text");
            return null;
        } catch (IOException e) {
            log.error("callModel: cannot parse response JSON: {}", e.getMessage());
            return null;
        }
    }

    // =========================================================================
    // Prompt builders  (see docs/GRANITE_PROMPTS.md)
    // =========================================================================

    private String buildDecomposePrompt(String needText, String city) {
        String skillList = store.skills().stream()
                .map(s -> s.id() + ": " + s.label())
                .collect(Collectors.joining("\n"));

        return "<|system|>\n" +
               "You map a small-business owner's description of work they need done onto a fixed list of skills. " +
               "Use only skill ids from the list provided. " +
               "Choose between 1 and 5 skills that the description clearly calls for. " +
               "Do not add skills the description does not mention or clearly imply. " +
               "Do not judge licenses, prices or providers. " +
               "If the description is too vague to map, return an empty skills list and a short clarifyingQuestion. " +
               "Reply with JSON only.\n" +
               "<|user|>\n" +
               "SKILLS (id: label):\n" + skillList + "\n\n" +
               "CITY: " + city + "\n\n" +
               "DESCRIPTION:\n" + needText + "\n\n" +
               "Return JSON exactly in this shape:\n" +
               "{\"summary\": \"<one sentence, max 20 words>\",\n" +
               " \"skills\": [{\"skillId\": \"<id from the list>\", \"reason\": \"<max 15 words, based on the description>\"}],\n" +
               " \"clarifyingQuestion\": null}\n" +
               "<|assistant|>\n";
    }

    private String buildExplainPrompt(ExplainContext ctx) {
        String badges = ctx.badges() == null || ctx.badges().isEmpty()
                ? "none" : String.join("; ", ctx.badges());
        String warnings = ctx.warnings() == null || ctx.warnings().isEmpty()
                ? "none" : String.join("; ", ctx.warnings());

        return "<|system|>\n" +
               "You write one short sentence (max 30 words) explaining why a business is a good fit for a need. " +
               "Use ONLY the facts provided. " +
               "Do not mention reviews, quality, price, years in business or anything not in the facts. " +
               "State trust status exactly as given. " +
               "If warnings are provided, do not hide them. Plain, neutral tone.\n" +
               "<|user|>\n" +
               "NEED SKILL: " + ctx.skillLabel() + "\n" +
               "BUSINESS: " + ctx.providerName() + " (" + ctx.structure() + "), " +
               ctx.city() + ", " + ctx.distanceMiles() + " miles away\n" +
               "OFFERS: " + ctx.summary() + "\n" +
               "AVAILABILITY: " + ctx.availability() + "\n" +
               "TRUST BADGES: " + badges + "\n" +
               "WARNINGS: " + warnings + "\n" +
               "Write the sentence.\n" +
               "<|assistant|>\n";
    }

    private String buildIntroPrompt(String needText, String skillLabel,
                                     String city, String providerName, String providerSummary) {
        return "<|system|>\n" +
               "You draft a short, polite first message from one small-business owner to another. " +
               "Max 110 words. Use only the facts given. " +
               "Do not include phone numbers, emails, addresses or prices. Do not promise anything. " +
               "Mention the timeframe only if the description gives one. " +
               "End with the placeholder [Your name]. Output only the message text.\n" +
               "<|user|>\n" +
               "NEED (owner's words): " + needText + "\n" +
               "SKILL REQUESTED: " + skillLabel + "\n" +
               "CITY: " + city + "\n" +
               "RECIPIENT: " + providerName + ", which offers: " + providerSummary + "\n" +
               "<|assistant|>\n";
    }

    // =========================================================================
    // Response validators
    // =========================================================================

    /**
     * Parses and validates a decompose response.
     * Rules (GRANITE_PROMPTS.md §1 Validation):
     *   - Every skillId must exist in the taxonomy (drop unknown ids).
     *   - At most 5 skills.
     *   - reason ≤ 120 chars.
     *   - If skills empty, clarifyingQuestion must be non-empty.
     *
     * Returns null if the JSON is structurally unparseable.
     */
    private DecomposeResponse parseDecompose(String raw, DecomposeRequest request) {
        try {
            // Model may wrap the JSON in a markdown fence — strip it
            String json = extractJson(raw);
            JsonNode node = mapper.readTree(json);

            String summary = node.path("summary").asText("").strip();
            String clarifying = node.path("clarifyingQuestion").isNull()
                    ? null : node.path("clarifyingQuestion").asText(null);

            List<DecomposeResponse.SkillMatch> skills = new ArrayList<>();
            JsonNode skillsNode = node.path("skills");
            if (skillsNode.isArray()) {
                for (JsonNode s : skillsNode) {
                    if (skills.size() >= MAX_SKILLS) break;
                    String skillId = s.path("skillId").asText("").strip();
                    String reason  = s.path("reason").asText("").strip();
                    if (skillId.isEmpty()) continue;
                    Optional<Skill> skill = store.skill(skillId);
                    if (skill.isEmpty()) {
                        log.debug("decompose: dropping unknown skillId '{}' from model output", skillId);
                        continue;
                    }
                    if (reason.length() > MAX_REASON_CHARS) {
                        reason = reason.substring(0, MAX_REASON_CHARS);
                    }
                    skills.add(new DecomposeResponse.SkillMatch(
                            skillId, skill.get().label(), reason, skill.get().regulatedHint()));
                }
            }

            // Validate: empty skills → need clarifying question
            if (skills.isEmpty() && (clarifying == null || clarifying.isBlank())) {
                clarifying = "Could you describe the specific work you need help with?";
            }

            if (summary.isEmpty()) {
                summary = skills.isEmpty()
                        ? "Need in " + request.city() + " — please clarify"
                        : skills.size() + " skill" + (skills.size() == 1 ? "" : "s") + " identified for " + request.city();
            }

            return new DecomposeResponse(summary, skills, clarifying, "granite");
        } catch (Exception e) {
            log.debug("decompose: JSON parse error: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Parses and validates an explain response.
     * Valid if non-empty and ≤ 220 chars.
     * Returns null (triggering fallback/template) on invalid output.
     */
    private ExplainResponse parseExplain(String raw, ExplainContext ctx) {
        String sentence = raw.strip().replaceAll("^\"|\"$", "").replaceAll("\\n", " ").strip();
        if (sentence.isEmpty()) {
            log.debug("explain: empty sentence from model");
            return null;
        }
        if (sentence.length() > MAX_EXPLAIN_CHARS) {
            // Per spec: if longer than 220 chars, fall back to template
            log.debug("explain: sentence too long ({} chars), using template", sentence.length());
            return null;
        }
        return new ExplainResponse(sentence, "granite");
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    /** Caps input at 1,000 chars and trims (security rule from GRANITE_PROMPTS.md). */
    private static String sanitize(String text) {
        if (text == null) return "";
        String t = text.trim();
        return t.length() > MAX_INPUT_CHARS ? t.substring(0, MAX_INPUT_CHARS) : t;
    }

    /**
     * Strips a markdown code fence if the model wrapped its JSON output in one.
     * e.g. ```json\n{...}\n``` → {…}
     */
    private static String extractJson(String raw) {
        String s = raw.strip();
        if (s.startsWith("```")) {
            int first = s.indexOf('\n');
            int last  = s.lastIndexOf("```");
            if (first != -1 && last > first) {
                return s.substring(first + 1, last).strip();
            }
        }
        return s;
    }
}
