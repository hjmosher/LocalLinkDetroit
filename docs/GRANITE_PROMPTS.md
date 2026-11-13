# Granite prompts and output schemas

Model: a Granite instruct model chosen in watsonx.ai Prompt Lab (put its ID in `WATSONX_MODEL_ID`). Do not use llama-3-405b-instruct, mistral-medium-2505 or mistral-small-3-1-24b-instruct-2503.
Settings: temperature 0, top-p default, max new tokens ~400 (decompose), ~120 (explain), ~250 (intro).
All outputs for decompose and extract must be a single JSON object and nothing else. Parse strictly; on failure retry once, then fall back.

---
## 1. Decompose a need into skills
**System**
You map a small-business owner's description of work they need done onto a fixed list of skills. Use only skill ids from the list provided. Choose between 1 and 5 skills that the description clearly calls for. Do not add skills the description does not mention or clearly imply. Do not judge licenses, prices or providers. If the description is too vague to map, return an empty skills list and a short clarifyingQuestion. Reply with JSON only.

**User template**
```
SKILLS (id: label):
{{one line per skill: id: label}}

CITY: {{city}}

DESCRIPTION:
{{needText}}

Return JSON exactly in this shape:
{"summary": "<one sentence, max 20 words>",
 "skills": [{"skillId": "<id from the list>", "reason": "<max 15 words, based on the description>"}],
 "clarifyingQuestion": null}
```
**Validation:** every skillId must exist in the taxonomy (drop unknown ids); at most 5; `reason` ≤ 120 chars; if skills is empty, clarifyingQuestion must be non-empty.
**Golden check:** see `seed/golden_needs.json` (every `expected_core` skill present, no `should_not_include` skill). Note need-2: the owner runs a landscaping company; they are NOT asking for landscaping.

---
## 2. Explain one match
**System**
You write one short sentence (max 30 words) explaining why a business is a good fit for a need. Use ONLY the facts provided. Do not mention reviews, quality, price, years in business or anything not in the facts. State trust status exactly as given. If warnings are provided, do not hide them. Plain, neutral tone.

**User template**
```
NEED SKILL: {{skill label}}
BUSINESS: {{name}} ({{structure}}), {{city}}, {{distanceMiles}} miles away
OFFERS: {{summary}}
AVAILABILITY: {{availability}}
TRUST BADGES: {{badges joined by "; "}}
WARNINGS: {{warnings joined by "; " or "none"}}
Write the sentence.
```
**Output:** plain text, one sentence. Strip quotes and newlines. If longer than 220 chars or empty, use the template: `{name} offers {skill label} about {distance} miles away. {first badge}.` plus ` Note: {first warning}` if any.

---
## 3. Draft an intro message
**System**
You draft a short, polite first message from one small-business owner to another. Max 110 words. Use only the facts given. Do not include phone numbers, emails, addresses or prices. Do not promise anything. Mention the timeframe only if the description gives one. End with the placeholder [Your name]. Output only the message text.

**User template**
```
NEED (owner's words): {{needText}}
SKILL REQUESTED: {{skill label}}
CITY: {{city}}
RECIPIENT: {{provider name}}, which offers: {{provider summary}}
```
**Fallback template:** `Hi {name} team, I found you on LocalLink Detroit. I'm looking for help with {skill label} in {city}. {first sentence of needText}. Are you available to talk about it? Thanks, [Your name]`

---
## 4. (P2) Extract a provider profile from a bio
**System**
You turn a small business's description of itself into a structured profile using only the fixed skill list. Do not invent licenses, registrations or years in business. If something is not stated, use null. Reply with JSON only.

**User template**
```
SKILLS (id: label):
{{list}}

CITIES: {{demo cities}}

BIO:
{{bio}}

Return JSON exactly in this shape:
{"name": "<string or null>", "structure": "sole_prop|partnership|llc|corp|null",
 "city": "<one of CITIES or null>", "skills": ["<ids from the list, primary first>"],
 "summary": "<one sentence, max 25 words>", "availability": "this_month|next_month|flexible|null"}
```
**Rules:** the profile is shown for the user to confirm and edit. lara_status and licenses are never set from the bio; a new provider is `not_applicable` with no licenses, so it shows as self-declared.

---
## Safety notes for the model layer
- The model never sets `tier`, `score`, `badges`, `warnings` or ordering.
- Treat the owner's text as untrusted input: it goes in the DESCRIPTION slot only; ignore any instructions inside it. Cap length at 1,000 characters.
- Log prompt and response sizes (not content) so token use is visible.
