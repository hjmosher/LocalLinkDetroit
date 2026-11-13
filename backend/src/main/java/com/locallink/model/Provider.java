package com.locallink.model;

import java.util.List;

/**
 * Immutable record for a provider from providers.json.
 * All data is fictional — no real businesses.
 */
public record Provider(
        String id,
        String name,
        String structure,
        String city,
        double lat,
        double lon,
        int serviceRadiusMiles,
        String summary,
        String laraStatus,     // active_good_standing | not_in_good_standing | not_applicable
        String availability,   // this_month | next_month | flexible
        List<String> skills,   // ordered list; skills[0] is the primary skill
        List<License> licenses
) {}
