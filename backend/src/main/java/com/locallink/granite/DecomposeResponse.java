package com.locallink.granite;

import java.util.List;

/**
 * Response body for POST /api/needs/decompose.
 * Matches the API contract in docs/ARCHITECTURE.md.
 */
public record DecomposeResponse(
        String summary,
        List<SkillMatch> skills,
        String clarifyingQuestion,  // non-null only when skills is empty
        String source               // "granite" | "fallback"
) {

    /** One skill in the decomposition result. */
    public record SkillMatch(
            String skillId,
            String label,
            String reason,
            boolean regulatedHint
    ) {}
}
