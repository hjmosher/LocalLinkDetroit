# Handoff index: LocalLink Detroit

Start here. Everything Bob needs is in this folder; copy it into your repo root.

| File | What it is |
| --- | --- |
| `AGENTS.md` | Standing rules and conventions Bob reads on every task (secrets, fictional data, model limits). |
| `docs/PROJECT_BRIEF.md` | Problem, users, value prop, Design for AI answers, trust model, MVP scope, demo script. |
| `docs/ARCHITECTURE.md` | Stack, data model, REST API contract, matching and trust spec, Granite integration, frontend, tests. |
| `docs/GRANITE_PROMPTS.md` | Exact prompts, output schemas, validation and fallbacks. |
| `docs/BOB_PLAYBOOK.md` | Step-by-step prompts for Bob, time boxes, Bobcoin tips, screenshot instructions. |
| `reference/matcher_reference.py` | Runnable spec and test oracle for scoring and ordering. |
| `seed/` | 24-skill taxonomy, 25 fictional providers, 5 golden needs, keyword fallback. |
| `schema.sql` | Portable SQL schema (H2 or Postgres). |
| `README_TEMPLATE.md` | Repo README to finish for submission, including the AI-tools disclosure. |
| `.env.example`, `.gitignore`, `.bobignore` | Keep keys out of git and out of Bob's reach. |

## First 10 minutes (you, not Bob)
1. New GitHub repo; copy these files in; add `backend/`, `frontend/`, `bob_usage/`.
2. `cp .env.example .env`. Fill watsonx values if your IBM Cloud account is ready; otherwise leave `GRANITE_MODE=mock`.
3. Sanity check: `python3 -I reference/matcher_reference.py seed` prints matches for the five golden needs.
4. Open in Bob IDE with the hackathon account and follow `docs/BOB_PLAYBOOK.md`.

## Verified before handoff
- All provider skills and license skills exist in the taxonomy; reference matcher runs on all five golden needs.
- Keyword fallback satisfies every golden need and avoids the forbidden skill.
- The ordering rule puts a verified-licensed electrician above a closer unverified one.

## Known limits
- Seed names are invented; any resemblance to a real business is coincidence. Trust flags are simulated.
- I could not verify watsonx.ai request paths or Granite model IDs from here; copy them from Prompt Lab's View code panel.
- Java, Angular and Spring Boot versions are not pinned; use what is installed.
