package com.locallink.model;

/**
 * Immutable record for a skill from skills_taxonomy.json.
 * regulated_hint means work in this category MAY require a state license.
 */
public record Skill(
        String id,
        String label,
        String grp,
        boolean regulatedHint
) {}
