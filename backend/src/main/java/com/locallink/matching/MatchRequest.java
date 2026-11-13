package com.locallink.matching;

import java.util.List;

/** Request body for POST /api/matches. */
public record MatchRequest(
        String city,
        List<String> skillIds,
        Double radiusMiles   // nullable — defaults to 40.0 if absent
) {}
