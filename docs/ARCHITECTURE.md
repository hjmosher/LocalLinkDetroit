# Architecture

## Stack
- Backend: Java with Spring Boot 3.x (use the JDK installed locally; run `java -version`), Maven.
- Frontend: Angular (current stable; run `ng version`), standalone components, TypeScript strict.
- Data: H2 in-memory, loaded at startup from `seed/` (JSON) using `schema.sql`. Keep SQL portable to Postgres.
- AI: IBM watsonx.ai (Granite instruct model) over REST, behind an interface with a mock implementation.
- Packaging: Dockerfile for backend (and static frontend). Optional IBM Code Engine deploy.

## Components
```
Angular SPA  --->  Spring Boot REST API  --->  H2 (providers, skills, licenses)
                        |
                        +--> GraniteClient interface
                              |- MockGraniteClient (keyword fallback, templates)   [default]
                              |- WatsonxGraniteClient (IAM token + REST)           [GRANITE_MODE=live]
```
Rules decide trust and ranking. The model only (1) maps text to skill ids, (2) writes a one-sentence explanation from supplied facts, (3) drafts an intro. Never let the model choose providers, scores or badges.

## Data model (see `schema.sql`)
`schema.sql` lives at `backend/src/main/resources/schema.sql` (DDL only). The Maven build copies `../seed/*.json` into the classpath; a startup loader reads the JSON. Do not duplicate seed data by hand.

- `skill(id, label, grp, regulated_hint)`: from `seed/skills_taxonomy.json`.
- `provider(id, name, structure, city, lat, lon, service_radius_miles, summary, lara_status, availability, fictional)`: from `seed/providers.json`.
- `provider_skill(provider_id, skill_id, position)`: `position` 0 is the provider's primary skill.
- `license(id, provider_id, type, state_verified)` plus `license_skill(license_id, skill_id)`.
- `structure`: sole_prop | partnership | llc | corp. `lara_status`: active_good_standing | not_in_good_standing | not_applicable. `availability`: this_month | next_month | flexible.
- City coordinates for the demo dropdown are in `providers.json` under `cities`.

## REST API
All JSON. Errors: `{ "error": "code", "message": "human text" }` with a sensible HTTP status.

`GET /api/health` → `{ "status": "ok", "graniteMode": "mock|live" }`

`GET /api/meta` → `{ "cities": [ { "name": "Royal Oak", "lat": 42.4895, "lon": -83.1446 } ], "skills": [ {id,label,group,regulatedHint} ], "demoData": true }`. The frontend uses only `name`; the backend resolves coordinates from the city name in each request.

`POST /api/needs/decompose`
```json
// request
{ "text": "I'm a general contractor building a deck...", "city": "Royal Oak" }
// response
{ "summary": "Deck build in Royal Oak needing concrete, framing and electrical work",
  "skills": [ { "skillId": "concrete", "label": "Concrete footings and flatwork",
                "reason": "Mentions concrete footings", "regulatedHint": false } ],
  "clarifyingQuestion": null,
  "source": "granite|fallback" }
```

`POST /api/matches`
```json
// request: skills come from the user-edited chips
{ "city": "Royal Oak", "skillIds": ["concrete","framing","electrical"], "radiusMiles": 40 }
// response
{ "groups": [ { "skillId": "electrical", "label": "Electrical", "regulatedHint": true,
    "matches": [ { "providerId": "demo-024", "name": "Lumen Light & Power Inc.", "structure": "corp",
       "city": "Southfield", "distanceMiles": 4.4, "tier": "licensed_verified", "score": 0.962,
       "badges": ["Registered with LARA, in good standing (simulated)", "Electrical contractor (state): verified (simulated)"],
       "warnings": [], "summary": "...", "availability": "this_month",
       "explanation": "One sentence from Granite or the template.",
       "explanationSource": "granite|template" } ] } ] }
```

`POST /api/intros/draft`
```json
{ "needText": "...", "city": "Royal Oak", "providerId": "demo-024", "skillId": "electrical" }
→ { "body": "Hi ...", "source": "granite|template" }   // draft only; never sent
```

P2: `POST /api/providers/extract` `{ "bio": "..." }` → a profile object (no id) for the user to confirm; `POST /api/providers` adds it to the in-memory set.

## Matching and trust (authoritative spec: `reference/matcher_reference.py`)
For each requested skill:
1. Candidates: providers whose `skills` contain it.
2. Distance: haversine miles from the chosen city's coordinates to the provider. Keep only if distance ≤ min(requested radius, provider.service_radius_miles). Default radius 40.
3. Tier per provider for that skill:
   - `not_in_good_standing` if lara_status is not_in_good_standing (score 0.10)
   - else `licensed_verified` if a license covers the skill and state_verified (score 1.0)
   - else `registered` if active_good_standing (0.85)
   - else `self_declared` (0.50)
4. Score = 0.35·trust + 0.30·distance + 0.20·depth + 0.15·availability, where distance = 1 − d/min(radius, service radius), depth = 1.0 if it is the provider's primary skill else 0.7, availability = this_month 1.0, flexible 0.8, next_month 0.6.
5. Warnings: not in good standing; license claimed but not verified; and, if the skill's `regulated_hint` is true and the provider has no verified license for it, "This type of work may require a state license" with the lookup link `https://www.michigan.gov/en/lara/bureau-list/cscl/online-services`.
6. Ordering (safety first, before score): regulated skill → licensed_verified, registered, self_declared, not_in_good_standing; other skills → not_in_good_standing last, everyone else by score. Ties: smaller distance, then id.
7. Badges and warnings are produced by code, never by the model.

## Granite integration
- Config (env vars only, never in code or git): `WATSONX_URL`, `WATSONX_PROJECT_ID`, `WATSONX_API_KEY`, `WATSONX_MODEL_ID`, `GRANITE_MODE` (`mock` default | `live`).
- Auth: exchange the API key for an IAM token with a POST to `https://iam.cloud.ibm.com/identity/token` (form body `grant_type=urn:ibm:params:oauth:grant-type:apikey&apikey=...`). Tokens last about 60 minutes; cache and refresh a minute early.
- Region: the hackathon project is in Dallas. Get project ID, endpoint URL and the exact request shape from the watsonx.ai Prompt Lab "View code" panel (or the home page "Developer access" section) and copy it; do not guess paths. Use the chat or text generation API with a Granite instruct model selected in Prompt Lab.
- Prompts and output schemas: `docs/GRANITE_PROMPTS.md`. Temperature 0, small max tokens.
- Every model call: validate the JSON against the schema; retry once on invalid output; otherwise fall back (keyword decomposition, template explanation, template intro) and set `source` accordingly. A model failure must never produce a 500.
- Never send secrets or personal information in prompts. Inputs are the user's need text (a demo sentence) and fictional provider facts.
- Cost is tiny (1,000 tokens = 1 Resource Unit = $0.0001), but cache identical decomposition and explanation calls in memory so demos are fast and repeatable.

## Fallback decomposition
`seed/keyword_fallback.json` maps keywords to skill ids. Lowercase the text, match keywords, rank by number of hits, return up to 5 skills with a reason like "Mentions 'footings'". It must satisfy the golden needs so mock mode passes.

## Frontend
Source labels: `granite` → "AI (Granite)", `template` → "Template", `fallback` → "Keyword fallback".

Single page, three states:
1. **Post a need:** textarea, city select, "Find help" button, sample-need buttons (the five golden needs).
2. **Review skills:** chips from decompose; user can remove, or add from the full list; then "Show matches".
3. **Matches:** one section per skill; cards with name, structure chip, city and distance, trust badge pills (green for licensed or registered, gray for self-declared, red for not in good standing), warning callouts, one-line explanation, "Draft intro" button opening a dialog with editable text and Copy.
Always show the demo-data banner. Show the explanation source (AI or template) as a small label. Accessible: labels on inputs, keyboard-operable dialog, color is never the only signal (badges have text).

## Testing
- Unit tests: scoring, tier logic, ordering rule, haversine. Use golden scores from `reference/matcher_reference.py` output (run it and copy expected values).
- Golden decomposition test: for each need in `seed/golden_needs.json`, mock-mode decompose includes every `expected_core` skill and no `should_not_include` skill.
- API tests (MockMvc): decompose, matches, intros in mock mode.
- Frontend: a basic component test for badge rendering; a manual run-through of the demo script.
