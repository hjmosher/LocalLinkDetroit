package com.locallink.granite;

/**
 * AI abstraction layer. The model only maps text → skill ids, writes one-sentence
 * explanations from supplied facts, and drafts intro messages.
 *
 * Rules:
 * - Never sets tier, score, badges, warnings or ordering (those are determined by code).
 * - On any failure, implementations MUST return a fallback result, not throw.
 * - source field values: "granite" | "fallback" | "template"
 *
 * See docs/GRANITE_PROMPTS.md for prompt templates and output schemas.
 */
public interface GraniteClient {

    /**
     * Decompose a free-text need into up to 5 skill ids with reasons.
     *
     * @param request needText + city
     * @return decomposition result; source = "granite" | "fallback"
     */
    DecomposeResponse decompose(DecomposeRequest request);

    /**
     * Write one sentence explaining why a provider matches a skill need.
     * Uses only the facts supplied in the context (no outside knowledge).
     *
     * @param context all facts needed for the sentence
     * @return plain-text sentence + source = "granite" | "template"
     */
    ExplainResponse explain(ExplainContext context);

    /**
     * Draft a short intro message from one owner to another.
     * Never sends; the user edits and approves.
     *
     * @param request needText + skillId + city + providerId
     * @return draft body + source = "granite" | "template"
     */
    IntroResponse draftIntro(IntroRequest request);
}
