package com.locallink.matching;

import java.util.List;

/** Response body for POST /api/matches. */
public record MatchResponse(List<SkillGroup> groups) {

    public record SkillGroup(
            String skillId,
            String label,
            boolean regulatedHint,
            List<MatchResult> matches
    ) {}

    public record MatchResult(
            String providerId,
            String name,
            String structure,
            String city,
            double distanceMiles,
            String tier,
            double score,
            List<String> badges,
            List<String> warnings,
            String summary,
            String availability,
            String explanation,        // one sentence from GraniteClient
            String explanationSource   // "granite" | "template"
    ) {}
}
