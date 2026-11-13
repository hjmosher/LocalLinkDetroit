# LocalLink Detroit (working name)

A verified local B2B network for Metro Detroit small businesses. Describe a need in plain language; AI breaks it into skills; a rules engine matches nearby businesses and shows what is and isn't verified about each.

Michigan DevFest + AI Hackathon, Nov 13, 2026 · Focus area: Reinvigorating Main Street

## Problem
(From workbook: user, problem statement, why it matters. Fill in with your two data points and sources.)

## What it does
1. Paste a need (contractor sub, operations help).
2. AI proposes the skills required; edit them.
3. See nearby matches with trust badges (LARA-registered, licensed, self-declared, not in good standing) and license warnings.
4. Draft an intro; nothing is sent.

## How AI is used (and where it isn't)
- IBM watsonx.ai with a Granite model: need → skills, one-sentence match explanations, intro drafts.
- Trust, scoring, badges, warnings and ordering are deterministic rules, not model output.
- Falls back to keyword and template logic if the model is unavailable.

## Tools and models (disclosure)
- IBM Bob IDE (core build tool): planning, code generation, tests, code review, commit messages.
- IBM watsonx.ai, Granite model: `<model id used>`.
- Other AI assistance: Claude (Anthropic) was used for brainstorming, idea research, drafting the project brief, handoff documents and seed data. (Edit to match what you actually used.)
- Stack: Java, Spring Boot, Angular, H2, Docker.

## Data
All data is fictional. 25 invented businesses, 24 skills, 5 sample needs in `seed/`. Trust flags (LARA status, license verification) are simulated. No personal information, no social media data, no client data. Real-world version would query the public LARA entity search and the state license lookup.
Public pages consulted while designing (not used as data): see `docs/PROJECT_BRIEF.md`.

## Run it
```
cp .env.example .env     # leave GRANITE_MODE=mock for no-key mode
cd backend && ./mvnw spring-boot:run
cd frontend && npm install && npm start
```
Live mode: set `GRANITE_MODE=live` plus the four WATSONX_ variables in your environment. Never commit keys.

## Tests
`cd backend && ./mvnw test` · `cd frontend && npm test -- --watch=false` · reference scores: `python3 -I reference/matcher_reference.py seed`

## Bob usage
Screenshot in `bob_usage/`.

## Limitations and next steps
Simulated verification; no accounts, messaging or payments; limited to Metro Detroit demo cities. Next: real registry and license lookups, offer-your-services flow, provider onboarding, intros.
