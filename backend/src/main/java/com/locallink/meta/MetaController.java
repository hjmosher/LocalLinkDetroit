package com.locallink.meta;

import com.locallink.seed.SeedStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * GET /api/health  — liveness check; exposes the active Granite mode.
 * GET /api/meta    — cities, skills, and demoData flag used by the frontend
 *                    to populate the city dropdown and skill chips.
 *
 * Both endpoints are described in docs/ARCHITECTURE.md.
 */
@RestController
@RequestMapping("/api")
public class MetaController {

    private final SeedStore store;
    /** "mock" or "live" — resolved once at startup by GraniteConfig. */
    private final String graniteMode;

    @Autowired
    public MetaController(SeedStore store, String graniteMode) {
        this.store = store;
        this.graniteMode = graniteMode;
    }

    /**
     * GET /api/health
     * → { "status": "ok", "graniteMode": "mock|live" }
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "graniteMode", graniteMode);
    }

    /**
     * GET /api/meta
     * → { "cities": [ { "name": "Royal Oak" } ],
     *     "skills": [ { "id": "...", "label": "...", "group": "...", "regulatedHint": false } ],
     *     "demoData": true }
     *
     * The frontend uses city name only; lat/lon are resolved on the backend per request.
     */
    @GetMapping("/meta")
    public Map<String, Object> meta() {
        List<Map<String, String>> cities = store.cities().keySet().stream()
                .map(name -> Map.of("name", name))
                .toList();

        List<Map<String, Object>> skills = store.skills().stream()
                .map(s -> Map.<String, Object>of(
                        "id",            s.id(),
                        "label",         s.label(),
                        "group",         s.grp(),
                        "regulatedHint", s.regulatedHint()
                ))
                .toList();

        return Map.of(
                "cities",   cities,
                "skills",   skills,
                "demoData", true
        );
    }
}
