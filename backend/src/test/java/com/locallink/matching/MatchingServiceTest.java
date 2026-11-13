package com.locallink.matching;

import com.locallink.model.License;
import com.locallink.model.Provider;
import com.locallink.model.Skill;
import com.locallink.seed.SeedStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.locallink.matching.MatchingService.haversine;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for MatchingService.
 *
 * Golden-need tests reproduce the reference/matcher_reference.py output within 0.001.
 * Expected scores are computed inline using the same formula as the reference so the
 * numbers are derived from first principles, not hard-coded magic values.
 *
 * Special-case tests:
 *   1. Licensed electrician outranks a closer but unverified one (regulated, safety-first).
 *   2. A not_in_good_standing provider sorts last and shows a warning.
 *   3. A provider outside its service radius is excluded entirely.
 */
class MatchingServiceTest {

    private SeedStore store;
    private MatchingService service;

    // ----- helpers -----

    /** Build a minimal Skill */
    private Skill skill(String id, boolean regulated) {
        return new Skill(id, id, "test", regulated);
    }

    /** Build a Provider with the given parameters. No licenses. */
    private Provider provider(String id, String city, double lat, double lon,
                               int radiusMiles, String laraStatus, String availability,
                               String... skills) {
        return new Provider(id, id, "llc", city, lat, lon, radiusMiles,
                "summary", laraStatus, availability, List.of(skills), List.of());
    }

    /** Build a Provider that has one verified license for the given skill. */
    private Provider providerWithVerifiedLicense(String id, String city, double lat, double lon,
                                                  int radiusMiles, String laraStatus, String availability,
                                                  String skillId) {
        License lic = new License("Test license", List.of(skillId), true);
        return new Provider(id, id, "llc", city, lat, lon, radiusMiles,
                "summary", laraStatus, availability, List.of(skillId), List.of(lic));
    }

    /** Build a Provider that has one UNVERIFIED license for the given skill. */
    private Provider providerWithClaimedLicense(String id, String city, double lat, double lon,
                                                 int radiusMiles, String laraStatus, String availability,
                                                 String skillId) {
        License lic = new License("Test license", List.of(skillId), false);
        return new Provider(id, id, "llc", city, lat, lon, radiusMiles,
                "summary", laraStatus, availability, List.of(skillId), List.of(lic));
    }

    /** Reference score formula — mirrors matcher_reference.py exactly. */
    private double expectedScore(double trustScore, double distScore, double depthScore, double availScore) {
        return 0.35 * trustScore + 0.30 * distScore + 0.20 * depthScore + 0.15 * availScore;
    }

    /** dist_score = max(0, 1 - d/reach), reach = min(reqRadius, providerRadius) */
    private double distScore(double reqLat, double reqLon, double provLat, double provLon,
                              double reqRadius, int provRadius) {
        double d = haversine(reqLat, reqLon, provLat, provLon);
        double reach = Math.min(reqRadius, provRadius);
        return Math.max(0.0, 1.0 - d / reach);
    }

    // ----- set up a fresh SeedStore before each test -----

    @BeforeEach
    void setUp() {
        store = new SeedStore();
        service = new MatchingService(store);
    }


    // =========================================================================
    // Haversine sanity
    // =========================================================================

    @Test
    void haversine_royalOakToSouthfield() {
        // Royal Oak (42.4895, -83.1446) → Southfield (42.4734, -83.2219)
        // Actual haversine result: ~4.09 miles
        double d = haversine(42.4895, -83.1446, 42.4734, -83.2219);
        assertThat(d).isCloseTo(4.09, within(0.5));
    }

    @Test
    void haversine_symmetry() {
        double d1 = haversine(42.4895, -83.1446, 42.3314, -83.0458);
        double d2 = haversine(42.3314, -83.0458, 42.4895, -83.1446);
        assertThat(d1).isCloseTo(d2, within(1e-9));
    }

    @Test
    void haversine_zero_for_same_point() {
        assertThat(haversine(42.4895, -83.1446, 42.4895, -83.1446)).isZero();
    }


    // =========================================================================
    // Trust / tier logic
    // =========================================================================

    @Test
    void trust_notInGoodStanding_tier_and_score() {
        Provider p = provider("p1", "Detroit", 42.33, -83.04, 40,
                "not_in_good_standing", "this_month", "electrical");
        MatchingService.TrustResult t = service.computeTrust(p, "electrical", false);
        assertThat(t.tier()).isEqualTo("not_in_good_standing");
        assertThat(t.score()).isEqualTo(0.10);
        assertThat(t.warnings()).anyMatch(w -> w.contains("not in good standing with LARA"));
    }

    @Test
    void trust_verifiedLicense_tier_and_score() {
        Provider p = providerWithVerifiedLicense("p1", "Detroit", 42.33, -83.04, 40,
                "active_good_standing", "this_month", "electrical");
        MatchingService.TrustResult t = service.computeTrust(p, "electrical", false);
        assertThat(t.tier()).isEqualTo("licensed_verified");
        assertThat(t.score()).isEqualTo(1.0);
        assertThat(t.badges()).anyMatch(b -> b.contains("verified (simulated)"));
    }

    @Test
    void trust_registeredNoLicense_tier_and_score() {
        Provider p = provider("p1", "Detroit", 42.33, -83.04, 40,
                "active_good_standing", "this_month", "concrete");
        MatchingService.TrustResult t = service.computeTrust(p, "concrete", false);
        assertThat(t.tier()).isEqualTo("registered");
        assertThat(t.score()).isEqualTo(0.85);
        assertThat(t.badges()).anyMatch(b -> b.contains("in good standing"));
    }

    @Test
    void trust_selfDeclaredNoLicense_tier_and_score() {
        Provider p = provider("p1", "Detroit", 42.33, -83.04, 40,
                "not_applicable", "this_month", "framing");
        MatchingService.TrustResult t = service.computeTrust(p, "framing", false);
        assertThat(t.tier()).isEqualTo("self_declared");
        assertThat(t.score()).isEqualTo(0.50);
    }

    @Test
    void trust_claimedButUnverified_produces_warning() {
        Provider p = providerWithClaimedLicense("p1", "Ann Arbor", 42.28, -83.74, 40,
                "not_applicable", "this_month", "electrical");
        MatchingService.TrustResult t = service.computeTrust(p, "electrical", false);
        assertThat(t.warnings()).anyMatch(w -> w.contains("claimed but not verified"));
    }

    @Test
    void trust_regulatedSkillNoVerifiedLicense_producesLookupWarning() {
        Provider p = provider("p1", "Detroit", 42.33, -83.04, 40,
                "active_good_standing", "this_month", "electrical");
        MatchingService.TrustResult t = service.computeTrust(p, "electrical", true /*regulated*/);
        assertThat(t.warnings()).anyMatch(w -> w.contains("may require a state license"));
        assertThat(t.warnings()).anyMatch(w -> w.contains("michigan.gov"));
    }

    @Test
    void trust_regulatedSkillWithVerifiedLicense_noLookupWarning() {
        Provider p = providerWithVerifiedLicense("p1", "Detroit", 42.33, -83.04, 40,
                "active_good_standing", "this_month", "electrical");
        MatchingService.TrustResult t = service.computeTrust(p, "electrical", true);
        assertThat(t.warnings()).noneMatch(w -> w.contains("may require a state license"));
    }


    // =========================================================================
    // Radius filtering
    // =========================================================================

    @Test
    void providerOutsideServiceRadius_isExcluded() {
        // Provider with 5-mile service radius placed 20 miles from request city
        // → effective reach = min(40, 5) = 5 miles. Distance >5 → excluded.
        store.putSkill(skill("concrete", false));
        store.putCity("Royal Oak", 42.4895, -83.1446);

        // Place provider ~20 miles south to guarantee exclusion
        Provider farProvider = provider("far-1", "FarCity", 42.19, -83.14, 5,
                "active_good_standing", "this_month", "concrete");
        store.putProvider(farProvider);

        List<MatchResponse.MatchResult> results =
                service.matchSkill(skill("concrete", false), 42.4895, -83.1446, 40.0);
        assertThat(results).isEmpty();
    }

    @Test
    void providerOutsideRequestedRadius_isExcluded() {
        // Provider with 100-mile service radius but request radius is 5 miles
        store.putSkill(skill("concrete", false));
        store.putCity("Royal Oak", 42.4895, -83.1446);

        // Place provider ~20 miles from Royal Oak
        Provider farProvider = provider("far-2", "FarCity", 42.19, -83.14, 100,
                "active_good_standing", "this_month", "concrete");
        store.putProvider(farProvider);

        List<MatchResponse.MatchResult> results =
                service.matchSkill(skill("concrete", false), 42.4895, -83.1446, 5.0);
        assertThat(results).isEmpty();
    }


    // =========================================================================
    // Special case 1: licensed electrician outranks a closer but unverified one
    // =========================================================================

    @Test
    void regulatedSkill_licensedVerifiedOutranksCloserUnverified() {
        // Royal Oak (42.4895, -83.1446)
        // "close" unverified provider — 2 miles away, self_declared (not_applicable + claimed lic)
        // "far" verified provider — 10 miles away, licensed_verified
        // For regulated skills, tier ordering puts licensed_verified before self_declared
        // regardless of distance.

        store.putSkill(skill("electrical", true));
        store.putCity("Royal Oak", 42.4895, -83.1446);

        // Provider A: closer but unverified license — self_declared tier
        Provider closeUnverified = providerWithClaimedLicense(
                "close-unverified", "NearCity", 42.4950, -83.1500, 40,
                "not_applicable", "this_month", "electrical");

        // Provider B: farther but verified license + good standing — licensed_verified tier
        Provider farLicensed = providerWithVerifiedLicense(
                "far-licensed", "FarCity", 42.4000, -83.1446, 40,
                "active_good_standing", "this_month", "electrical");

        store.putProvider(closeUnverified);
        store.putProvider(farLicensed);

        List<MatchResponse.MatchResult> results =
                service.matchSkill(skill("electrical", true), 42.4895, -83.1446, 40.0);

        assertThat(results).hasSize(2);
        // farLicensed must be first despite being farther
        assertThat(results.get(0).providerId()).isEqualTo("far-licensed");
        assertThat(results.get(0).tier()).isEqualTo("licensed_verified");
        assertThat(results.get(1).providerId()).isEqualTo("close-unverified");
        assertThat(results.get(1).tier()).isEqualTo("self_declared");
    }


    // =========================================================================
    // Special case 2: not_in_good_standing sorts last and shows a warning
    // =========================================================================

    @Test
    void notInGoodStanding_sortsLastAndHasWarning_nonRegulated() {
        // For non-regulated skill: not_in_good_standing sorts after everyone else
        store.putSkill(skill("concrete", false));
        store.putCity("Detroit", 42.3314, -83.0458);

        Provider bad = provider("bad", "Detroit", 42.33, -83.04, 40,
                "not_in_good_standing", "this_month", "concrete");
        Provider good = provider("good", "Detroit", 42.33, -83.04, 40,
                "active_good_standing", "this_month", "concrete");

        store.putProvider(bad);
        store.putProvider(good);

        List<MatchResponse.MatchResult> results =
                service.matchSkill(skill("concrete", false), 42.3314, -83.0458, 40.0);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).providerId()).isEqualTo("good");
        assertThat(results.get(1).providerId()).isEqualTo("bad");
        assertThat(results.get(1).tier()).isEqualTo("not_in_good_standing");
        assertThat(results.get(1).warnings())
                .anyMatch(w -> w.contains("not in good standing with LARA"));
    }

    @Test
    void notInGoodStanding_sortsLastAndHasWarning_regulated() {
        // For regulated skill: not_in_good_standing sorts last (priority 3)
        store.putSkill(skill("electrical", true));
        store.putCity("Detroit", 42.3314, -83.0458);

        Provider bad = provider("bad", "Detroit", 42.33, -83.04, 40,
                "not_in_good_standing", "this_month", "electrical");
        Provider selfDeclared = provider("sd", "Detroit", 42.33, -83.04, 40,
                "not_applicable", "this_month", "electrical");

        store.putProvider(bad);
        store.putProvider(selfDeclared);

        List<MatchResponse.MatchResult> results =
                service.matchSkill(skill("electrical", true), 42.3314, -83.0458, 40.0);

        assertThat(results.get(results.size() - 1).providerId()).isEqualTo("bad");
        assertThat(results.get(results.size() - 1).warnings())
                .anyMatch(w -> w.contains("not in good standing with LARA"));
    }


    // =========================================================================
    // Special case 3: provider outside service radius is excluded
    // (covered above in radius tests — this is an end-to-end variant)
    // =========================================================================

    @Test
    void providerOutsideOwnServiceRadius_excluded_even_if_insideRequestRadius() {
        store.putSkill(skill("roofing", true));
        store.putCity("Livonia", 42.3684, -83.3527);

        // Provider 25 miles from Livonia, service_radius_miles=10 → reach=10 < 25 → excluded
        Provider tooFar = provider("too-far", "FarCity", 42.14, -83.35, 10,
                "active_good_standing", "this_month", "roofing");
        store.putProvider(tooFar);

        List<MatchResponse.MatchResult> results =
                service.matchSkill(skill("roofing", true), 42.3684, -83.3527, 40.0);
        assertThat(results).isEmpty();
    }


    // =========================================================================
    // Golden need tests — reproduce reference/matcher_reference.py within 0.001
    //
    // Expected scores are computed here using the same formula as the reference,
    // making these tests an independent re-derivation, not hard-coded magic.
    // =========================================================================

    /**
     * Seed the store with real providers and skills from providers.json / skills_taxonomy.json.
     * We set up only the providers relevant to the golden need under test.
     */
    private void seedRealCities() {
        store.putCity("Royal Oak",  42.4895, -83.1446);
        store.putCity("Ann Arbor",  42.2808, -83.7430);
        store.putCity("Detroit",    42.3314, -83.0458);
        store.putCity("Livonia",    42.3684, -83.3527);
        store.putCity("Warren",     42.5145, -83.0147);
        store.putCity("Southfield", 42.4734, -83.2219);
        store.putCity("Troy",       42.6064, -83.1498);
        store.putCity("Dearborn",   42.3223, -83.1763);
        store.putCity("Hamtramck",  42.3928, -83.0497);
        store.putCity("Pontiac",    42.6389, -83.2910);
        store.putCity("Lansing",    42.7325, -84.5555);
        store.putCity("Grand Rapids", 42.9634, -85.6681);
    }

    // --- need-1: concrete (Royal Oak, default radius 40) ---

    @Test
    void golden_need1_concrete_demo001_Warren() {
        // demo-001: Warren (42.5125,-83.0121), radius=30, registered, this_month, primary
        seedRealCities();
        store.putSkill(skill("concrete", false));

        Provider demo001 = provider("demo-001", "Warren", 42.5125, -83.0121, 30,
                "active_good_standing", "this_month", "concrete", "excavation");
        store.putProvider(demo001);

        List<MatchResponse.MatchResult> results =
                service.matchSkill(skill("concrete", false), 42.4895, -83.1446, 40.0);

        assertThat(results).hasSize(1);
        MatchResponse.MatchResult r = results.get(0);
        assertThat(r.tier()).isEqualTo("registered");

        // Compute expected score using reference formula
        double d = haversine(42.4895, -83.1446, 42.5125, -83.0121);
        double reach = Math.min(40.0, 30);
        double expected = expectedScore(0.85, Math.max(0, 1 - d / reach), 1.0, 1.0);
        assertThat(r.score()).isCloseTo(expected, within(0.001));
    }

    @Test
    void golden_need1_concrete_demo009_Detroit() {
        // demo-009: Detroit (42.3274,-83.0406), radius=25, self_declared, flexible, secondary (masonry primary)
        seedRealCities();
        store.putSkill(skill("concrete", false));

        Provider demo009 = provider("demo-009", "Detroit", 42.3274, -83.0406, 25,
                "not_applicable", "flexible", "masonry_retaining", "concrete");
        store.putProvider(demo009);

        List<MatchResponse.MatchResult> results =
                service.matchSkill(skill("concrete", false), 42.4895, -83.1446, 40.0);

        assertThat(results).hasSize(1);
        MatchResponse.MatchResult r = results.get(0);
        assertThat(r.tier()).isEqualTo("self_declared");

        double d = haversine(42.4895, -83.1446, 42.3274, -83.0406);
        double reach = Math.min(40.0, 25);
        double expected = expectedScore(0.50, Math.max(0, 1 - d / reach), 0.7 /*secondary*/, 0.8);
        assertThat(r.score()).isCloseTo(expected, within(0.001));
    }

    // --- need-1: framing (Royal Oak, radius 40) ---

    @Test
    void golden_need1_framing_demo003_RoyalOak() {
        // demo-003: Royal Oak (42.4815,-83.1342), radius=20, self_declared, this_month, primary
        seedRealCities();
        store.putSkill(skill("framing", false));

        Provider demo003 = provider("demo-003", "Royal Oak", 42.4815, -83.1342, 20,
                "not_applicable", "this_month", "framing", "drywall_painting");
        store.putProvider(demo003);

        List<MatchResponse.MatchResult> results =
                service.matchSkill(skill("framing", false), 42.4895, -83.1446, 40.0);

        assertThat(results).hasSize(1);
        MatchResponse.MatchResult r = results.get(0);
        assertThat(r.tier()).isEqualTo("self_declared");

        double d = haversine(42.4895, -83.1446, 42.4815, -83.1342);
        double reach = Math.min(40.0, 20);
        double expected = expectedScore(0.50, Math.max(0, 1 - d / reach), 1.0, 1.0);
        assertThat(r.score()).isCloseTo(expected, within(0.001));
    }

    @Test
    void golden_need1_framing_demo025_Troy() {
        // demo-025: Troy (42.5984,-83.1394), radius=35, registered, next_month, primary
        seedRealCities();
        store.putSkill(skill("framing", false));

        Provider demo025 = provider("demo-025", "Troy", 42.5984, -83.1394, 35,
                "active_good_standing", "next_month", "framing");
        store.putProvider(demo025);

        List<MatchResponse.MatchResult> results =
                service.matchSkill(skill("framing", false), 42.4895, -83.1446, 40.0);

        assertThat(results).hasSize(1);
        MatchResponse.MatchResult r = results.get(0);
        assertThat(r.tier()).isEqualTo("registered");

        double d = haversine(42.4895, -83.1446, 42.5984, -83.1394);
        double reach = Math.min(40.0, 35);
        double expected = expectedScore(0.85, Math.max(0, 1 - d / reach), 1.0, 0.6);
        assertThat(r.score()).isCloseTo(expected, within(0.001));
    }

    // --- need-1 / need-5: electrical (Ann Arbor / Royal Oak) — the key trust demos ---

    @Test
    void golden_electrical_demo024_Southfield_licensedVerified() {
        // demo-024: Southfield (42.4794,-83.2297), radius=35, licensed_verified, this_month, primary
        seedRealCities();
        License lic = new License("Electrical contractor (state)", List.of("electrical"), true);
        Provider demo024 = new Provider("demo-024", "Lumen Light & Power Inc.", "corp",
                "Southfield", 42.4794, -83.2297, 35, "summary",
                "active_good_standing", "this_month", List.of("electrical"), List.of(lic));
        store.putSkill(skill("electrical", true));
        store.putProvider(demo024);

        List<MatchResponse.MatchResult> results =
                service.matchSkill(skill("electrical", true), 42.4895, -83.1446, 40.0);

        assertThat(results).hasSize(1);
        MatchResponse.MatchResult r = results.get(0);
        assertThat(r.tier()).isEqualTo("licensed_verified");
        assertThat(r.warnings()).noneMatch(w -> w.contains("may require a state license"));

        double d = haversine(42.4895, -83.1446, 42.4794, -83.2297);
        double reach = Math.min(40.0, 35);
        double expected = expectedScore(1.0, Math.max(0, 1 - d / reach), 1.0, 1.0);
        assertThat(r.score()).isCloseTo(expected, within(0.001));
    }

    @Test
    void golden_electrical_demo023_AnnArbor_claimedLicense_selfDeclared() {
        // demo-023: Ann Arbor (42.2788,-83.7404), radius=30, not_applicable + claimed-but-unverified lic
        // → self_declared tier, should show claimed-not-verified warning + regulated lookup warning
        seedRealCities();
        License lic = new License("Electrical contractor (state)", List.of("electrical"), false);
        Provider demo023 = new Provider("demo-023", "Sparkplug Electric", "sole_prop",
                "Ann Arbor", 42.2788, -83.7404, 30, "summary",
                "not_applicable", "this_month", List.of("electrical"), List.of(lic));
        store.putSkill(skill("electrical", true));
        store.putProvider(demo023);

        // From Ann Arbor (42.2808,-83.7430) — demo-023 is very close, within reach
        List<MatchResponse.MatchResult> results =
                service.matchSkill(skill("electrical", true), 42.2808, -83.7430, 40.0);

        assertThat(results).hasSize(1);
        MatchResponse.MatchResult r = results.get(0);
        assertThat(r.tier()).isEqualTo("self_declared");
        assertThat(r.warnings()).anyMatch(w -> w.contains("claimed but not verified"));
        assertThat(r.warnings()).anyMatch(w -> w.contains("may require a state license"));

        double d = haversine(42.2808, -83.7430, 42.2788, -83.7404);
        double reach = Math.min(40.0, 30);
        double expected = expectedScore(0.50, Math.max(0, 1 - d / reach), 1.0, 1.0);
        assertThat(r.score()).isCloseTo(expected, within(0.001));
    }

    @Test
    void golden_electrical_demo004_Detroit_licensedVerified_nextMonth() {
        // demo-004: Detroit (42.3314,-83.0458), radius=35, licensed_verified, next_month, primary
        seedRealCities();
        License lic = new License("Electrical contractor (state)", List.of("electrical"), true);
        Provider demo004 = new Provider("demo-004", "Brightline Electric LLC", "llc",
                "Detroit", 42.3314, -83.0458, 35, "summary",
                "active_good_standing", "next_month", List.of("electrical"), List.of(lic));
        store.putSkill(skill("electrical", true));
        store.putProvider(demo004);

        List<MatchResponse.MatchResult> results =
                service.matchSkill(skill("electrical", true), 42.4895, -83.1446, 40.0);

        assertThat(results).hasSize(1);
        MatchResponse.MatchResult r = results.get(0);
        assertThat(r.tier()).isEqualTo("licensed_verified");

        double d = haversine(42.4895, -83.1446, 42.3314, -83.0458);
        double reach = Math.min(40.0, 35);
        double expected = expectedScore(1.0, Math.max(0, 1 - d / reach), 1.0, 0.6);
        assertThat(r.score()).isCloseTo(expected, within(0.001));
    }

    // --- need-4: roofing (Livonia) — not_in_good_standing demo ---

    @Test
    void golden_need4_roofing_demo006_Livonia_notInGoodStanding() {
        // demo-006: Livonia (42.3624,-83.3449), radius=35, not_in_good_standing, this_month, primary
        seedRealCities();
        store.putSkill(skill("roofing", true));

        Provider demo006 = provider("demo-006", "Livonia", 42.3624, -83.3449, 35,
                "not_in_good_standing", "this_month", "roofing");
        store.putProvider(demo006);

        List<MatchResponse.MatchResult> results =
                service.matchSkill(skill("roofing", true), 42.3684, -83.3527, 40.0);

        assertThat(results).hasSize(1);
        MatchResponse.MatchResult r = results.get(0);
        assertThat(r.tier()).isEqualTo("not_in_good_standing");
        assertThat(r.score()).isCloseTo(
                expectedScore(0.10,
                        Math.max(0, 1 - haversine(42.3684, -83.3527, 42.3624, -83.3449) / Math.min(40.0, 35)),
                        1.0, 1.0),
                within(0.001));
        // must carry warning
        assertThat(r.warnings()).anyMatch(w -> w.contains("not in good standing with LARA"));
        // regulated + no verified lic → lookup warning too
        assertThat(r.warnings()).anyMatch(w -> w.contains("may require a state license"));
    }

    // --- need-2: invoicing_collections (Ann Arbor) ---

    @Test
    void golden_need2_invoicingCollections_demo013_RoyalOak() {
        // demo-013: Royal Oak (42.4955,-83.1524), radius=40, self_declared, this_month, primary
        seedRealCities();
        store.putSkill(skill("invoicing_collections", false));

        Provider demo013 = provider("demo-013", "Royal Oak", 42.4955, -83.1524, 40,
                "not_applicable", "this_month",
                "bookkeeping", "payroll", "invoicing_collections");
        store.putProvider(demo013);

        List<MatchResponse.MatchResult> results =
                service.matchSkill(skill("invoicing_collections", false), 42.2808, -83.7430, 40.0);

        // demo-013 is ~27 miles from Ann Arbor; reach=min(40,40)=40 → included
        assertThat(results).hasSize(1);
        MatchResponse.MatchResult r = results.get(0);
        assertThat(r.tier()).isEqualTo("self_declared");

        double d = haversine(42.2808, -83.7430, 42.4955, -83.1524);
        double reach = Math.min(40.0, 40);
        double expected = expectedScore(0.50, Math.max(0, 1 - d / reach), 0.7 /*secondary*/, 1.0);
        assertThat(r.score()).isCloseTo(expected, within(0.001));
    }

    @Test
    void golden_need2_invoicingCollections_demo016_AnnArbor() {
        // demo-016: Ann Arbor (42.2888,-83.7534), radius=40, self_declared, flexible, primary
        seedRealCities();
        store.putSkill(skill("invoicing_collections", false));

        Provider demo016 = provider("demo-016", "Ann Arbor", 42.2888, -83.7534, 40,
                "not_applicable", "flexible", "invoicing_collections", "bookkeeping");
        store.putProvider(demo016);

        List<MatchResponse.MatchResult> results =
                service.matchSkill(skill("invoicing_collections", false), 42.2808, -83.7430, 40.0);

        assertThat(results).hasSize(1);
        MatchResponse.MatchResult r = results.get(0);
        double d = haversine(42.2808, -83.7430, 42.2888, -83.7534);
        double reach = Math.min(40.0, 40);
        double expected = expectedScore(0.50, Math.max(0, 1 - d / reach), 1.0, 0.8);
        assertThat(r.score()).isCloseTo(expected, within(0.001));
    }

    // --- need-3: permit_compliance_admin (Detroit) ---

    @Test
    void golden_need3_permitCompliance_demo017_Detroit() {
        // demo-017: Detroit (42.3254,-83.0380), radius=40, registered, this_month, primary
        seedRealCities();
        store.putSkill(skill("permit_compliance_admin", false));

        Provider demo017 = provider("demo-017", "Detroit", 42.3254, -83.0380, 40,
                "active_good_standing", "this_month", "permit_compliance_admin");
        store.putProvider(demo017);

        List<MatchResponse.MatchResult> results =
                service.matchSkill(skill("permit_compliance_admin", false), 42.3314, -83.0458, 40.0);

        assertThat(results).hasSize(1);
        MatchResponse.MatchResult r = results.get(0);
        assertThat(r.tier()).isEqualTo("registered");

        double d = haversine(42.3314, -83.0458, 42.3254, -83.0380);
        double reach = Math.min(40.0, 40);
        double expected = expectedScore(0.85, Math.max(0, 1 - d / reach), 1.0, 1.0);
        assertThat(r.score()).isCloseTo(expected, within(0.001));
    }

    // --- need-3: marketing_web (Detroit) ---

    @Test
    void golden_need3_marketingWeb_demo019_Dearborn() {
        // demo-019: Dearborn (42.3323,-83.1893), radius=50, self_declared, this_month, primary
        seedRealCities();
        store.putSkill(skill("marketing_web", false));

        Provider demo019 = provider("demo-019", "Dearborn", 42.3323, -83.1893, 50,
                "not_applicable", "this_month", "marketing_web");
        store.putProvider(demo019);

        List<MatchResponse.MatchResult> results =
                service.matchSkill(skill("marketing_web", false), 42.3314, -83.0458, 40.0);

        assertThat(results).hasSize(1);
        MatchResponse.MatchResult r = results.get(0);
        assertThat(r.tier()).isEqualTo("self_declared");

        double d = haversine(42.3314, -83.0458, 42.3323, -83.1893);
        double reach = Math.min(40.0, 50);
        double expected = expectedScore(0.50, Math.max(0, 1 - d / reach), 1.0, 1.0);
        assertThat(r.score()).isCloseTo(expected, within(0.001));
    }
}
