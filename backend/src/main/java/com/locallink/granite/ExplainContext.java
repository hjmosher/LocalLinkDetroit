package com.locallink.granite;

import java.util.List;

/**
 * All facts needed to generate one-sentence match explanation.
 * The model (or template) uses ONLY what is supplied here — no external lookups.
 * See docs/GRANITE_PROMPTS.md §2.
 */
public record ExplainContext(
        String skillLabel,
        String providerName,
        String structure,
        String city,
        double distanceMiles,
        String summary,
        String availability,
        List<String> badges,
        List<String> warnings
) {}
