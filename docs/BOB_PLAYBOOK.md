# Bob playbook (Bobcoin-aware build plan)

Budget: the hackathon Bob trial gives 50 Bobcoins per account. Check the Budget badge in the Bob chat after your first prompt to learn what a typical prompt costs, then adjust. Use the hackathon-provisioned Bob account, not a personal one. Bob IDE must be a core part of the build: let Bob write the scaffolding, services, UI and tests, and keep your own edits to small fixes.

Tips from the guide: front-load your highest-leverage prompts; use the sparkles "Enhance prompt" icon sparingly; use @ mentions to point Bob at files; start a new context window between phases; keep auto-approve conservative (it increases security risk) and never let Bob read `.env` (see `.bobignore`).

## Before you spend any coins (about 10 minutes, yourself)
1. Create a GitHub repo (public at submission, no keys ever). Copy this handoff folder's contents into it. Add folders `backend/`, `frontend/`, `bob_usage/` (empty with a `.gitkeep`).
2. Copy `.env.example` to `.env` and fill in values (see below). Confirm `.env` is git-ignored.
3. Install check: JDK, Node, Angular CLI, Docker (optional). Open the repo in Bob IDE and sign in with the hackathon email.
4. Get watsonx.ai values: watsonx.ai home page → "Developer access" → pick the "watsonx Hackathon Sandbox" project → copy project ID and the Dallas endpoint URL → Create API key (choose "Delete the leaked key"). In Prompt Lab, pick a Granite instruct model and use the `</>` View code panel to copy the exact request shape and model ID. **If your IBM Cloud account isn't provisioned yet, skip this and run in mock mode; add live Granite later.**
5. Optional: run `/init` only if you want Bob to extend `AGENTS.md`; the supplied file already has what Bob needs, so skipping saves coins.

## Phases and prompts (copy, paste, adjust)
Mode names follow the guide: Plan mode for planning, Code/Agent mode for building. Time boxes assume you start about 3:15 PM and want the MVP working by 4:30 (workbook checkpoint) and feature freeze at 5:00.

### Phase 0, 3:15, Plan mode, 1 prompt
> Read @AGENTS.md, @docs/PROJECT_BRIEF.md, @docs/ARCHITECTURE.md and @docs/GRANITE_PROMPTS.md. Produce a short implementation plan (max 25 lines) for the backend first, then the frontend, listing files to create and the order. Flag any ambiguity or conflict between the docs. Do not write code yet.

Review the plan; fix conflicts in the docs yourself (free), not by asking Bob to rewrite them.

### Phase 1, 3:20 to 3:50, backend core, Code mode, 2 prompts
**1a. Scaffold and data**
> In `backend/`, create a Spring Boot 3 Maven project (package com.locallink) with H2, JPA or JdbcTemplate, and Jackson. Load `schema.sql` and the JSON files in `seed/` at startup (copy them into resources via the build, do not duplicate the files by hand). Implement `GET /api/health` and `GET /api/meta` per @docs/ARCHITECTURE.md. Add a test that the app starts and meta returns 24 skills, 25 providers' worth of data and the city list.

**1b. Matching and trust**
> Implement the matching service exactly per the "Matching and trust" section of @docs/ARCHITECTURE.md and @reference/matcher_reference.py: haversine distance, tiers, score weights, warnings and the safety-first ordering. Expose `POST /api/matches`. Write unit tests that reproduce the reference output within 0.001 for the five needs in @seed/golden_needs.json (run the Python reference to get expected values). Include the cases: licensed electrician outranks a closer unverified one; a not-in-good-standing provider sorts last and shows a warning; a provider outside its service radius is excluded.

### Phase 2, 3:50 to 4:20, AI layer, Code mode, 2 prompts (new context window first)
**2a. Mock client and endpoints**
> Add a `GraniteClient` interface with `decompose`, `explain` and `draftIntro`. Implement `MockGraniteClient`: decompose via @seed/keyword_fallback.json (return up to 5 skills with a reason), template explanation and template intro as specified in @docs/GRANITE_PROMPTS.md. Implement `POST /api/needs/decompose` and `POST /api/intros/draft`, and have `/api/matches` attach `explanation` and `explanationSource`. Default `GRANITE_MODE=mock`. Add a test that every `expected_core` skill in the golden needs appears in mock decomposition.

**2b. Live watsonx.ai client**
> Implement `WatsonxGraniteClient` selected by `GRANITE_MODE=live`. Read `WATSONX_URL`, `WATSONX_PROJECT_ID`, `WATSONX_API_KEY`, `WATSONX_MODEL_ID` from environment only. Exchange the API key for an IAM token at https://iam.cloud.ibm.com/identity/token (grant_type=urn:ibm:params:oauth:grant-type:apikey), cache it and refresh a minute before expiry. Call watsonx.ai using the request shape I paste below from Prompt Lab's View code panel: [PASTE the curl or JSON here, with the key and token replaced by placeholders]. Use the prompts in @docs/GRANITE_PROMPTS.md, temperature 0. Validate JSON output, retry once, then fall back to the mock logic and set `source` to "fallback" or "template". Never log keys or tokens. Add tests using a stubbed HTTP server, no real network.

Then test live yourself once with `GRANITE_MODE=live`. Run the five golden needs through decompose; if a skill is wrong, adjust the prompt text in the docs and in code (a small manual edit is fine).

### Phase 3, 4:20 to 4:45, frontend, Code mode, 2 prompts (new context window first)
**3a. Core flow**
> In `frontend/`, create an Angular app (standalone components, strict TS) implementing the three states in the "Frontend" section of @docs/ARCHITECTURE.md against the API in @docs/ARCHITECTURE.md: post a need (with five sample-need buttons from @seed/golden_needs.json), review skills as removable chips with an "add skill" picker, and matches grouped by skill. Proxy `/api` to the backend in dev. Keep styling simple and clean.

**3b. Trust UI and intro dialog**
> Add the trust UI: badge pills (green licensed or registered, gray self-declared, red not in good standing), warning callouts, a small label showing whether an explanation came from AI or a template, a "Draft intro" dialog with editable text and Copy, and the persistent banner "Demo data: all businesses are fictional. Trust flags are simulated." Make everything keyboard accessible and never rely on color alone.

### Phase 4, 4:45 to 5:00, tests, Docker, README, Code mode, 1 prompt
> Add a multi-stage Dockerfile that builds the Angular app, copies it into the Spring Boot static resources, and runs the jar; document `docker build` and `docker run` with env vars. Update the README from @README_TEMPLATE.md. Run all tests and fix failures. List anything that doesn't match @docs/ARCHITECTURE.md.

Use Bob's code review and commit-message features for the final commits (cheap, and it shows real use of Bob). Optional stretch if time and coins remain: P2 "Offer your services", or a Code Engine deploy.

### Feature freeze at 5:00. Everything after is polish, video and submission.

## Screenshot of Bob usage (required)
- In Bob IDE, hover over the Budget badge at the top right of the Bob chat. Take a screenshot showing budget usage. Save as PNG in `bob_usage/` named like `<teamname>_michigan-devfest_IBM_Bob_usage.png`. Commit it. Take one early (for safety) and a final one near 5:00.

## Time plan against the workbook
| By | Checkpoint | Done when |
| --- | --- | --- |
| 3:15 PM | Repo ready | Handoff files in repo, .env filled or mock mode chosen |
| 3:50 PM | Backend core | Matching tests pass against reference |
| 4:20 PM | AI layer | Mock and (if keys ready) live decompose work |
| 4:30 PM | MVP working | Need → chips → matches → intro works in the browser |
| 5:00 PM | Feature freeze | Tests pass, Docker runs, README done |
| 5:30 PM | Video recorded | One-minute demo uploaded |
| 5:45 PM | Submitted | Repo URL, video URL, tools list, Bob screenshot in repo |

## If things go wrong
- No IBM Cloud account or key yet: stay in mock mode; the UI marks explanations as "template". Add live mode when available; do not block on it.
- Bobcoins running low: stop using Bob for polish; make small manual edits; use watsonx tooling and your own code for the rest. You can keep working after Bobcoins reach 100%, but there are no more.
- Granite output flaky: lower to temperature 0, shorten the prompt, rely on fallback. The demo works either way.
- Live demo risk: demo in mock mode if the network is shaky; mention live mode in the pitch.
