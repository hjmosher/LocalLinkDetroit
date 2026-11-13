# AGENTS.md: LocalLink Detroit

Read first: `docs/PROJECT_BRIEF.md`, `docs/ARCHITECTURE.md`, `docs/GRANITE_PROMPTS.md`. The authoritative scoring spec is `reference/matcher_reference.py`; seed data is in `seed/`.

## What we are building
A verified local B2B matching demo for a hackathon: an owner describes a need, AI maps it to skills, a deterministic matcher ranks nearby fictional businesses with trust badges, AI explains matches and drafts intros. Solo builder, deadline 6:00 PM. Prefer the simplest thing that works and a demo that never crashes.

## Stack and layout
- `backend/`: Spring Boot 3.x (Maven), Java version = whatever the local JDK is. Package `com.locallink`.
- `frontend/`: Angular (current stable), standalone components, strict TypeScript.
- `seed/`, `reference/`, `docs/`: inputs, do not edit seed data without being asked.
- `bob_usage/`: holds the Bob usage screenshot (do not delete).

## Commands (create scripts so these work)
- Backend tests: `cd backend && ./mvnw test`
- Backend run: `cd backend && ./mvnw spring-boot:run` (default `GRANITE_MODE=mock`)
- Frontend: `cd frontend && npm install && npm start`; tests `npm test -- --watch=false`
- Reference check: `python3 -I reference/matcher_reference.py seed`

## Hard rules
1. **No secrets in the repo.** Read `WATSONX_URL`, `WATSONX_PROJECT_ID`, `WATSONX_API_KEY`, `WATSONX_MODEL_ID`, `GRANITE_MODE` from environment variables only. Never write real values into any file, test, log or Dockerfile. `.env` is git-ignored; `.env.example` has placeholders.
2. **Data is fictional only.** No real businesses, people, emails, phones or addresses. No personal information. No scraping or calls to any government site. Trust flags are simulated and labeled as such in the UI.
3. **The model never decides trust or ranking.** Tier, score, badges, warnings and order come from code that matches `reference/matcher_reference.py`. The model only maps text to skill ids, writes one explanatory sentence from supplied facts, and drafts an intro.
4. **Model failures never break the app.** Validate model JSON, retry once, then fall back to the keyword decomposer or templates. Mock mode must work with no network.
5. **Do not use** llama-3-405b-instruct, mistral-medium-2505 or mistral-small-3-1-24b-instruct-2503. Use a Granite instruct model ID supplied by env var.
6. **Never claim a license is or isn't required.** Only say work "may require a state license" for skills flagged `regulated_hint`, and link the lookup.
7. The app must show the "Demo data: all businesses are fictional" banner on every screen.

## Style
- Small classes, constructor injection, records for DTOs, no Lombok unless already present.
- Errors as `{ "error": code, "message": text }`. No stack traces to clients.
- Keep dependencies minimal. No new frameworks without asking.
- Comment the "why" for scoring and fallback logic; reference the spec file.
- Tests before moving on: scoring tests must reproduce the reference output within 0.001.

## Definition of done for any task
Builds, tests pass, runs in mock mode, matches the API contract in `docs/ARCHITECTURE.md`, no secrets, and the change is described in a short commit message.
