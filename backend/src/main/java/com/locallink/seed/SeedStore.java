package com.locallink.seed;

import com.locallink.model.Provider;
import com.locallink.model.Skill;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory store for seed data populated by SeedLoader.
 * MatchingService reads directly from here — no DB queries needed for matching.
 */
@Component
public class SeedStore {

    // Preserves insertion order for deterministic test output
    private final Map<String, Skill> skillsById = new LinkedHashMap<>();
    private final Map<String, Provider> providersById = new LinkedHashMap<>();
    private final Map<String, double[]> citiesByName = new LinkedHashMap<>(); // name -> [lat, lon]

    public void putSkill(Skill skill) {
        skillsById.put(skill.id(), skill);
    }

    public void putProvider(Provider provider) {
        providersById.put(provider.id(), provider);
    }

    public void putCity(String name, double lat, double lon) {
        citiesByName.put(name, new double[]{lat, lon});
    }

    public List<Skill> skills() {
        return List.copyOf(skillsById.values());
    }

    public List<Provider> providers() {
        return List.copyOf(providersById.values());
    }

    public Map<String, double[]> cities() {
        return Collections.unmodifiableMap(citiesByName);
    }

    public Optional<Skill> skill(String id) {
        return Optional.ofNullable(skillsById.get(id));
    }

    /** Returns [lat, lon] for the named city, or empty if unknown. */
    public Optional<double[]> cityCoords(String cityName) {
        return Optional.ofNullable(citiesByName.get(cityName));
    }
}
