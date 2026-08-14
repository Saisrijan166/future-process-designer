# AI Future Process Designer — Full Build Plan
### Modus ETI Enterprise AI Build Challenge — Assignment 3

**Owner:** Saisrijan
**Target:** A small, real, working, free-hosted, end-to-end AI application — not a demo, not a prompt wrapper.
**Instruction to Claude Code:** Follow this document top to bottom. Do not skip sections. Ask no clarifying questions unless a step is genuinely blocked — make the sensible default choice documented here and proceed. Build incrementally, verify each layer runs before moving to the next, and keep the scope exactly as defined below (do not silently expand it).

---

## 0. Non-Negotiable Rules (from the assignment brief — do not violate any of these)

1. Must be a **real, working application**: frontend + backend + database + AI integration. Not a Notebook, not static HTML, not a slide deck, not a spreadsheet.
2. Must be built **from scratch** during this effort. Pre-existing libraries/frameworks are fine and must be listed in `LIBRARIES.md`.
3. Every tool, model, API, database, and hosting service used must be **free, open-source, free-tier, or locally runnable**. No paid license required to run or demo it.
4. **Data must persist.** Restarting the app must not wipe intelligence — everything lives in Postgres, nothing important lives only in memory.
5. The **future process must not be a paragraph of prose.** It must be stored as structured rows (activities, roles, systems, AI interventions, relationships) that can be queried and compared.
6. Must process **multiple records systematically** — the AI pipeline must work identically on any process, not just the demo ones. This is the **"Surprise Record" / "Process 101" test**: a judge can create a brand-new process live, click Analyze, and get a real structured result with no code changes.
7. No hard-coded outputs. No giant single mega-prompt that pretends to be "the whole system." The reasoning must be broken into a pipeline with real backend logic around it.
8. Outputs must be **traceable** — every AI-generated claim should be linkable back to the process/activity/evidence that produced it.
9. Must ship: source code, README/setup instructions, architecture diagram, data model, model/library inventory with licenses, sample data, research sources, and a 10–15 min live demo script.
10. Keep it **small and finishable**, not a platform. One industry, ~6 seed processes, one clean pipeline, done properly.

---

## 1. Chosen Domain

**Industry:** Online Education & Digital Assessment
**Fictional organisation:** "**AssessWise**" — an ed-tech company running online exams, question banks, proctoring, grading and certification (deliberately close to real-world experience with the Think Exam platform, so the domain modeling is fast and credible, and every design decision in the demo can be explained honestly).

### Seed processes (create these 6 as sample/synthetic data — NOT the only processes the system can ever handle)

| # | Process | Why it matters |
|---|---|---|
| 1 | Online Assessment Creation | Core authoring workflow |
| 2 | Question Bank Management | Content quality/reuse |
| 3 | Candidate Onboarding & Proctoring | Trust/integrity-heavy |
| 4 | Result Evaluation & Grading | High manual effort today |
| 5 | Certification Issuance | Compliance-adjacent |
| 6 | Learner Support & Doubt Resolution | High volume, repetitive |

These exist only to seed the demo and prove the pipeline works on real-looking data. The **judge's live "Process 101" test must work on a completely different, unseen process** — this is the actual pass/fail criterion, so the pipeline must never assume anything about these six specifically.

---

## 2. Architecture (must match the mandatory 5-layer gate from the brief)

```
┌─────────────────────────────────────────┐
│  UI — Next.js (React, TypeScript)        │
│  Process list · New Process form ·       │
│  Current / Transition / Future tabs      │
└───────────────────┬───────────────────────┘
                    ▼ REST (JSON)
┌─────────────────────────────────────────┐
│  API — Spring Boot (Java)                │
│  ProcessController · AnalysisController  │
│  EvidenceController                      │
└───────────────────┬───────────────────────┘
                    ▼
┌─────────────────────────────────────────┐
│  AI INTELLIGENCE LAYER                   │
│  AiAnalysisService → Gemini API          │
│  (behind a swappable AiProvider          │
│  interface — one live implementation)    │
│  Structured-JSON prompting + validation  │
│  Curated knowledge-snippet grounding     │
└───────────────────┬───────────────────────┘
                    ▼
┌─────────────────────────────────────────┐
│  DATA & KNOWLEDGE LAYER                  │
│  PostgreSQL (Neon, free serverless)      │
│  Processes · Activities · Problems ·     │
│  Roles · Systems · AIOpportunities ·     │
│  FutureActivities · AIInterventions ·    │
│  Evidence/Sources                        │
└─────────────────────────────────────────┘
```

This maps 1:1 onto your existing Portfoliooss stack (Next.js + Spring Boot + Neon Postgres), so no new infra concepts — just a new domain and a new AI pipeline.

---

## 3. Tech Stack (100% free) + Required Fallback Answer

| Layer | Choice | Free tier | If it becomes paid/unavailable |
|---|---|---|---|
| Frontend hosting | Vercel | Free hobby tier | Move to Render static / Netlify free tier — no code change, just redeploy |
| Frontend framework | Next.js + TypeScript | Open source | N/A |
| Backend hosting | Render (Web Service, free) | Free (cold start ~50s) | Fly.io free tier or Railway trial — service is a plain Spring Boot jar, portable |
| Backend framework | Spring Boot (Java 17) | Open source | N/A |
| Database | Neon Postgres | Free serverless tier | Supabase Postgres free tier (same SQL, just change connection string) |
| AI model | Gemini 1.5 Flash (Google AI Studio API key) | Free tier | **Not built — explained in README only** (this is all the brief asks for): if Gemini's free tier stopped being free, the model call is isolated behind one `AiProvider` interface, so swapping to Groq or a local Ollama model would be a config/implementation-class change, not a rewrite. No second live provider or runtime failover is implemented, since the brief only requires an explanation, not working failover code. |
| ORM | Spring Data JPA / Hibernate | Open source | N/A |
| Auth (minimal) | None required for MVP (single-tenant demo); documented as a "Phase 2" extension | — | — |

**Why no live web-search API:** to keep the 2-day build reliable and avoid depending on a rate-limited/unstable free search API during the live demo, "research" is implemented as a small **curated knowledge-snippet table** (10–15 short, cited excerpts on AI-in-education trends, each with a source URL, source type, and retrieval date). These snippets are retrieved and injected into the AI prompt as grounding context (lightweight RAG), and every AI Opportunity stores which snippet(s) informed it. This satisfies "Outputs must be traceable to underlying data, research or reasoning" honestly, without a fragile live-internet dependency during evaluation. State this design decision plainly in the README and in the demo — it's a defensible, intentional trade-off, not a shortcut being hidden.

---

## 4. Data Model (the structured "not paragraphs" requirement)

Implement as JPA entities + Postgres tables. Use UUID primary keys.

```
Process
 - id, name, industry, description, status (CURRENT_ONLY / ANALYZED), created_at

Activity              (belongs to a Process; represents CURRENT state)
 - id, process_id, name, sequence_order, description

Problem               (linked to a Process, optionally to an Activity)
 - id, process_id, activity_id (nullable), description, severity (LOW/MED/HIGH)

Role
 - id, name                          -- shared lookup table, e.g. "Exam Coordinator"

ActivityRole          (join table: which roles perform which current activities)
 - activity_id, role_id

SystemTool
 - id, name, type                    -- shared lookup, e.g. "LMS", "Proctoring Engine"

ActivitySystem         (join table: which systems support which current activities)
 - activity_id, system_id

AIOpportunity
 - id, process_id, activity_id (nullable), description, ai_capability,
   automation_potential (LOW/MEDIUM/HIGH), business_benefit, risk,
   reasoning_note, evidence_ids (join table AIOpportunityEvidence)

FutureActivity         (belongs to a Process; represents FUTURE state)
 - id, process_id, name, sequence_order, description,
   human_responsibility, ai_responsibility,
   responsibility_type (AI_AUTOMATED / AI_AUGMENTED / HUMAN_LED)

AIIntervention          (links a FutureActivity back to what changed and why)
 - id, process_id, future_activity_id, related_ai_opportunity_id,
   intervention_type (AUTOMATE/AUGMENT/ELIMINATE/NEW),
   description

KnowledgeSnippet        (curated research grounding — the "Evidence" layer)
 - id, title, snippet_text, source_url, source_type
   (LAW/GUIDANCE/STANDARD/RESEARCH/VENDOR/GENERAL_WEB), retrieved_at

AIOpportunityEvidence   (join table: which snippets support which opportunity)
 - ai_opportunity_id, knowledge_snippet_id
```

This gives a clean **CURRENT** (Activity/Problem/Role/System) vs **FUTURE** (FutureActivity/AIIntervention) split, with **AIOpportunity** as the explicit "transition" reasoning layer connecting them — matching the brief's `Current → Activities → Problems → AI Opportunities → Future Process → Human vs AI → Benefit` chain exactly.

Produce a real ER diagram (`docs/data-model.png` or `.md` with Mermaid) as part of deliverables — see Section 12.

---

## 5. AI Analysis Pipeline (the core intelligence layer)

Endpoint: `POST /api/processes/{id}/analyze`

Steps the backend performs (all real code, not a single prompt pretending to be the whole system):

1. **Load** the Process + its Activities from Postgres.
2. **Retrieve grounding context**: run a simple keyword-match against `KnowledgeSnippet` (match on process/industry keywords — no vector DB needed at this scale; keep it simple and explainable) and select the top 3–5 relevant snippets.
3. **Build a structured prompt** (see template below) combining: process name/description, activity list, and the retrieved snippets. Explicitly instruct the model to return **strict JSON only**, matching a fixed schema.
4. **Call `AiProvider.analyze(prompt)`** — a small interface with **one** live implementation (`GeminiProvider`). The interface exists purely so a future provider swap is a config change, not a rewrite; no second live provider is built, since the brief only requires explaining the "what if it becomes paid" scenario, not implementing runtime failover.
5. **Validate** the JSON response against the expected schema (problems[], ai_opportunities[], future_activities[], ai_interventions[]). Reject and retry once (with a stricter "return valid JSON only" repair prompt) if parsing fails.
6. **Persist** each element into its respective table, wiring foreign keys (activity_id, evidence_ids, related_ai_opportunity_id) so everything is queryable afterward — this is what makes it "structured," not prose.
7. **Mark** `Process.status = ANALYZED` and return the full structured result to the frontend.
8. This exact same code path runs for the 6 seed processes and for any brand-new process created live — **no branching logic based on which process it is.** This is what passes the Surprise Record test.

### Prompt template (store in `prompts/analyze-process.txt`, keep out of code)

```
You are an enterprise process transformation analyst.

PROCESS: {{name}}
DESCRIPTION: {{description}}
CURRENT ACTIVITIES:
{{#each activities}}
- {{sequence_order}}. {{name}} — {{description}}
{{/each}}

RELEVANT RESEARCH CONTEXT:
{{#each snippets}}
- [{{source_type}}] {{title}}: {{snippet_text}} (source: {{source_url}})
{{/each}}

TASK:
Analyze this process and return STRICT JSON ONLY, matching exactly this schema
(no markdown, no commentary, no extra keys):

{
  "problems": [{"activity_name": "", "description": "", "severity": "LOW|MEDIUM|HIGH"}],
  "ai_opportunities": [{
    "activity_name": "",
    "description": "",
    "ai_capability": "",
    "automation_potential": "LOW|MEDIUM|HIGH",
    "business_benefit": "",
    "risk": "",
    "reasoning_note": "",
    "supporting_snippet_titles": []
  }],
  "future_activities": [{
    "sequence_order": 0,
    "name": "",
    "description": "",
    "human_responsibility": "",
    "ai_responsibility": "",
    "responsibility_type": "AI_AUTOMATED|AI_AUGMENTED|HUMAN_LED"
  }],
  "ai_interventions": [{
    "future_activity_name": "",
    "related_ai_opportunity_description": "",
    "intervention_type": "AUTOMATE|AUGMENT|ELIMINATE|NEW",
    "description": ""
  }]
}
```

---

## 6. Backend Build Plan (Spring Boot)

```
com.assesswise.processdesigner
 ├── entity/            (JPA entities from Section 4)
 ├── repository/         (Spring Data JPA repos)
 ├── dto/                (request/response DTOs, incl. AnalysisResultDto)
 ├── service/
 │    ├── ProcessService.java
 │    ├── AnalysisService.java        (orchestrates the pipeline in Section 5)
 │    ├── KnowledgeRetrievalService.java  (keyword match on snippets)
 │    └── ai/
 │         ├── AiProvider.java        (interface, one live implementation)
 │         └── GeminiProvider.java
 ├── controller/
 │    ├── ProcessController.java      (CRUD)
 │    ├── AnalysisController.java     (trigger + fetch analysis)
 │    └── EvidenceController.java     (list knowledge snippets)
 └── config/             (CORS, AI client beans, env-based API keys)
```

### REST API surface

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/processes` | List all processes |
| POST | `/api/processes` | Create a new process + activities (this is what the judge uses for the Surprise Record test) |
| GET | `/api/processes/{id}` | Full detail (current + future + evidence) |
| POST | `/api/processes/{id}/analyze` | Run the AI pipeline (re-runnable/idempotent — clears and regenerates future-state rows) |
| GET | `/api/processes/{id}/comparison` | Combined CURRENT / TRANSITION / FUTURE view for the UI |
| GET | `/api/knowledge-snippets` | List curated research sources (Evidence tab) |
| GET | `/api/roles`, `/api/systems` | Reference lookups |

---

## 7. Frontend Build Plan (Next.js + TypeScript)

Pages:
1. **`/` — Dashboard**: table of all processes (name, status, activity count), "+ New Process" button.
2. **`/processes/new`**: form — process name, description, dynamic activity list builder (add/remove rows with name + description). On submit → `POST /api/processes`, redirect to detail page.
3. **`/processes/[id]`**: tabbed detail view:
   - **Current** tab: activities table with linked roles/systems/problems.
   - **Transition** tab: AI Opportunities list (capability, automation potential badge, benefit, risk, reasoning, linked evidence chips).
   - **Future** tab: future activities table with human vs AI responsibility columns.
   - **Evidence** tab: the knowledge snippets that grounded this process's analysis, with source type + link.
   - A prominent **"Analyze"** button (disabled/loading state while the pipeline runs) that calls `POST /analyze` and refreshes all tabs — this button, run live on a new process, IS the demo.
4. A simple **CURRENT → TRANSITION → FUTURE** three-column comparison strip at the top of the detail page (cards, not paragraphs) satisfying the "visually compare" requirement.

Keep styling clean and simple (Tailwind, no heavy component libs needed) — this is a judged engineering artifact, not a design showcase.

---

## 8. Seed / Sample Data

Create a `data-seed.sql` (or a Spring `CommandLineRunner` behind a `seed.enabled` profile flag) that inserts:
- The 6 seed processes with 4–6 realistic activities each (Section 1).
- ~12 curated `KnowledgeSnippet` rows with real, findable public source URLs (e.g., reputable ed-tech/AI-in-education reports, vendor whitepapers, research articles) — each tagged with the correct `source_type`. Do **not** invent fake URLs; find real ones and paraphrase short snippets in your own words (do not paste large excerpts).
- Do **not** pre-populate `AIOpportunity` / `FutureActivity` rows for the seed processes — leave them `status = CURRENT_ONLY` so the demo can show the "Analyze" pipeline running live on them too, proving it's not hard-coded.

---

## 9. The Surprise Record Test — explicit design checklist

Before calling this "done," verify:
- [ ] Creating a brand-new process (any industry, any activities) through the UI, with zero code changes, produces a full structured analysis.
- [ ] The analysis is generated by the same `AnalysisService` code path as the seed data — no `if (processName.equals("Online Assessment Creation"))` type branching anywhere.
- [ ] Re-running `/analyze` on an already-analyzed process regenerates future-state rows cleanly (no duplicate/orphaned rows).
- [ ] If the Gemini call fails or returns malformed JSON, the app retries once with a repair prompt and fails gracefully (clear error to the UI) rather than crashing — no second provider needed for this.
- [ ] Every AI Opportunity and Future Activity can be traced back to a specific process/activity/evidence row in the database (not just visible in a chat response).

---

## 10. Build Schedule (2 focused days, ~8 phases)

**Day 1 — Foundation + Backend**
1. Repo scaffold: Spring Boot project, Next.js project, Neon DB provisioned, `.env` files, CORS configured, hello-world round trip working end to end.
2. JPA entities + repositories + Flyway/SQL migration for the schema in Section 4.
3. `ProcessController` CRUD + seed data loader; verify via Postman/curl.
4. `AiProvider` interface + `GeminiProvider` implementation; hard-code one test call to confirm the API key and JSON-mode prompting work.
5. `AnalysisService` full pipeline (retrieve snippets → prompt → call AI → validate JSON → persist). Test against 2 seed processes.

**Day 2 — Frontend + Polish + Deploy**
6. JSON-repair retry logic on the single provider; Evidence retrieval service; comparison endpoint.
7. Next.js pages: dashboard, new-process form, detail page with 4 tabs + comparison strip.
8. End-to-end test: run Analyze on all 6 seed processes + one deliberately new "surprise" process. Fix any JSON-parsing edge cases.
9. Deploy: Neon (already live) → Render (backend) → Vercel (frontend). Smoke-test the deployed URLs, not just localhost.
10. Write README, architecture diagram, data model doc, library inventory, demo script (Sections 11–13 below).

---

## 11. Testing Checklist

- [ ] Fresh DB + seed script → app boots clean, no manual steps.
- [ ] All 6 seed processes visible on dashboard.
- [ ] Analyze works on each seed process; JSON always parses (test with a malformed-response simulation to confirm the repair-retry works).
- [ ] Create + analyze a genuinely new process live (rehearse this exact flow before the demo).
- [ ] Restart the backend (or redeploy) → data still there (persistence requirement).
- [ ] Evidence tab shows real linked sources, not placeholder text.
- [ ] Comparison view renders correctly for a process with 0 problems (edge case) and one with 6+ opportunities.

---

## 12. Deliverables Checklist (must all exist before submission)

- [ ] `README.md` — what it is, architecture summary, how to run locally, how to deploy, environment variables needed.
- [ ] `docs/architecture-diagram.md` (Mermaid diagram matching Section 2).
- [ ] `docs/data-model.md` (Mermaid ER diagram matching Section 4).
- [ ] `LIBRARIES.md` — every library/framework/model used, version, license (all must be OSS/free-tier — confirm none are paid-only).
- [ ] `data-seed.sql` / seed runner — the sample/synthetic data.
- [ ] `docs/sources.md` — the curated knowledge snippets and their real source URLs.
- [ ] Publicly reachable working app: Vercel frontend URL + Render backend URL (share both).
- [ ] Source code pushed to a public (or invite-only) GitHub repo.
- [ ] `docs/ai-tools-disclosure.md` — plainly state Claude Code was used, and briefly describe what you personally reviewed/decided vs what was scaffolded (the brief explicitly requires this).

---

## 13. Live Demo Script (10–15 min)

1. **(1 min)** One-liner on the problem: "This analyzes how AI could transform business processes at AssessWise, an online-assessment company — and it's built so it works on any process, not just pre-loaded ones."
2. **(2 min)** Show architecture diagram, name each real layer (UI/API/AI/DB), and the free-tier stack + the "what if it becomes paid" answer from Section 3 (a one-line explanation, not extra running code).
3. **(3 min)** Walk one seed process end-to-end: Current tab → Transition (AI Opportunities with reasoning + evidence) → Future tab → Comparison strip.
4. **(1 min)** Open the Evidence tab, click a source URL, show it's real and traceable.
5. **(4–5 min) — the critical part:** Ask the judge (or simulate it yourself) for a brand-new process from any industry they name. Create it live, click Analyze, walk through the generated result while it's still warm — this is the "Process 101" / Surprise Record proof.
6. **(1 min)** Show the Postgres tables directly (or an admin query) proving the future process is rows, not a paragraph.
7. **(1 min)** Close with what Phase 2 would add (multi-tenant orgs, more industries, semantic retrieval instead of keyword-match, versioned prompts) — ties back to the scalability answers already given in the questionnaire (Q64–72), without over-promising anything not built.

---

## 14. Explicit Anti-Patterns (do not do any of these — direct from the "What Is NOT Accepted" section)

- No giant single prompt that does everything with no backend logic around it.
- No hard-coded per-process responses.
- No paragraph-only "future process" — must be structured rows.
- No chatbot-only UI with no real domain model behind it.
- No screenshots/mockups substituting for working functionality — everything demoed must be live and real.
- No claiming research/evidence that isn't actually stored with a real, checkable source.

---

## 15. Summary for Claude Code

Build, in order: DB schema → Spring Boot CRUD → AI pipeline with a single Gemini provider behind a swappable interface → seed data → Next.js UI (dashboard, new-process form, 4-tab detail view) → deploy to Neon/Render/Vercel → docs/diagrams/README (including the one-paragraph "what if the free tier goes away" explanation) → rehearse the Surprise Record flow. Keep every process-specific behavior generic and data-driven — nothing about "Online Assessment Creation" or any other seed process name should ever appear in application logic, only in seed data. This is the single most important constraint in the whole build. Do not build a second live AI provider or automatic runtime failover — the brief only requires explaining that risk, not engineering around it, and that time is better spent on the pipeline, UI, and demo rehearsal.
