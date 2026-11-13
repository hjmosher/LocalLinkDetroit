# LocalLink Detroit (working name): Project brief

Michigan DevFest Hackathon, Nov 13, 2026. Focus area: **Reinvigorating Main Street** (also touches Building Michigan's Innovation Ecosystem).
Submission closes 6:00 PM. Solo builder. Bob IDE must be a core part of the build.

## One-liner
A verified local B2B network for Metro Detroit small businesses. An owner describes a need in plain language; AI breaks it into the skills required, then matches nearby businesses and shows exactly what is and isn't verified about each one.

## Problem statement
A small contractor or owner-operator struggles to find trustworthy nearby businesses for work they can't or don't want to do themselves, because they don't know which skills a job needs, who is registered or licensed, or who is nearby, which costs them delays, risky hires and time spent on admin instead of their craft.

## Users
1. **Contractor needing a sub for part of a job** (e.g. a general contractor building a deck who needs concrete, framing and electrical).
2. **Owner-operator who wants operations help** (e.g. a landscaper buried in invoicing, scheduling and payroll who wants to focus on the work).
Providers on the other side are small businesses, including sole proprietors.

## Value proposition
For small contractors and owner-operators in Metro Detroit, LocalLink is a verified local B2B network that turns a plain-language need into the skills required and nearby matches with clear trust badges, unlike general marketplaces, which don't separate registered, licensed and self-declared providers.

## Design for AI (use these answers on the workbook slide)
- **What does AI do for the user?** Turns a messy description into a structured list of needed skills, explains why each match fits, and drafts a short intro message.
- **Where does a human stay in control?** The user edits the skill list before any matching, and approves every intro. The app never contacts anyone.
- **What happens when the AI is wrong?** Skills are shown as editable chips, so a wrong guess is one click to fix. Matching and ranking are deterministic rules, not model output. If the model fails, a keyword fallback keeps the demo working.
- **How do we earn trust?** Trust badges come from rules over structured data, not from the model. Every match says what is verified and what isn't. Regulated work gets a visible "may require a license" warning with a link to the state license lookup. A permanent banner says all businesses are fictional demo data.

## Trust model (the differentiator)
Each provider shows badges, derived per skill:
- **Registered with LARA, in good standing** (LLCs and corporations; simulated flag in this demo).
- **Licensed, verified** (holds a state license for that skill; simulated flag).
- **Self-declared** (sole proprietors and anyone not in the LARA registry). Sole proprietors are in scope: they can list and be matched, but are labeled honestly.
- **Not in good standing** (shown with a warning, always sorted last).
Safety-first ordering: for regulated work, verified-licensed providers sort above everyone else regardless of distance; anyone not in good standing sorts last. See `reference/matcher_reference.py`.

## MVP scope
**P0 (must work for the demo)**
- "Post a need" screen: free text + city dropdown.
- AI decomposes the need into skills (editable chips).
- Matches grouped by skill, each card showing distance, structure, trust badges, warnings and a one-sentence "why this match".
- "Draft intro" produces an editable message with a copy button. Nothing is sent.
- Persistent "Demo data: all businesses are fictional" banner.
- Mock mode so everything works with no network or API key.

**P1**
- Live watsonx.ai Granite calls behind the same interfaces (switch by env var).
- Docker build and a documented local run; Code Engine deploy if time allows.

**P2 (only if time remains)**
- "Offer your services" screen: paste a bio, AI extracts a profile, add it to the in-memory network.
- Map view.

## Non-goals
Logins, messaging, payments, reviews, real registry lookups or scraping, any personal information, any data from social media, any claim that a license is or isn't required.

## Hard constraints from the hackathon guide
- Bob IDE must be a core component, and a Bob usage screenshot must be in `bob_usage/` in the repo.
- Data must be compliant: this project uses only fictional data. No personal information, no social media data, no client or confidential data.
- Never commit API keys. An exposed IBM Cloud credential gets the account suspended.
- Do not use these watsonx.ai models: llama-3-405b-instruct, mistral-medium-2505, mistral-small-3-1-24b-instruct-2503. Use a Granite instruct model.
- The hackathon cloud accounts do not support watsonx.ai solution deployment; plan to run locally for the demo, with Code Engine as a bonus.
- Note every AI tool and model used (judges will ask). See `README_TEMPLATE.md`.

## Demo script
**One-minute video (workbook slide 04)**
- 0:00 The user: a contractor with a deck project and no idea who to call.
- 0:10 The problem: three trades to find, no way to tell who's registered or licensed.
- 0:20 Click 1: paste the need; the AI proposes skills; remove one, add one.
- 0:30 Click 2: matches appear with badges; the licensed electrician is on top even though another is closer; show the warning on the unverified one.
- 0:40 Click 3: draft an intro to the top match.
- 0:50 The impact: faster, safer local hiring that keeps work in Michigan.

**Three-minute pitch:** problem 20s, why us 20s, live demo 90s (use need-1, then need-5 to show the license warning), how it works 30s (rules decide trust, AI explains), what's next 20s (real registry and license lookups, offer-your-services, intros).

## Success criteria
- The five golden needs in `seed/golden_needs.json` decompose to include every `expected_core` skill and none of the `should_not_include` skills (live and mock mode).
- Match scores equal `reference/matcher_reference.py` within 0.001, and the ordering rule holds.
- Full flow works with `GRANITE_MODE=mock` and no network.
