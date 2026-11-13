package com.locallink.matching;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * POST /api/matches — returns ranked providers per skill.
 * Matching and trust logic lives entirely in MatchingService; this layer only validates input.
 */
@RestController
@RequestMapping("/api/matches")
public class MatchController {

    private final MatchingService matchingService;

    @Autowired
    public MatchController(MatchingService matchingService) {
        this.matchingService = matchingService;
    }

    @PostMapping
    public ResponseEntity<?> matches(@RequestBody MatchRequest request) {
        if (request.city() == null || request.city().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "MISSING_CITY", "message", "city is required"));
        }
        if (request.skillIds() == null || request.skillIds().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "MISSING_SKILLS", "message", "skillIds must not be empty"));
        }

        try {
            List<MatchResponse.SkillGroup> groups =
                    matchingService.match(request.city(), request.skillIds(), request.radiusMiles());
            return ResponseEntity.ok(new MatchResponse(groups));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "UNKNOWN_CITY", "message", e.getMessage()));
        }
    }
}
