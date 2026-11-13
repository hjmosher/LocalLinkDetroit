package com.locallink.granite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Exchanges an IBM Cloud API key for an IAM bearer token and caches it.
 *
 * Token lifetime is ~3600 s; we refresh one minute before expiry so callers
 * always get a valid token without racing the clock.
 *
 * Security rules (see AGENTS.md):
 *  - apiKey is never logged (not even partially).
 *  - The raw HTTP response body is never logged in full.
 *  - Only token-size metrics are emitted.
 */
public class IamTokenService {

    private static final Logger log = LoggerFactory.getLogger(IamTokenService.class);

    static final String IAM_TOKEN_URL = "https://iam.cloud.ibm.com/identity/token";
    /** Refresh one minute before the token expires so callers always get a valid token. */
    private static final long REFRESH_BUFFER_SECONDS = 60L;

    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    // Guarded by synchronized(this)
    private String cachedToken;
    private Instant expiresAt = Instant.EPOCH;

    public IamTokenService(String apiKey, HttpClient httpClient, ObjectMapper mapper) {
        this.apiKey = apiKey;
        this.httpClient = httpClient;
        this.mapper = mapper;
    }

    /**
     * Returns a valid bearer token, fetching or refreshing as needed.
     * Synchronized so concurrent callers share a single in-flight token.
     */
    public synchronized String getToken() {
        if (cachedToken == null || Instant.now().isAfter(expiresAt.minusSeconds(REFRESH_BUFFER_SECONDS))) {
            fetchToken();
        }
        return cachedToken;
    }

    // package-visible for test assertions
    synchronized Instant expiresAt() {
        return expiresAt;
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private void fetchToken() {
        // Form body — apiKey never logged
        String formBody = "grant_type=" +
                URLEncoder.encode("urn:ibm:params:oauth:grant-type:apikey", StandardCharsets.UTF_8) +
                "&apikey=" +
                URLEncoder.encode(apiKey, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(IAM_TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IamTokenException("IAM token request failed: " + e.getMessage(), e);
        }

        if (response.statusCode() != 200) {
            // Do NOT log the body — it may echo back part of the credentials.
            throw new IamTokenException(
                    "IAM token endpoint returned HTTP " + response.statusCode());
        }

        parseAndCacheToken(response.body());
        // Log only token length, not the value
        log.info("IAM token fetched; length={} chars, expiresAt={}", cachedToken.length(), expiresAt);
    }

    private void parseAndCacheToken(String body) {
        try {
            JsonNode node = mapper.readTree(body);
            String token = node.path("access_token").asText(null);
            if (token == null || token.isBlank()) {
                throw new IamTokenException("IAM response missing access_token field");
            }
            long expiresIn = node.path("expires_in").asLong(3600L);
            this.cachedToken = token;
            this.expiresAt = Instant.now().plusSeconds(expiresIn);
        } catch (IOException e) {
            throw new IamTokenException("Cannot parse IAM token response: " + e.getMessage(), e);
        }
    }
}
