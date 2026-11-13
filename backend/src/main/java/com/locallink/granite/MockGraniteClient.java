package com.locallink.granite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.locallink.model.Skill;
import com.locallink.seed.SeedStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Mock GraniteClient — works with no network or API key (GRANITE_MODE=mock, the default).
 *
 * decompose:   keyword hit-count over seed/keyword_fallback.json, top 5 by hits, then alpha.
 * explain:     template: "{name} offers {skill} about {distance} miles away. {first badge}."
 *              plus " Note: {first warning}" if any.
 * draftIntro:  template from docs/GRANITE_PROMPTS.md §3 fallback template.
 *
 * All three methods are guaranteed never to throw.
 */
public class MockGraniteClient implements GraniteClient {

    private static final Logger log = LoggerFactory.getLogger(MockGraniteClient.class);
    private static final int MAX_SKILLS = 5;

    /** keyword → list-of-skill-ids (one keyword can appear for multiple skills in theory) */
    private final Map<String, List<String>> keywordToSkills = new LinkedHashMap<>();

    private final SeedStore store;

    public MockGraniteClient(SeedStore store, ObjectMapper mapper) {
        this.store = store;
        loadFallbackKeywords(mapper);
    }

    // -------------------------------------------------------------------------
    // decompose
    // -------------------------------------------------------------------------

    /**
     * Keyword fallback decomposition.
     *
     * Algorithm (matches docs/ARCHITECTURE.md §Fallback decomposition):
     * 1. Lowercase the description.
     * 2. For each keyword in the map, count how many times any keyword for a skill matches.
     * 3. Rank skill ids by hit count desc; ties broken alphabetically for determinism.
     * 4. Return top MAX_SKILLS with reason "Mentions '<keyword>'".
     * 5. source = "fallback".
     *
     * Note need-2 guard: "landscaping_hardscape" is intentionally absent from the keyword
     * list for terms that the owner's own business uses ("landscaping company") — the
     * keyword_fallback.json uses "patio", "walkway", etc. not "landscaping" itself, so
     * the golden-need constraint is satisfied naturally by the data file.
     */
    @Override
    public DecomposeResponse decompose(DecomposeRequest request) {
        // Cap input length per security note in GRANITE_PROMPTS.md
        String text = request.text() == null ? "" : request.text();
        if (text.length() > 1000) text = text.substring(0, 1000);
        String lower = text.toLowerCase();

        // Count hits per skill: skillId → (hitCount, first matching keyword)
        Map<String, int[]> hitCount = new LinkedHashMap<>();
        Map<String, String> firstKeyword = new LinkedHashMap<>();

        for (Map.Entry<String, List<String>> entry : keywordToSkills.entrySet()) {
            String keyword = entry.getKey();
            List<String> skillIds = entry.getValue();
            if (lower.contains(keyword)) {
                for (String skillId : skillIds) {
                    hitCount.merge(skillId, new int[]{1}, (a, b) -> new int[]{a[0] + 1});
                    firstKeyword.putIfAbsent(skillId, keyword);
                }
            }
        }

        // Sort: hits desc, then skillId asc for determinism
        List<String> ranked = new ArrayList<>(hitCount.keySet());
        ranked.sort(Comparator
                .comparingInt((String id) -> -hitCount.get(id)[0])
                .thenComparing(id -> id));

        // Build result list — keep only skill ids that exist in taxonomy
        List<DecomposeResponse.SkillMatch> skills = new ArrayList<>();
        for (String skillId : ranked) {
            if (skills.size() >= MAX_SKILLS) break;
            Optional<Skill> skill = store.skill(skillId);
            if (skill.isEmpty()) continue; // unknown id — skip (taxonomy may not have it)
            String reason = "Mentions '" + firstKeyword.get(skillId) + "'";
            skills.add(new DecomposeResponse.SkillMatch(
                    skillId,
                    skill.get().label(),
                    reason,
                    skill.get().regulatedHint()
            ));
        }

        String clarifyingQuestion = skills.isEmpty()
                ? "Could you describe the specific work you need help with?" : null;

        // Build a simple summary from the city + number of skills found
        String city = request.city() == null ? "" : request.city();
        String summary = skills.isEmpty()
                ? "Need in " + city + " — please clarify"
                : skills.size() + " skill" + (skills.size() == 1 ? "" : "s") +
                  " identified for " + city;

        log.debug("Mock decompose: {} skills from {} chars of text", skills.size(), text.length());
        return new DecomposeResponse(summary, skills, clarifyingQuestion, "fallback");
    }

    // -------------------------------------------------------------------------
    // explain
    // -------------------------------------------------------------------------

    /**
     * Template explanation as specified in docs/GRANITE_PROMPTS.md §2 fallback:
     *   "{name} offers {skill label} about {distance} miles away. {first badge}."
     *   plus " Note: {first warning}" if any warning present.
     *
     * Also the guard: if sentence > 220 chars or empty, the same template is the fallback.
     */
    @Override
    public ExplainResponse explain(ExplainContext ctx) {
        String sentence = buildExplainTemplate(ctx);
        return new ExplainResponse(sentence, "template");
    }

    // -------------------------------------------------------------------------
    // draftIntro
    // -------------------------------------------------------------------------

    /**
     * Fallback template from docs/GRANITE_PROMPTS.md §3:
     *   "Hi {name} team, I found you on LocalLink Detroit. I'm looking for help with
     *    {skill label} in {city}. {first sentence of needText}. Are you available to
     *    talk about it? Thanks, [Your name]"
     */
    @Override
    public IntroResponse draftIntro(IntroRequest request) {
        // Resolve provider name and skill label from the store
        String providerName = store.providers().stream()
                .filter(p -> p.id().equals(request.providerId()))
                .map(p -> p.name())
                .findFirst()
                .orElse("the team");

        String skillLabel = store.skill(request.skillId())
                .map(Skill::label)
                .orElse(request.skillId());

        String city = request.city() == null ? "" : request.city();

        // Extract first sentence from needText (up to first period, '!', '?', or 100 chars)
        String needText = request.needText() == null ? "" : request.needText().trim();
        String firstSentence = extractFirstSentence(needText);

        String body = "Hi " + providerName + " team, I found you on LocalLink Detroit. " +
                "I'm looking for help with " + skillLabel + " in " + city + ". " +
                firstSentence +
                (firstSentence.isEmpty() ? "" : " ") +
                "Are you available to talk about it? Thanks, [Your name]";

        log.debug("Mock draftIntro: provider={}, skill={}", request.providerId(), request.skillId());
        return new IntroResponse(body, "template");
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static String buildExplainTemplate(ExplainContext ctx) {
        String firstBadge = ctx.badges() != null && !ctx.badges().isEmpty()
                ? ctx.badges().get(0) : "Self-declared";
        String base = ctx.providerName() + " offers " + ctx.skillLabel() +
                " about " + ctx.distanceMiles() + " miles away. " + firstBadge + ".";

        if (!ctx.warnings().isEmpty()) {
            base = base + " Note: " + ctx.warnings().get(0);
        }

        // Guard: truncate to 220 chars max (spec says if longer, use template — which we already are)
        if (base.length() > 220) {
            base = base.substring(0, 217) + "...";
        }
        return base;
    }

    /** Returns the first sentence (up to first .!? or the whole string if none). */
    private static String extractFirstSentence(String text) {
        if (text == null || text.isEmpty()) return "";
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '.' || c == '!' || c == '?') {
                return text.substring(0, i + 1);
            }
        }
        // No sentence boundary — return up to 100 chars to keep intro concise
        return text.length() <= 100 ? text : text.substring(0, 100);
    }

    /**
     * Load seed/keyword_fallback.json from the classpath.
     * Maps each keyword → list of skill ids.
     * Called once at construction; fail-fast with unchecked if the file is missing (bug).
     */
    private void loadFallbackKeywords(ObjectMapper mapper) {
        try {
            JsonNode root = mapper.readTree(
                    new ClassPathResource("seed/keyword_fallback.json").getInputStream());
            JsonNode keywords = root.get("keywords");
            Iterator<Map.Entry<String, JsonNode>> fields = keywords.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String skillId = entry.getKey();
                for (JsonNode kw : entry.getValue()) {
                    String keyword = kw.asText().toLowerCase();
                    keywordToSkills.computeIfAbsent(keyword, k -> new ArrayList<>()).add(skillId);
                }
            }
            log.info("MockGraniteClient: loaded {} keyword entries", keywordToSkills.size());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load seed/keyword_fallback.json", e);
        }
    }
}
