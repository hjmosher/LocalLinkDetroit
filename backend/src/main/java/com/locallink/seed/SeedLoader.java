package com.locallink.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.locallink.model.License;
import com.locallink.model.Provider;
import com.locallink.model.Skill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads seed/*.json into H2 at startup.
 * Skills come from skills_taxonomy.json; providers (with licenses) from providers.json.
 * City coordinates are also held in SeedStore for use by MatchingService.
 */
@Component
public class SeedLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedLoader.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final SeedStore store;

    @Autowired
    public SeedLoader(JdbcTemplate jdbc, ObjectMapper mapper, SeedStore store) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.store = store;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        loadSkills();
        loadProviders();
        log.info("Seed loaded: {} skills, {} providers, {} cities",
                store.skills().size(), store.providers().size(), store.cities().size());
    }

    private void loadSkills() throws IOException {
        JsonNode root = mapper.readTree(new ClassPathResource("seed/skills_taxonomy.json").getInputStream());
        for (JsonNode s : root.get("skills")) {
            Skill skill = new Skill(
                    s.get("id").asText(),
                    s.get("label").asText(),
                    s.get("group").asText(),
                    s.get("regulated_hint").asBoolean()
            );
            store.putSkill(skill);
            jdbc.update(
                    "INSERT INTO skill(id, label, grp, regulated_hint) VALUES (?, ?, ?, ?)",
                    skill.id(), skill.label(), skill.grp(), skill.regulatedHint()
            );
        }
    }

    private void loadProviders() throws IOException {
        JsonNode root = mapper.readTree(new ClassPathResource("seed/providers.json").getInputStream());

        // Parse cities for coordinate lookup
        JsonNode citiesNode = root.get("cities");
        citiesNode.fields().forEachRemaining(e -> {
            double lat = e.getValue().get("lat").asDouble();
            double lon = e.getValue().get("lon").asDouble();
            store.putCity(e.getKey(), lat, lon);
        });

        int licenseId = 1;
        for (JsonNode p : root.get("providers")) {
            String providerId = p.get("id").asText();

            // Parse skills list (ordered; index 0 is primary)
            List<String> skills = new ArrayList<>();
            for (JsonNode sk : p.get("skills")) skills.add(sk.asText());

            // Parse licenses
            List<License> licenses = new ArrayList<>();
            for (JsonNode l : p.get("licenses")) {
                List<String> licSkills = new ArrayList<>();
                for (JsonNode ls : l.get("skills")) licSkills.add(ls.asText());
                licenses.add(new License(
                        l.get("type").asText(),
                        licSkills,
                        l.get("state_verified").asBoolean()
                ));
            }

            Provider provider = new Provider(
                    providerId,
                    p.get("name").asText(),
                    p.get("structure").asText(),
                    p.get("city").asText(),
                    p.get("lat").asDouble(),
                    p.get("lon").asDouble(),
                    p.get("service_radius_miles").asInt(),
                    p.get("summary").asText(),
                    p.get("lara_status").asText(),
                    p.get("availability").asText(),
                    skills,
                    licenses
            );
            store.putProvider(provider);

            jdbc.update(
                    "INSERT INTO provider(id, name, structure, city, lat, lon, service_radius_miles, " +
                    "summary, lara_status, availability, fictional) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    provider.id(), provider.name(), provider.structure(), provider.city(),
                    provider.lat(), provider.lon(), provider.serviceRadiusMiles(),
                    provider.summary(), provider.laraStatus(), provider.availability(), true
            );

            for (int i = 0; i < skills.size(); i++) {
                jdbc.update(
                        "INSERT INTO provider_skill(provider_id, skill_id, position) VALUES (?,?,?)",
                        providerId, skills.get(i), i
                );
            }

            for (License lic : licenses) {
                jdbc.update(
                        "INSERT INTO license(id, provider_id, type, state_verified) VALUES (?,?,?,?)",
                        licenseId, providerId, lic.type(), lic.stateVerified()
                );
                for (String ls : lic.skills()) {
                    jdbc.update(
                            "INSERT INTO license_skill(license_id, skill_id) VALUES (?,?)",
                            licenseId, ls
                    );
                }
                licenseId++;
            }
        }
    }
}
