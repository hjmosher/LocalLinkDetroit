"""Reference implementation of the matching and trust logic.
Purpose: a readable spec and test oracle. The Java service should reproduce these scores
(within 0.001) for the golden needs. Run: python3 -I matcher_reference.py ../seed
"""
import json, math, sys

W = {"trust": 0.35, "distance": 0.30, "depth": 0.20, "availability": 0.15}
AVAIL = {"this_month": 1.0, "flexible": 0.8, "next_month": 0.6}
DEFAULT_RADIUS = 40.0
LOOKUP = "https://www.michigan.gov/en/lara/bureau-list/cscl/online-services"

def haversine(a, b, c, d):
    r = 3958.8
    p1, p2 = math.radians(a), math.radians(c)
    dp, dl = p2 - p1, math.radians(d - b)
    h = math.sin(dp/2)**2 + math.cos(p1)*math.cos(p2)*math.sin(dl/2)**2
    return 2*r*math.asin(math.sqrt(h))

def trust(p, skill, regulated):
    """Returns (tier, trust_score, badges, warnings) for provider p and one skill."""
    badges, warnings = [], []
    verified_lic = [l for l in p["licenses"] if skill in l["skills"] and l["state_verified"]]
    claimed_lic = [l for l in p["licenses"] if skill in l["skills"] and not l["state_verified"]]
    if p["lara_status"] == "active_good_standing":
        badges.append("Registered with LARA, in good standing (simulated)")
    elif p["lara_status"] == "not_in_good_standing":
        badges.append("LARA: not in good standing (simulated)")
        warnings.append("This business is not in good standing with LARA. Verify before hiring.")
    else:
        badges.append("Self-declared (not in the LARA registry)")
    for l in verified_lic:
        badges.append(f"{l['type']}: verified (simulated)")
    for l in claimed_lic:
        warnings.append(f"{l['type']} claimed but not verified.")
    if p["lara_status"] == "not_in_good_standing":
        tier, score = "not_in_good_standing", 0.10
    elif verified_lic:
        tier, score = "licensed_verified", 1.0
    elif p["lara_status"] == "active_good_standing":
        tier, score = "registered", 0.85
    else:
        tier, score = "self_declared", 0.50
    if regulated and not verified_lic:
        warnings.append(f"This type of work may require a state license. Check the state license lookup: {LOOKUP}")
    return tier, score, badges, warnings

def match(seed, skill_id, lat, lon, radius=DEFAULT_RADIUS):
    tax = {s["id"]: s for s in seed["tax"]["skills"]}
    reg = tax[skill_id]["regulated_hint"]
    out = []
    for p in seed["providers"]:
        if skill_id not in p["skills"]:
            continue
        d = haversine(lat, lon, p["lat"], p["lon"])
        reach = min(radius, p["service_radius_miles"])
        if d > reach:
            continue
        tier, ts, badges, warnings = trust(p, skill_id, reg)
        parts = {
            "trust": ts,
            "distance": max(0.0, 1 - d/reach),
            "depth": 1.0 if p["skills"][0] == skill_id else 0.7,
            "availability": AVAIL[p["availability"]],
        }
        score = sum(W[k]*v for k, v in parts.items())
        out.append({"id": p["id"], "name": p["name"], "structure": p["structure"], "city": p["city"],
                    "distance_miles": round(d, 1), "tier": tier, "score": round(score, 3),
                    "breakdown": {k: round(v, 3) for k, v in parts.items()},
                    "badges": badges, "warnings": warnings})
    # Safety-first ordering: tier decides the order BEFORE the numeric score.
    # Regulated work: licensed_verified > registered > self_declared > not_in_good_standing.
    # Other work: anyone not in good standing sorts last; everyone else is ordered by score.
    if reg:
        pri = {"licensed_verified": 0, "registered": 1, "self_declared": 2, "not_in_good_standing": 3}
    else:
        pri = {"licensed_verified": 0, "registered": 0, "self_declared": 0, "not_in_good_standing": 1}
    return sorted(out, key=lambda m: (pri[m["tier"]], -m["score"], m["distance_miles"], m["id"]))

if __name__ == "__main__":
    d = sys.argv[1]
    seed = {"tax": json.load(open(f"{d}/skills_taxonomy.json"))}
    pr = json.load(open(f"{d}/providers.json"))
    seed["providers"], cities = pr["providers"], pr["cities"]
    needs = json.load(open(f"{d}/golden_needs.json"))["needs"]
    for n in needs:
        c = cities[n["city"]]
        print(f"\n### {n['id']} {n['persona']}  ({n['city']})")
        for s in n["expected_core"]:
            ms = match(seed, s, c["lat"], c["lon"])
            print(f"  {s}: {len(ms)} match(es)")
            for m in ms[:3]:
                w = " | WARN" if m["warnings"] else ""
                print(f"    {m['score']:.3f}  {m['name']} ({m['city']}, {m['distance_miles']} mi, {m['tier']}){w}")
