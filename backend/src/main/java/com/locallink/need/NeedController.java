package com.locallink.need;

import com.locallink.granite.DecomposeRequest;
import com.locallink.granite.DecomposeResponse;
import com.locallink.granite.GraniteClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * POST /api/needs/decompose — decompose free text into skills.
 *
 * Delegates to GraniteClient; in mock mode this uses keyword fallback.
 * Input text is capped at 1000 chars (security note in GRANITE_PROMPTS.md).
 */
@RestController
@RequestMapping("/api/needs")
public class NeedController {

    private final GraniteClient graniteClient;

    @Autowired
    public NeedController(GraniteClient graniteClient) {
        this.graniteClient = graniteClient;
    }

    @PostMapping("/decompose")
    public ResponseEntity<?> decompose(@RequestBody DecomposeRequest request) {
        if (request.text() == null || request.text().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "MISSING_TEXT", "message", "text is required"));
        }
        if (request.city() == null || request.city().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "MISSING_CITY", "message", "city is required"));
        }

        // Cap at 1000 chars per GRANITE_PROMPTS.md safety note
        String safeText = request.text().length() > 1000
                ? request.text().substring(0, 1000) : request.text();
        DecomposeRequest safeRequest = new DecomposeRequest(safeText, request.city());

        DecomposeResponse response = graniteClient.decompose(safeRequest);
        return ResponseEntity.ok(response);
    }
}
