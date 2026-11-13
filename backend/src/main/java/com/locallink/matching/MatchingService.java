package com.locallink.matching;

import com.locallink.granite.ExplainContext;
import com.locallink.granite.ExplainResponse;
import com.locallink.granite.GraniteClient;
import com.locallink.model.License;
import com.locallink.model.Provider;
import com.locallink.model.Skill;
import com.locallink.seed.SeedStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Core matching logic. Implements the spec in reference/matcher_reference.py exactly.
 *
 * Score = 0.35·trust + 0.30·distance + 0.20·depth + 0.15·availability
 *
 * Safety-first ordering (before numeric score):
 *   regulated skill → licensed_verified(0) > registered(1) > self_declared(2) > not_in_good_standing(3)
 *   other skill     → not_in_good_standing(1), everyone else(0); within group sort by score desc
 *
 * Ties broken by distance asc, then id asc.
 *
 * The model never decides trust, tier, badges, warnings, or ranking — only code does.
 */
@Service
public class MatchingService {

    static final double EARTH_RADIUS_MILES = 3958.8;
    static final double DEFAULT_RADIUS = 40.0;
    static final String LOOKUP_URL =
            "https://www.michigan.gov/en/lara/bureau-list/cscl/online-services";

    // Score weights — must match reference/matcher_reference.py W dict
    private static final double W_TRUST = 0.35;
    private static final double W_DISTANCE = 0.30;
    private static final double W_DEPTH = 0.20;
    private static final double W_AVAIL = 0.15;

    private static final Map<String, Double> AVAIL_SCORE = Map.of(
            "this_month", 1.0,
            "flexible",   0.8,
            "next_month", 0.6
    );

    private final SeedStore store;
    // Nullable: tests that build MatchingService directly (without Spring) pass null here;
    // matchSkill uses template explanation when graniteClient is null.
    private final GraniteClient graniteClient;

    @Autowired
    public MatchingService(SeedStore store, GraniteClient graniteClient) {
        this.store = store;
        this.graniteClient = graniteClient;
    }

    /** Test-only constructor — no GraniteClient; explanations use template. */
    MatchingService(SeedStore store) {
        this(store, null);
    }

    /**
     * Run matches for every requested skill against the named city's coordinates.
     *
     * @param cityName   city from the request (resolved to lat/lon via SeedStore)
     * @param skillIds   skill ids from user-edited chips
     * @param radius     requested radius in miles (defaults to DEFAULT_RADIUS when null)
     * @return list of SkillGroups in the same order as skillIds
     * @throws IllegalArgumentException if the city is unknown
     */
    public List<MatchResponse.SkillGroup> match(String cityName, List<String> skillIds, Double radius) {
        double[] coords = store.cityCoords(cityName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown city: " + cityName));
        double lat = coords[0];
        double lon = coords[1];
        double requestedRadius = (radius != null && radius > 0) ? radius : DEFAULT_RADIUS;

        List<MatchResponse.SkillGroup> groups = new ArrayList<>();
        for (String skillId : skillIds) {
            Optional<Skill> skillOpt = store.skill(skillId);
            if (skillOpt.isEmpty()) continue; // unknown skill id — skip silently

            Skill skill = skillOpt.get();
            List<MatchResponse.MatchResult> results = matchSkill(skill, lat, lon, requestedRadius);
            groups.add(new MatchResponse.SkillGroup(skill.id(), skill.label(), skill.regulatedHint(), results));
        }
        return groups;
    }

    /**
     * Match one skill: filter, score, sort.
     * Step-by-step per reference/matcher_reference.py#match().
     */
    List<MatchResponse.MatchResult> matchSkill(Skill skill, double reqLat, double reqLon, double requestedRadius) {
        List<MatchResponse.MatchResult> out = new ArrayList<>();

        for (Provider p : store.providers()) {
            if (!p.skills().contains(skill.id())) continue;

            double d = haversine(reqLat, reqLon, p.lat(), p.lon());
            double reach = Math.min(requestedRadius, p.serviceRadiusMiles());
            if (d > reach) continue; // outside both radii

            TrustResult trust = computeTrust(p, skill.id(), skill.regulatedHint());
            double distScore = Math.max(0.0, 1.0 - d / reach);
            double depthScore = p.skills().get(0).equals(skill.id()) ? 1.0 : 0.7;
            double availScore = AVAIL_SCORE.getOrDefault(p.availability(), 0.6);

            double score = W_TRUST * trust.score()
                    + W_DISTANCE * distScore
                    + W_DEPTH * depthScore
                    + W_AVAIL * availScore;

            ExplainContext ctx = new ExplainContext(
                    skill.label(), p.name(), p.structure(), p.city(),
                    round1(d), p.summary(), p.availability(),
                    trust.badges(), trust.warnings()
            );
            ExplainResponse explanation = (graniteClient != null)
                    ? graniteClient.explain(ctx)
                    : new ExplainResponse(buildFallbackSentence(ctx), "template");

            out.add(new MatchResponse.MatchResult(
                    p.id(),
                    p.name(),
                    p.structure(),
                    p.city(),
                    round1(d),
                    trust.tier(),
                    round3(score),
                    trust.badges(),
                    trust.warnings(),
                    p.summary(),
                    p.availability(),
                    explanation.sentence(),
                    explanation.source()
            ));
        }

        // Safety-first ordering: tier priority before numeric score (spec §6)
        Map<String, Integer> priority = skill.regulatedHint()
                ? Map.of("licensed_verified", 0, "registered", 1, "self_declared", 2, "not_in_good_standing", 3)
                : Map.of("licensed_verified", 0, "registered", 0, "self_declared", 0, "not_in_good_standing", 1);

        out.sort(Comparator
                .comparingInt((MatchResponse.MatchResult m) -> priority.getOrDefault(m.tier(), 99))
                .thenComparingDouble(m -> -m.score())
                .thenComparingDouble(MatchResponse.MatchResult::distanceMiles)
                .thenComparing(MatchResponse.MatchResult::providerId));

        return out;
    }

    /**
     * Compute tier, trust score, badges and warnings for one provider×skill pair.
     * Exactly mirrors reference/matcher_reference.py#trust().
     *
     * Tier assignment (in order of precedence):
     *   1. not_in_good_standing → score 0.10
     *   2. verified license for skill → licensed_verified, score 1.0
     *   3. active_good_standing (no verified license) → registered, score 0.85
     *   4. otherwise → self_declared, score 0.50
     *
     * Regulated warning: if skill is regulated and provider has NO verified license for it,
     * append the state lookup warning. (Applies even to not_in_good_standing providers.)
     */
    TrustResult computeTrust(Provider p, String skillId, boolean regulated) {
        List<String> badges = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        List<License> verifiedLics = p.licenses().stream()
                .filter(l -> l.skills().contains(skillId) && l.stateVerified())
                .toList();
        List<License> claimedLics = p.licenses().stream()
                .filter(l -> l.skills().contains(skillId) && !l.stateVerified())
                .toList();

        // LARA registration badge — same logic as reference
        if ("active_good_standing".equals(p.laraStatus())) {
            badges.add("Registered with LARA, in good standing (simulated)");
        } else if ("not_in_good_standing".equals(p.laraStatus())) {
            badges.add("LARA: not in good standing (simulated)");
            warnings.add("This business is not in good standing with LARA. Verify before hiring.");
        } else {
            badges.add("Self-declared (not in the LARA registry)");
        }

        // Verified license badges
        for (License l : verifiedLics) {
            badges.add(l.type() + ": verified (simulated)");
        }
        // Claimed-but-unverified warnings
        for (License l : claimedLics) {
            warnings.add(l.type() + " claimed but not verified.");
        }

        // Tier + trust score
        String tier;
        double score;
        if ("not_in_good_standing".equals(p.laraStatus())) {
            tier = "not_in_good_standing";
            score = 0.10;
        } else if (!verifiedLics.isEmpty()) {
            tier = "licensed_verified";
            score = 1.0;
        } else if ("active_good_standing".equals(p.laraStatus())) {
            tier = "registered";
            score = 0.85;
        } else {
            tier = "self_declared";
            score = 0.50;
        }

        // Regulated warning (spec §5): if regulated and no verified license → append lookup
        if (regulated && verifiedLics.isEmpty()) {
            warnings.add("This type of work may require a state license. Check the state license lookup: " + LOOKUP_URL);
        }

        return new TrustResult(tier, score, badges, warnings);
    }

    /**
     * Haversine distance in miles between two lat/lon points.
     * Matches reference/matcher_reference.py#haversine() exactly.
     */
    public static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = p2 - p1;
        double dl = Math.toRadians(lon2 - lon1);
        double h = Math.pow(Math.sin(dp / 2), 2)
                + Math.cos(p1) * Math.cos(p2) * Math.pow(Math.sin(dl / 2), 2);
        return 2 * EARTH_RADIUS_MILES * Math.asin(Math.sqrt(h));
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    /**
     * Template explanation used when graniteClient is null (test mode).
     * Mirrors the template in MockGraniteClient so output is consistent.
     */
    private static String buildFallbackSentence(ExplainContext ctx) {
        String firstBadge = ctx.badges() != null && !ctx.badges().isEmpty()
                ? ctx.badges().get(0) : "Self-declared";
        String base = ctx.providerName() + " offers " + ctx.skillLabel() +
                " about " + ctx.distanceMiles() + " miles away. " + firstBadge + ".";
        if (ctx.warnings() != null && !ctx.warnings().isEmpty()) {
            base = base + " Note: " + ctx.warnings().get(0);
        }
        return base.length() > 220 ? base.substring(0, 217) + "..." : base;
    }

    /** Internal result of trust computation. */
    record TrustResult(String tier, double score, List<String> badges, List<String> warnings) {}
}
