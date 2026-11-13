package com.locallink.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.locallink.granite.GraniteClient;
import com.locallink.granite.IamTokenService;
import com.locallink.granite.MockGraniteClient;
import com.locallink.granite.WatsonxGraniteClient;
import com.locallink.seed.SeedStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

/**
 * Wires the correct GraniteClient implementation based on the granite.mode property.
 *
 * granite.mode=mock (default, GRANITE_MODE env var unset) → MockGraniteClient
 * granite.mode=live                                        → WatsonxGraniteClient
 *
 * The default is always "mock" so the app works with no network or API key.
 * Never hardcode a value other than "mock" in application.properties.
 */
@Configuration
public class GraniteConfig {

    private static final Logger log = LoggerFactory.getLogger(GraniteConfig.class);

    @Value("${granite.mode:mock}")
    private String graniteMode;

    // Env-only — values are empty strings when GRANITE_MODE=mock (application.properties default)
    @Value("${watsonx.url:}")
    private String watsonxUrl;

    @Value("${watsonx.project-id:}")
    private String projectId;

    @Value("${watsonx.api-key:}")
    private String apiKey;

    @Value("${watsonx.model-id:}")
    private String modelId;

    @Bean
    public GraniteClient graniteClient(SeedStore store, ObjectMapper mapper) {
        if ("live".equalsIgnoreCase(graniteMode)) {
            if (watsonxUrl.isBlank() || projectId.isBlank() || apiKey.isBlank() || modelId.isBlank()) {
                log.error("GRANITE_MODE=live but one or more required env vars are missing " +
                          "(WATSONX_URL, WATSONX_PROJECT_ID, WATSONX_API_KEY, WATSONX_MODEL_ID). " +
                          "Falling back to MockGraniteClient.");
            } else {
                log.info("GraniteClient: using WatsonxGraniteClient (mode=live, model={})", modelId);
                // HttpClient is shared; IamTokenService handles token caching
                HttpClient httpClient = HttpClient.newHttpClient();
                IamTokenService tokenService = new IamTokenService(apiKey, httpClient, mapper);
                return new WatsonxGraniteClient(
                        watsonxUrl, projectId, modelId, tokenService, httpClient, mapper, store);
            }
        }
        log.info("GraniteClient: using MockGraniteClient (mode={})", graniteMode);
        return new MockGraniteClient(store, mapper);
    }

    /** Expose the resolved mode string for /api/health. */
    @Bean
    public String resolvedGraniteMode() {
        return "live".equalsIgnoreCase(graniteMode) ? "live" : "mock";
    }
}
