# Technical documentation

AI Future Process Designer — a service that takes a business process as it runs today and produces
an AI-enabled future version of it, stored as structured rows rather than prose.

This document is the technical reference for the system: what each module does, what every endpoint
accepts and returns, how the analysis pipeline behaves under failure, and every configuration knob.
It is written to be read on its own. Companion documents:

| Document | Covers |
|---|---|
| [architecture-diagram.md](architecture-diagram.md) | Layer diagram, pipeline sequence diagram, deployment topology |
| [data-model.md](data-model.md) | ER diagram, every table and column, the join path behind the chain |
| [../README.md](../README.md) | Local setup, quick start, submission index |
| [../DEPLOYMENT.md](../DEPLOYMENT.md) | Hosted deployment to Neon, Render and Vercel |
| [sources.md](sources.md) | The 16 curated research sources and their limitations |
| [../LIBRARIES.md](../LIBRARIES.md) | Every dependency, its licence, and why it is there |

---

## 1. System overview

The system converts an unstructured description of how work happens today into a typed, queryable
future-state design, grounded in a curated research corpus and fully auditable after the fact.

```
Current activities → Problems → AI opportunities → Future activities → Human vs AI → Benefit
     (rows)          (rows)     (rows + evidence)      (rows)          (columns)    (queryable)
```

The domain used for the demo data is online assessment, but nothing in the pipeline is specific to
it — and neither is the research layer, which is why it has to go and look. A process from any
industry, created through the API seconds before, takes an identical path through the same ten
stages.

**Four processes, four responsibilities:**

| Layer | Runtime | Responsibility |
|---|---|---|
| UI | Next.js 16 on Vercel | Capture the current process; render current / transition / future side by side |
| API | Spring Boot 3.5 on Render | Authentication, ownership, CRUD, orchestration of the analysis |
| AI pipeline | In-process, inside the API | Retrieval, prompting, provider failover, parsing, validation, persistence |
| Data | PostgreSQL 16 on Neon | Process state, the knowledge corpus, and the full audit trail of every run |

---

## 2. Technology stack

### Backend

| Component | Version | Notes |
|---|---|---|
| Java | 21 | Virtual threads enabled — analysis requests are long and I/O bound |
| Spring Boot | 3.5.16 | Web, Data JPA, Validation, Security, OAuth2 Resource Server, Actuator |
| PostgreSQL driver | bundled | Target is PostgreSQL 16 |
| Flyway | bundled | Owns the schema; Hibernate is `ddl-auto: validate` only |
| springdoc-openapi | — | Serves `/swagger-ui.html` and the OpenAPI document |
| Lombok | — | Boilerplate reduction on entities |

### Frontend

| Component | Version |
|---|---|
| Next.js | 16.3.1 (App Router) |
| React / React DOM | 19.2.8 |
| TypeScript | 5.9.3 |
| Tailwind CSS | 4.3.3 |
| Node | ≥ 20.9.0 |

No component library, no charting library, no state-management library. The comparison strip, the
tab content and the visualisations in [`components/viz.tsx`](../frontend/src/components/viz.tsx)
are hand-built, which keeps the dependency surface — and the licence surface — small.

### External services

| Service | Role | Tier |
|---|---|---|
| Google AI Studio (Gemini) | Primary model provider | Free |
| Groq Cloud (Llama 3.3 70B) | Fallback model provider | Free |
| Neon | Serverless PostgreSQL | Free |
| Render | Backend container host | Free (sleeps when idle) |
| Vercel | Frontend host | Free hobby |

---

## 3. Module map

### Backend — `com.assesswise.processdesigner`

| Package | Contents | Notes |
|---|---|---|
| `domain/` | JPA entities and enums | `BusinessProcess`, `Activity`, `Problem`, `AiOpportunity`, `FutureActivity`, `AiIntervention`, `KnowledgeSnippet`, `AnalysisRun`, `AppUser` |
| `repository/` | Spring Data repositories | One per aggregate |
| `dto/` | Request/response records | `dto/ai/AiAnalysisPayload` is the model's contract, kept separate from the API's own DTOs |
| `service/` | Orchestration and persistence | Retrieval, validation, entity resolution, access control, the read side for insights |
| `service/ai/` | The model layer | `AiGateway`, `ModelRouter`, `TokenBudgetGovernor`, `AiResponseCache`, `StructuredJson`, and the providers behind one `AiProvider` interface |
| `service/research/` | The research layer | Query planning, the HTTP client, page fetching, content extraction, claim extraction, `QuoteVerifier`, credibility scoring, corroboration, persistence |
| `service/research/connector/` | Eleven search connectors | One class each, all implementing `SearchConnector` and all allowed to return nothing |
| `service/pipeline/` | The ten stages | `PipelineStage` implementations, `StagedAnalysisPipeline`, `ImpactCalculator`, `ScorecardCalculator`, `StageRecorder` |
| `service/progress/` | Live progress | `ProgressEvent`, `ProgressSink` (no-op by default), `SseProgressSink` |
| `controller/` | REST endpoints | Plus `GlobalExceptionHandler`, which produces every error response |
| `config/` | Typed configuration | `AppProperties`, CORS, OpenAPI, provider chain wiring, `DatabaseUrlCheck` |
| `security/` | Authentication | JWT issuing and decoding, the filter chain, current-user resolution |

The classes worth knowing by name:

| Class | What it owns |
|---|---|
| `AnalysisService` | Chooses the pipeline, guards concurrency and rate limits, and records the run |
| `StagedAnalysisPipeline` | Runs the ten stages in order and decides what a failure costs |
| `ModelStage` | The shared machinery of a stage that asks a model: render, call, parse, one repair retry |
| `AiGateway` | The single door to any model: route → cache → budget → call, with every substitution recorded |
| `ModelRouter` | Which model does which job, and in what order to try alternatives |
| `TokenBudgetGovernor` | Per-model and organisation-wide token buckets, synchronised from response headers |
| `AiResponseCache` | Prompt-keyed responses in Postgres, so a restart does not discard spent quota |
| `ResearchOrchestrator` | Plan, search, fetch, read, quote, cross-check — and contain every failure |
| **`QuoteVerifier`** | **Whether a quote is really in its source. The check everything else rests on** |
| `SourceCredibilityScorer` | A 0-100 score with its arithmetic stored beside it |
| `CorroborationAnalyzer` | Agreement across independent domains; contradictions recorded, never resolved |
| `ImpactCalculator` | Hours, rupees and payback, from the model's four inputs |
| `ScorecardCalculator` | Six quality components, each a ratio over stored rows |
| `AnalysisPersistenceService` | Foreign-key resolution, citation resolution, grounding scores, idempotent replace |
| `AnalysisInsightService` | The read side: reviews, impacts, risks, roadmap, scorecard, research |
| `ProcessAccessService` | The single place ownership is decided, for every route touching one process |

### Frontend — `frontend/src`

| Path | Contents |
|---|---|
| `app/page.tsx` | Dashboard — the process list |
| `app/processes/new/page.tsx` | Create a process and its current activities |
| `app/processes/[id]/page.tsx` | The workspace: ten tabs from Overview to Run trace |
| `app/evidence/page.tsx` | The curated fallback corpus, browsable |
| `app/system/page.tsx` | Engine: model routing, live free-tier budgets, connector health |
| `app/how-it-works/page.tsx` | The pipeline stage by stage, and what it does and does not guarantee |
| `app/login/page.tsx` | Sign in and register |
| `lib/api.ts` | The typed API client — the only place `fetch` is called, including the SSE reader |
| `lib/auth-context.tsx` | Token storage and the current account |
| `lib/use-api-resource.ts` | Loading / error / cold-start states for every fetch |
| `components/app-shell.tsx` | Sidebar, theme toggle, command palette |
| `components/charts.tsx` | Every chart, as inline SVG against validated colour tokens |
| `components/evidence.tsx` | Citation chips, the evidence drawer, verification badges |
| `components/process-views.tsx` | The ten tab panels |
| `components/analysis-console.tsx` | The live run: stage timeline and event feed |
| `components/ui.tsx` | Buttons, panels, badges, tabs, drawer, toasts, form fields |

---

## 4. The analysis pipeline

`POST /api/processes/{id}/analyze` (or `/analyze/stream`, the same run reporting itself) is the only
entry point. Ten stages, identical for seed data and for a process created seconds ago.

Before the first stage: a rate-limit check (`429` if over the per-minute budget), a concurrency guard
(`409` if a run is already in flight for this process), a visibility check (`404`), and an
`analysis_run` row written as `RUNNING` — before any model call, so a crash leaves evidence.

| # | Stage | Required | Behaviour on failure |
|---|---|---|---|
| 1 | Intake — counts and gaps, no model | no | Cannot fail; a process with no activities is recorded as degraded |
| 2 | Diagnosis | **yes** | One repair retry, then the run stops with `422` naming the stage |
| 3 | Research | no | Every connector, fetch and extraction is individually contained; worst case is a run with no claims, recorded as degraded |
| 4 | Opportunities | **yes** | As stage 2 |
| 5 | Critique | no | Recommendations are shown without a verdict, and the scorecard's agreement score is zero |
| 6 | Future design | **yes** | As stage 2 |
| 7 | Quantification | no | The Impact tab is empty and says so |
| 8 | Risks | no | The Risks tab is empty and says so |
| 9 | Roadmap | no | The Roadmap tab is empty and says so |
| 10 | Scorecard — six ratios, no model | no | Runs even when earlier stages failed; a bad run should score badly |

Every stage writes an `analysis_stage` row with its prompt, its response, the model that answered,
its token cost and how long it waited for rate-limit budget — before the next stage begins, so a run
that dies at stage six leaves five readable rows.

### The model gateway

Every model call goes through `AiGateway`, which does four things in order:

1. **Route.** `ModelRouter` supplies an ordered candidate list for the task. Diagnosis, opportunities
   and future design get the strongest model; claim extraction gets a fast one; the critique gets a
   *different model family* on purpose; quantification and roadmap start on Gemini to keep Groq's
   constrained budget for the stages that need it.
2. **Cache.** Every candidate is checked against `ai_cache` before any network call. An unchanged
   re-run costs nothing and returns immediately.
3. **Budget.** `TokenBudgetGovernor` either admits the call, makes it wait for the bucket to refill,
   or refuses — in which case the next candidate is tried instead of the run failing. Buckets are
   synchronised from each provider's own `x-ratelimit-*` headers, and there are two per provider:
   the per-model one, and the organisation-wide one that on Groq is what actually binds.
4. **Call**, with one narrow retry: when a provider rejects its own JSON-mode output — which in
   practice means the response was truncated — the same model is asked again without JSON mode,
   because this application's parser can usually repair what the provider discarded.

Every substitution, wait and failure is recorded on the completion and ends up in the trace.

### The research layer

Contained at every level, because eleven third parties fail independently:

| Failure | What happens |
|---|---|
| A connector errors or returns nothing | Recorded as a note; the other ten carry the run |
| A publisher refuses a direct fetch | Retried through a text reader; then kept as a search snippet, labelled |
| `robots.txt` disallows the path | Skipped, and the source kept with its snippet |
| A page is a PDF | Recorded as snippet-only; the abstract from the index is used instead |
| Claim extraction fails on a chunk | Earlier chunks' claims are kept and the loss is noted |
| A quote is not found in the page | The claim is kept and marked unverified — never silently dropped, never silently trusted |
| The agentic connector runs out of budget | Capped at two calls a run anyway; its absence degrades the research rather than ending it |

### Retrieval scoring

TF-IDF-flavoured but deliberately legible, so the selection can be defended:

- A term matched in a snippet's **tags** is worth `3.0`, in its **title** `2.0`, in its **body** `1.0`.
- Terms appearing in nearly every snippet are discounted by inverse document frequency.
- Ties break on title, so the same input always retrieves the same snippets.
- The score and the matched terms are persisted on `analysis_run_snippet` — "why was this source
  used?" has an answer in the database, not just in the logs.
- A process from an unrelated industry can legitimately match nothing. Rather than send an
  ungrounded prompt, the retriever falls back to general AI-adoption material and records a zero
  score, which makes the fallback visible on the run.

`ANALYSIS_SNIPPET_COUNT` (default `4`) controls how many are injected.

### Validation: two levels of strictness

**Warnings — the item is dropped, the run proceeds.** One opportunity with a typo'd enum should not
discard an otherwise good analysis. Every drop is recorded in `analysis_run.validation_warnings` and
shown on the run panel in the UI. The validator also coerces the paraphrases models actually
produce: `MODERATE` → `MEDIUM`, `CRITICAL` → `HIGH`, `AI_ASSISTED` → `AI_AUGMENTED`,
`AUTOMATION` → `AUTOMATE`, and so on.

**Errors — nothing usable came back.** No opportunities at all, or no future activities. These
trigger the single repair retry (`prompts/repair-json.txt`, with the specific errors included), and
if that also fails, an honest `422` naming the reason. A model that ignores an explicit JSON schema
twice will not comply on the third attempt, and a caller is waiting.

### Citations and grounding

A citation is either a row in `ai_opportunity_claim` pointing at a real `evidence_claim`, or it does
not exist. The model cites by the numbers it was shown; a number it was not shown is dropped and
recorded as a fabricated citation.

Grounding is then computed rather than claimed. Only quote-verified claims count; the score combines
the best cited claim's confidence with a bonus for citing more than one independent domain. An
opportunity citing nothing scores zero and is labelled ungrounded in the interface — kept, because
an idea with no supporting literature can still be a good idea, and hiding that it has none would be
the dishonest choice.

### Quote verification

The check the whole evidence model rests on, and deliberately not clever. Both strings are normalised
— case, whitespace, the curly quotes and en-dashes publishers use and models rewrite — and the quote
is looked for in the stored page text. Found means verified. Not found falls back to a token-window
match that must reach 85% of the quote's own words *in sequence*, which tolerates an extraction
artefact but not a fabrication. The unit tests assert both halves: what must verify, and what must
not.

---

## 5. API reference

Base URL — local `http://localhost:8080`, hosted `https://ai-future-process-designer-api.onrender.com`.
Interactive documentation at `/swagger-ui.html`.

All routes under `/api` require `Authorization: Bearer <token>` except `/api/auth/register`,
`/api/auth/login` and `/actuator/health`.

### Authentication

| Method | Path | Body | Returns |
|---|---|---|---|
| `POST` | `/api/auth/register` | `{ email, password, displayName }` | `{ token, user }` |
| `POST` | `/api/auth/login` | `{ email, password }` | `{ token, user }` |
| `GET` | `/api/auth/me` | — | The account the token belongs to |

Tokens are stateless JWTs signed with `AUTH_JWT_SECRET`, valid for `AUTH_TOKEN_TTL_HOURS`
(default 12). Passwords are stored as BCrypt hashes. A bearer token rather than a session cookie is
deliberate: the frontend and API are on different sites, so a cookie would have to be
`SameSite=None` third-party, which browsers increasingly block.

### Processes

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/processes` | List — paginated and filterable via `page`, `size`, `status`, `q`, `sort` |
| `POST` | `/api/processes` | Create a process and its current activities |
| `GET` | `/api/processes/{id}` | Full detail: current, transition, future, evidence |
| `PUT` | `/api/processes/{id}` | Replace the definition; clears any stale future state |
| `DELETE` | `/api/processes/{id}` | Delete the process and everything derived from it |
| `GET` | `/api/processes/{id}/comparison` | Current / transition / future with roll-up counters |

`POST /api/processes` request body:

```json
{
  "name": "Result Evaluation & Grading",
  "industry": "Online Assessment",
  "description": "How examiner marking works today, end to end.",
  "activities": [
    {
      "name": "Collect submitted answer scripts",
      "description": "Scripts are exported from the platform and shared with examiners.",
      "roles": ["Exam Coordinator"],
      "systems": ["LMS", "Email"]
    }
  ]
}
```

Validation limits, enforced with per-field messages: `name` ≤ 200 characters, `industry` ≤ 120,
`description` ≤ 4000, 1–30 activities, activity `name` ≤ 200, activity `description` ≤ 2000, and at
most 10 roles and 10 systems per activity. `roles` and `systems` are optional; they are matched
against existing reference rows by name and created if new.

### Analysis

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/processes/{id}/analyze` | Run the pipeline. Re-runnable and idempotent |
| `POST` | `/api/processes/{id}/analyze/stream` | The same run, streaming its progress as Server-Sent Events |
| `GET` | `/api/processes/{id}/research` | The research behind the stored analysis: queries, sources with their credibility arithmetic, and every claim with its checked quote |
| `GET` | `/api/processes/{id}/analysis-runs` | Run history with provider, model, tokens, cache hits and duration |
| `GET` | `/api/processes/{id}/analysis-runs/latest/trace` | Every stage, with the exact prompt sent and the exact text returned |
| `GET` | `/api/processes/{id}/analysis-runs/{runId}/stages` | The same, for a specific run |

The trace endpoint is what makes "the output is generated, not hard-coded" checkable rather than
asserted.

**The streaming endpoint** is a `POST` despite being a stream, for two reasons: it is not idempotent,
and the browser must send its session token in a header. `EventSource` cannot do that, so the client
reads the stream with `fetch` and parses the SSE framing itself — the alternative, a token in the
query string, writes it into every access log in between.

Event names: `progress` for each step (stage started, query planned, connector answered, source
fetched, claims extracted, stage finished), then exactly one of `result` — carrying the same payload
as `POST /analyze` — or `failed`. The run continues on the server if the client disconnects; the work
has already been paid for out of a free-tier quota, and somebody closing a tab is not a reason to
throw an analysis away.

```
event:progress
data:{"type":"SOURCE_FETCHED","stageId":"research","title":"europepmc.org",
      "message":"Prospective Deployment of Multimodal AI Grading — read 18,204 characters",
      "at":"2026-08-27T17:33:15Z","data":{"domain":"europepmc.org","status":"FETCHED","chars":18204}}
```

### System

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/system/ai-status` | Providers and whether each has a key, per-task model routing, remaining free-tier budget per model, and which research connectors are live |
| `GET` | `/api/system/budgets` | Just the token buckets — cheap enough to poll while a run is in flight |

No keys are exposed, only whether one is present.

### Reference data

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/knowledge-snippets` | The curated fallback corpus (16 cited excerpts) |
| `GET` | `/api/roles` | Role lookup |
| `GET` | `/api/systems` | System/tool lookup |
| `GET` | `/actuator/health` | Liveness and readiness, including the database |

### Errors

Every error is an RFC 7807 problem document produced by `GlobalExceptionHandler` — the default
Spring error page is disabled (`server.error.include-message: never`).

| Status | Meaning |
|---|---|
| `400` | Validation failed — includes per-field detail |
| `401` | Missing, expired or invalid token |
| `404` | Not found, **or** the process belongs to someone else |
| `409` | An analysis is already running for this process |
| `422` | The model output was unusable even after the repair retry |
| `429` | Rate limited |
| `502` | Every configured provider failed |
| `503` | No provider API key is configured |

A process owned by another account returns `404`, not `403`, because a `403` would confirm the id
exists.

---

## 6. Security and access control

| Concern | Approach |
|---|---|
| Passwords | BCrypt, never stored or logged in plaintext |
| Sessions | Stateless JWT, HMAC-signed, `AUTH_TOKEN_TTL_HOURS` expiry |
| Transport | HTTPS everywhere in the hosted deployment; TLS on the JDBC connection |
| CORS | Explicit allow-list via `APP_CORS_ALLOWED_ORIGINS`; no wildcard |
| Authorisation | One service — `ProcessAccessService` — for every single-process route |
| Injection | JPA parameter binding throughout; no string-concatenated SQL |
| Untrusted model output | Never reaches the database; the validator emits a normalised structure and persistence writes only that |
| Secrets | Environment variables only; `DatabaseUrlCheck` fails startup if credentials are embedded in `DATABASE_URL` |
| Error leakage | Problem details carry actionable messages, never stack traces |

### Visibility rules

| Data | Scope |
|---|---|
| Research corpus, roles, systems | Shared reference data — the same sources ground everyone's analysis |
| The 6 sample processes | Shared, read-only — anyone may read and analyse; nobody may edit or delete |
| Processes you create | Private to your account — invisible to others in listing, search and by id |
| Opportunities, future steps, runs | Follow their process; no separate ownership |

"Shared" is encoded as `process.owner_id IS NULL` rather than a separate boolean, so the two facts
can never disagree, and the visibility rule stays a single clause in the listing query.

`AUTH_DEMO_ACCOUNT_ENABLED` creates a known account on first start so the app can be opened without
registering. **Turn it off anywhere that is not a demo.**

---

## 7. Configuration reference

Every knob is an environment variable; nothing requires a code change. Defaults live in
[`application.yml`](../backend/src/main/resources/application.yml) and
[`.env.example`](../backend/.env.example).

### Database

| Variable | Default | Purpose |
|---|---|---|
| `DATABASE_URL` | local Postgres | JDBC URL. Startup fails if credentials are embedded in it |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | `postgres` | Credentials |
| `DATABASE_POOL_SIZE` | `5` | Keep small — Neon's free tier allows few connections |

### Authentication

| Variable | Default | Purpose |
|---|---|---|
| `AUTH_JWT_SECRET` | dev placeholder | **Set in production.** Minimum 32 characters — `openssl rand -base64 48` |
| `AUTH_TOKEN_TTL_HOURS` | `12` | How long a sign-in lasts |
| `AUTH_DEMO_ACCOUNT_ENABLED` | `true` | Creates the demo account on first start. **Turn off in production** |
| `AUTH_DEMO_EMAIL` / `AUTH_DEMO_PASSWORD` / `AUTH_DEMO_DISPLAY_NAME` | demo values | The demo account's details |

### AI providers

| Variable | Default | Purpose |
|---|---|---|
| `AI_PROVIDER` | `groq` | The provider tried first |
| `AI_FALLBACK_PROVIDERS` | `gemini` | Tried in order when the primary fails; empty disables failover |
| `GROQ_API_KEY` | *(empty)* | Primary provider. With no key at all, `/analyze` returns a `503` naming the variable |
| `GROQ_MODEL` | `openai/gpt-oss-120b` | The fallback when a per-task route names a model that has gone away — see §9 |
| `GROQ_RESEARCH_MODEL` | `groq/compound` | The agentic model used as a research connector |
| `GROQ_MAX_OUTPUT_TOKENS` | `4096` | Groq reserves the requested maximum against its per-minute budget, so this is spent whether or not it is used |
| `GEMINI_API_KEY` | *(empty)* | Fallback, and the second quota the high-volume stages alternate into |
| `GEMINI_MODEL` | `gemini-3.1-flash-lite` | Any model the key can reach |
| `GEMINI_THINKING_BUDGET` | `0` | `0` disables thinking and saves free-tier tokens; `-1` restores the model default |
| `GEMINI_STRUCTURED_OUTPUT` | `true` | Whether a caller-supplied response schema is sent. Validation and repair run either way |
| `AI_ROUTE_<TASK>` | *(built-in defaults)* | Per-task routing: a comma-separated list of `provider:model`, best first. One per stage — `AI_ROUTE_DIAGNOSIS`, `AI_ROUTE_CRITIQUE`, `AI_ROUTE_CLAIM_EXTRACTION`, and so on |
| `AI_CACHE_ENABLED` | `true` | Remembered model responses in Postgres |
| `AI_CACHE_TTL_HOURS` | `72` | How long a remembered response stays usable |
| `AI_MAX_RATE_LIMIT_WAIT_SECONDS` | `60` | How long a stage waits for token budget before trying another model |

### Research

| Variable | Default | Purpose |
|---|---|---|
| `RESEARCH_ENABLED` | `true` | Live research. Off falls back to the curated corpus alone |
| `RESEARCH_MAX_QUERIES` | `5` | Searches the planner may produce |
| `RESEARCH_HITS_PER_QUERY` | `5` | Results kept per connector per query |
| `RESEARCH_MAX_DOCUMENTS` | `6` | **The main lever on run time.** Each document read costs a model call against a shared per-minute ceiling |
| `RESEARCH_MAX_CLAIMS` | `24` | Ceiling on claims per run |
| `RESEARCH_FETCH_TIMEOUT_SECONDS` | `12` | Per-request timeout |
| `RESEARCH_FETCH_CONCURRENCY` | `6` | Parallel page fetches |
| `RESEARCH_DOCUMENT_CACHE_TTL_HOURS` | `168` | Fetched pages are reused for a week |
| `RESEARCH_RESPECT_ROBOTS` | `true` | Honour `robots.txt`. A disallowed path is skipped and the source kept |
| `RESEARCH_USER_AGENT` | `AssessWiseResearchBot/2.0 …` | Identifies this client to publishers |
| `RESEARCH_READER_FALLBACK` | `true` | Retry blocked publishers through a public text reader |
| `RESEARCH_TAVILY_API_KEY` / `RESEARCH_BRAVE_API_KEY` | *(empty)* | Optional keyed search; dormant without a key |

### Analysis behaviour

| Variable | Default | Purpose |
|---|---|---|
| `ANALYSIS_PIPELINE` | `staged` | `staged` = ten stages with live research; `single` = the original one-prompt analysis |
| `ANALYSIS_SNIPPET_COUNT` | `4` | Curated snippets used when live research found nothing |
| `ANALYSIS_RATE_LIMIT_ENABLED` | `true` | Whether the per-minute limit applies |
| `ANALYSIS_RATE_LIMIT_PER_MINUTE` | `20` | Protects the free AI quota from a double-clicked button |

### Platform

| Variable | Default | Purpose |
|---|---|---|
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Comma-separated frontend origins |
| `PORT` | `8080` | Server port; Render sets this |
| `LOG_LEVEL` | `INFO` | Application log level |
| `NEXT_PUBLIC_API_BASE_URL` | `http://localhost:8080` | Frontend → backend. **Baked in at build time** — changing it needs a redeploy, not a restart |

---

## 8. Build, test and run

### Local

```bash
docker compose up -d          # PostgreSQL 16
cd backend && ./run-local.sh  # or: ./mvnw spring-boot:run
cd frontend && npm install && npm run dev
```

Flyway applies `V1`–`V5` and seeds the samples on first boot — there is no manual load step. Full
prerequisites and troubleshooting are in [../README.md](../README.md#run-it-locally).

### Tests

```bash
cd backend && ./mvnw test
```

165 tests. The integration tests run against a real embedded PostgreSQL rather than an in-memory
substitute, so Flyway migrations, JPA mappings and the `CHECK` constraints are all exercised as they
will behave in production. The AI provider is stubbed (`support/StubAiProvider`) and live research is
switched off, so the suite needs no API key and no network.

One test is the exception and is skipped unless asked for:

```bash
RESEARCH_LIVE_TESTS=true GROQ_API_KEY=... ./mvnw test -Dtest=LiveResearchSmokeTest
```

It talks to the real internet, because the research layer's dependencies are eleven third parties
with no contract to this project — a mocked connector test only proves the mock still matches what
the connector was written against, which is precisely the thing that goes stale. It asserts that
some connectors still answer, that a page can still be read, and that a quote taken from real page
text still verifies while an invented one does not. Worth running before a demo.

| Test | What it protects |
|---|---|
| `StagedPipelineIntegrationTest` | The full ten-stage path against a process from an unrelated industry: stage records, citation resolution, computed impact, the scorecard, and that a degraded stage does not end the run |
| `QuoteVerifierTest` | The trust foundation — including that a fabricated quote which *sounds* like the source is refused |
| `TokenBudgetGovernorTest` | The shared ceiling, including that a second model does not get a second per-minute allowance |
| `CorroborationAnalyzerTest` | Agreement counted only across independent domains; contradictions recorded, not resolved |
| `GeminiProviderTest` | That only a caller-supplied response schema is ever sent — the regression that made eight stages answer the wrong schema |
| `GroqProviderTest` | Request shape, `executed_tools` parsing, and JSON-mode rejection detection |
| `LiveResearchSmokeTest` | The connectors against the real internet. Skipped unless `RESEARCH_LIVE_TESTS=true` |
| `AnalysisPipelineIntegrationTest` | The legacy single-call path, including the repair retry |
| `KnowledgeRetrievalIntegrationTest` | Curated-corpus scoring, ordering and the zero-match fallback |
| `ProcessApiIntegrationTest` | CRUD, pagination, filtering, and cascade on delete |
| `AuthIntegrationTest` | Registration, login, token rejection, cross-account isolation |
| `AnalysisPayloadValidatorTest` | Enum coercion, per-item drops, warning vs. error |
| `AnalysisResponseParserTest` | Fence stripping and brace matching on messy responses |
| `FallbackAiProviderTest` | Failover order and the transport retry |
| `AnalysisJsonSchemaTest` | The generated schema, the Java enums and the DB constraints agree |
| `DatabaseUrlCheckTest` | Startup fails on a URL with credentials still in it |

[`.github/workflows/ci.yml`](../.github/workflows/ci.yml) runs the suite against PostgreSQL on every
push.

### Deploy

Neon → Render → Vercel, then back to Render for the CORS origin. Exact variables per platform are in
[../DEPLOYMENT.md](../DEPLOYMENT.md). Two things that catch people out: Vercel's **Root Directory
must be `frontend`**, and `NEXT_PUBLIC_API_BASE_URL` is baked in at build time.

---

## 9. Operations and troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Frontend loads, every call fails | Render instance is asleep | Wait ~60 s; the UI shows a "waking up" state |
| Every call blocked by the browser | `APP_CORS_ALLOWED_ORIGINS` does not include the Vercel URL | Set it on Render and restart |
| Calls go to `localhost` in production | `NEXT_PUBLIC_API_BASE_URL` was changed without a rebuild | Redeploy the frontend |
| `503` from `/analyze` | No provider API key configured | Set `GROQ_API_KEY` and/or `GEMINI_API_KEY` |
| `502` mentioning the model was not found | The configured model has been decommissioned | Set one the key can call — config only. Check what is available: `curl -s https://api.groq.com/openai/v1/models -H "Authorization: Bearer $GROQ_API_KEY"` |
| `422` after a repair retry | The model returned nothing usable twice | Open the Run trace tab; the failing stage's response is stored verbatim |
| A run takes five minutes | Normal on a fresh process: ~30,000 tokens against a shared 8,000-per-minute ceiling | Lower `RESEARCH_MAX_DOCUMENTS`, or set the second provider's key so the high-volume stages can alternate. Check **Engine** for the live budget |
| A run is fast the second time | The response cache is working | Nothing to fix |
| Grounding score is zero across the board | Live research found nothing citable — usually every connector blocked, or `RESEARCH_ENABLED=false` | Open the Evidence tab: the run's notes say which connectors failed |
| A stage says DEGRADED | It produced usable output but not all of it | The stage's notes name what was lost; the run is still valid |
| `429` | Analysis rate limit | Raise `ANALYSIS_RATE_LIMIT_PER_MINUTE` or wait |
| Startup fails on the database URL | Credentials embedded in `DATABASE_URL` | Move them into `DATABASE_USERNAME` / `DATABASE_PASSWORD` |

### A warning about model ids and free-tier quotas

All three of these are configuration problems, not code problems.

**Model availability changes without notice, and a listing endpoint is not a reliable guide to what a
given key may actually call.** This has now bitten twice: `gemini-2.5-flash` is listed by Google and
still refuses a newly-issued key, and `llama-3.3-70b-versatile` — this project's own previous Groq
default — has been decommissioned outright. The router falls back through the provider chain, so a
stale route degrades a run rather than ending it, but the right fix is to pin a model you have
actually called.

**The tokens-per-minute ceiling is shared across models, not per model.** Measured: a call to
`groq/compound-mini` was refused with *"Rate limit reached for model openai/gpt-oss-120b"*. Routing a
stage to a second Groq model buys a second daily request allowance, not extra throughput. The only
real throughput multiplier is a second provider.

**A full analysis costs about 30,000 tokens**, so at 8,000 a minute a fresh run has a floor of roughly
four minutes. The response cache makes a re-run of an unchanged process near-instant, and
`RESEARCH_MAX_DOCUMENTS` is the lever for trading evidence against speed.

To see what a key can reach:

```bash
curl -s https://api.groq.com/openai/v1/models -H "Authorization: Bearer $GROQ_API_KEY" \
  | grep -o '"id":"[^"]*"'

curl -s https://generativelanguage.googleapis.com/v1beta/models \
  -H "x-goog-api-key: $GEMINI_API_KEY" \
  | grep -o '"name": "models/[^"]*"'
```

### Auditing a run

Everything needed to verify an analysis is in the database:

```sql
-- The run, and what it cost.
SELECT pipeline_version, provider, model, stage_count,
       total_prompt_tokens, total_output_tokens, cache_hit_count,
       throttled_ms, duration_ms, validation_warnings, provider_notes
FROM   analysis_run
WHERE  process_id = ?
ORDER  BY started_at DESC;

-- Every stage of the latest run: which model, what it cost, what it produced.
SELECT stage_id, status, model, output_tokens, waited_ms, cached, summary
FROM   analysis_stage
WHERE  analysis_run_id = ?
ORDER  BY display_order;

-- The evidence, and whether it can be trusted.
SELECT c.citation_index, c.quote_verified, c.corroboration_count,
       s.domain, s.source_type, s.credibility_score, left(c.quote, 80)
FROM   evidence_claim c
JOIN   research_source s ON s.id = c.research_source_id
WHERE  c.research_run_id = ?
ORDER  BY c.citation_index;
```

Every stage row holds the exact prompt sent and the exact text returned. `provider` and `model`
record who actually answered — which, with a fallback chain and a per-task router, is often not who
was asked first. `research_source.credibility_breakdown` holds the arithmetic behind each score, so a
low score can be argued with rather than merely accepted. `evidence_claim.quote_offset` says where in
the stored page text the quote was found; `web_document.content_hash` says the text has not changed
since. The **Run trace** and **Evidence** tabs in the UI are views over exactly these rows — nothing
is shown there that cannot be reproduced by the SQL above.

---

## 10. Design decisions and their reasons

| Decision | Reason |
|---|---|
| `AiProvider` interface with a chain behind it | The model call is the piece most likely to change — pricing, quotas, retirement. Adding Groq after Gemini's free tier ran out mid-testing was a new class plus a config value, not a rewrite |
| Live research over eleven keyless connectors, with the curated corpus kept as a fallback | Curated excerpts are reproducible but frozen, and a process about a domain nobody anticipated gets nothing relevant. The connectors are keyless so there is no credential to expire mid-demo, there are eleven so no single publisher blocking us ends the run, and every fetched page is stored so the result stays auditable after the fact. When they all fail, the corpus still grounds the analysis |
| Quotes verified by string matching, not taken on the model's word | This is the one place the system refuses to trust itself. A model asked for a verbatim quote will sometimes produce a plausible paraphrase, and a paraphrase presented as a quotation is the most damaging thing this system could emit. The check is `String.indexOf` over the stored page text — the cheapest and least clever component in the codebase, and the one the credibility of everything else rests on |
| Ten stages instead of one prompt | One prompt asked to diagnose, research, propose, design, quantify, assess risk and sequence delivery does all seven adequately and none well, and when it fails there is nothing to inspect. Ten stages each have a prompt small enough to read, a stored response, a model chosen for the task, and a failure that costs one stage instead of the run |
| The critique stage runs on a different model family | A model reviewing its own output agrees with itself. Routing critique to a different family (a Qwen model reviewing gpt-oss) makes the review capable of disagreeing |
| The model supplies inputs to the impact model; Java computes the numbers | Language models do arithmetic unreliably and are persuasive about it anyway. Asking for volume, minutes, share and cost — the things judgement is actually needed for — and multiplying them in Java means every rupee figure can be recomputed from stored inputs |
| Rate-limit waiting made visible rather than hidden | The free-tier ceiling means a fresh run takes minutes. A spinner for four minutes is indistinguishable from a hang; a console that says which query is running and which quote just verified turns the wait into the most convincing part of the demo |
| Retrieval separate from prompting | Retrieval is deterministic and testable alone; the prompt is text in a resource file. Neither needs the other to change |
| Parsing and validation separate from persistence | Nothing untrusted reaches the database |
| Persistence in its own transactional bean | The model call takes tens of seconds and must not hold a database connection open — a real constraint on serverless Postgres |
| One repair retry, not a loop | A model that ignores an explicit schema twice will not comply on the third attempt, and a caller is waiting |
| Individually broken items dropped, not fatal | One typo'd enum should not discard an otherwise good analysis; every drop is recorded as a warning |
| Ownership decided in one service | Read and write rules cannot drift apart between endpoints — the usual way authorisation bugs happen |
| Stateless JWT rather than a session cookie | Frontend and API are on different sites; a third-party cookie would be blocked |
| Client-side data fetching | Server rendering would block on a cold Render instance and time out; fetching in the browser lets the UI say "the backend is waking up" |
| Audit tables as first-class schema | "Not hard-coded" is a claim; the stored prompt and raw response of every stage make it checkable |
| The scorecard is allowed to score badly | A quality measure that always reports success measures nothing. All six components are ratios over stored rows, so a run with weak evidence says so |
| Token budget tracked in the application, not just retried on 429 | Retrying on rejection wastes the request and the wait. Buckets synced from the providers' own rate-limit headers let a stage wait exactly as long as it must, and let the Engine page show what is left |
| Model responses cached in Postgres rather than in memory | Free-tier quota is the scarcest resource here, and a restart on Render's free instance is routine. A cache that does not survive one is not saving anything |
| UUID keys generated in the application | Portable across Neon, Supabase and local Postgres without an extension, and lets a whole object graph be built before touching the database |
| `VARCHAR` + `CHECK` instead of native Postgres enums | Adding a value later is a plain migration rather than a type alteration, and values stay readable in `psql` |

---

## 11. Known limitations and what Phase 2 would add

**A fresh run takes about four minutes.** Roughly 30,000 tokens against a ceiling of 8,000 per
minute; the floor is arithmetic, not inefficiency. Caching, per-task routing and provider rotation
have taken it as far as one free provider allows. A second provider key, or a paid tier, is the only
thing that moves it.

**Corroboration is often zero.** With six documents, two sources on independent domains making the
same claim in comparable words is genuinely uncommon. The matching is lexical (Jaccard overlap with a
numeric-agreement path), so it also misses agreement expressed differently. Embeddings would help and
cost a model call per claim — not affordable inside the same budget as everything else.

**PDFs are not parsed.** A PDF is fetched, recognised, and kept as a source with its search snippet
rather than read. Since much of the best sector research is published as PDF, this is the largest
single gap in evidence quality.

**Research quality depends on publishers who owe us nothing.** `robots.txt` is honoured, so a
disallowed path is skipped. Consulting and vendor sites frequently refuse an unknown client; the text
reader recovers some of them. When a source cannot be read it is kept and labelled, never quietly
dropped — but a run's evidence is thinner than it looks when several publishers refuse at once.

**Analysis is synchronous.** The SSE stream makes the wait legible but the request is still held open
for the duration. Acceptable at this scale; under load this wants a job queue and a run id to poll.

**Accounts are single-user.** No organisations, no password reset, no email verification. Shared
sample processes are analysable by anyone, so one person's re-analysis replaces the future state
everyone else sees — intentional for demo material, wrong for real work.

**Phase 2.** PDF text extraction · embedding-based claim matching so corroboration finds agreement
expressed differently · a job queue with pollable run ids · versioned prompts with side-by-side run
comparison · a review workflow so a human accepts or rejects each opportunity before it becomes part
of the stored future state · export to BPMN · multi-tenant organisations, password reset and email
verification.
