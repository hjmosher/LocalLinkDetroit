package com.locallink.intro;

import com.locallink.granite.GraniteClient;
import com.locallink.granite.IntroRequest;
import com.locallink.granite.IntroResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * POST /api/intros/draft — draft an intro message to a provider.
 *
 * The draft is never sent; the user edits and approves it in the UI.
 * Delegates to GraniteClient; in mock mode this fills a template.
 */
@RestController
@RequestMapping("/api/intros")
public class IntroController {

    private final GraniteClient graniteClient;

    @Autowired
    public IntroController(GraniteClient graniteClient) {
        this.graniteClient = graniteClient;
    }

    @PostMapping("/draft")
    public ResponseEntity<?> draft(@RequestBody IntroRequest request) {
        if (request.providerId() == null || request.providerId().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "MISSING_PROVIDER", "message", "providerId is required"));
        }
        if (request.skillId() == null || request.skillId().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "MISSING_SKILL", "message", "skillId is required"));
        }
        if (request.needText() == null || request.needText().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "MISSING_TEXT", "message", "needText is required"));
        }

        IntroResponse response = graniteClient.draftIntro(request);
        return ResponseEntity.ok(response);
    }
}
