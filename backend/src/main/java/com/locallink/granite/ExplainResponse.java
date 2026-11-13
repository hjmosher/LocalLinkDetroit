package com.locallink.granite;

/** One-sentence explanation returned from GraniteClient.explain(). */
public record ExplainResponse(
        String sentence,    // plain text, max 220 chars
        String source       // "granite" | "template"
) {}
