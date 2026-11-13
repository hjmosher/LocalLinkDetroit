package com.locallink.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.locallink.granite.GraniteClient;
import com.locallink.granite.MockGraniteClient;
import com.locallink.seed.SeedStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the correct GraniteClient implementation based on the granite.mode property.
 *
 * granite.mode=mock (default, GRANITE_MODE env var unset) → MockGraniteClient
 * granite.mode=live                                        → WatsonxGraniteClient (P1)
 *
 * The default is always "mock" so the app works with no network or API key.
 * Never hardcode a value other than "mock" in application.properties.
 */
@Configuration
public class GraniteConfig {

    private static final Logger log = LoggerFactory.getLogger(GraniteConfig.class);

    @Value("${granite.mode:mock}")
    private String graniteMode;

    @Bean
    public GraniteClient graniteClient(SeedStore store, ObjectMapper mapper) {
        if ("live".equalsIgnoreCase(graniteMode)) {
            // P1 — WatsonxGraniteClient will be wired here when implemented.
            // For now fall back to mock so the app doesn't crash on a missing impl.
            log.warn("GRANITE_MODE=live requested but WatsonxGraniteClient is not yet implemented; " +
                     "falling back to MockGraniteClient");
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
