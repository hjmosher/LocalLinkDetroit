package com.locallink.model;

import java.util.List;

/**
 * Immutable record for a license entry from providers.json.
 * state_verified is simulated — never read from any real registry.
 */
public record License(
        String type,
        List<String> skills,
        boolean stateVerified
) {}
