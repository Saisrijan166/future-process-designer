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
it. A process from any industry, created through the API seconds before, takes an identical path
through the same eight steps.

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
| `service/` | The pipeline | Retrieval, prompt building, parsing, validation, persistence, access control |
| `service/ai/` | The provider seam | `AiProvider` interface, `GeminiProvider`, `GroqProvider`, `FallbackAiProvider`, `AnalysisJsonSchema` |
| `controller/` | REST endpoints | Plus `GlobalExceptionHandler`, which produces every error response |
| `config/` | Typed configuration | `AppProperties`, CORS, OpenAPI, provider chain wiring, `DatabaseUrlCheck` |
| `security/` | Authentication | JWT issuing and decoding, the filter chain, current-user resolution |

The classes worth knowing by name:

| Class | What it owns |
|---|---|
| `AnalysisService` | Orchestrates the eight pipeline steps and nothing else |
| `AnalysisInputLoader` | Loads the process, activities, roles, systems and recorded problems in one place |
| `KnowledgeRetrievalService` | Selects the grounding snippets and records why each was selected |
| `PromptBuilder` + `PromptTemplateRenderer` | Renders `resources/prompts/*.txt`; prompts are text, not Java |
| `FallbackAiProvider` | Tries each configured provider in order; the pipeline is unaware failover exists |
| `AnalysisResponseParser` | Fence stripping and brace matching — recovers JSON from a chatty response |
| `AnalysisPayloadValidator` | Enum coercion, caps, per-item drops; decides warning vs. error |
| `AnalysisPersistenceService` | Foreign-key resolution and an idempotent replace, in its own transaction |
| `AnalysisRunRecorder` | Writes the audit trail — prompt, raw response, tokens, provider, warnings |
| `ProcessAccessService` | The single place ownership is decided, for every route touching one process |

### Frontend — `frontend/src`

| Path | Contents |
|---|---|
| `app/page.tsx` | Dashboard — the process list |
| `app/processes/new/page.tsx` | Create a process and its current activities |
| `app/processes/[id]/page.tsx` | The comparison strip: Current · Transition · Future · Evidence |
| `app/evidence/page.tsx` | The curated research corpus, browsable |
| `app/how-it-works/page.tsx` | Plain-language explanation for a first-time reader |
| `app/login/page.tsx` | Sign in and register |
| `lib/api.ts` | The typed API client — the only place `fetch` is called |
| `lib/auth-context.tsx` | Token storage and the current account |
| `lib/use-api-resource.ts` | Loading / error / cold-start states for every fetch |
| `components/` | Comparison strip, tab content, run-trace panel, process flow, UI primitives |

---

## 4. The analysis pipeline

`POST /api/processes/{id}/analyze` is the only entry point. Eight steps, identical for seed data and
for a process created seconds ago.

| # | Step | Owner | Behaviour on failure |
|---|---|---|---|
| 1 | Rate-limit and concurrency check | `AnalysisRateLimiter`, `AnalysisService` | `429` if over the per-minute budget; `409` if a run is already in flight for this process |
| 2 | Load the process graph | `AnalysisInputLoader` | `404` if the process does not exist or is not visible to the caller |
| 3 | Retrieve grounding snippets | `KnowledgeRetrievalService` | Never fatal — a zero-score fallback is recorded rather than hidden |
| 4 | Render the prompt | `PromptBuilder` | Template errors surface at startup, not at request time |
| 5 | Open the audit run | `AnalysisRunRecorder` | Run is written as `RUNNING` *before* the model call, so a crash leaves evidence |
| 6 | Call the model | `FallbackAiProvider` → `GeminiProvider` → `GroqProvider` | Transport retry on `429`/`5xx`; then fall through to the next provider; `502` only if every provider fails, `503` if no key is configured |
| 7 | Parse and validate | `AnalysisResponseParser`, `AnalysisPayloadValidator` | One repair retry with the specific errors fed back; then `422` naming what was wrong |
| 8 | Persist | `AnalysisPersistenceService` | Deletes the previous AI-generated rows and inserts the new ones in one transaction |

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

### Citations

A citation is either a row in `ai_opportunity_evidence` pointing at a real `knowledge_snippet`, or
it does not exist. When the model cites a title that was never supplied to it, the citation is
dropped and the omission is recorded as a warning. The Evidence tab cannot show a source that did
not inform the analysis.

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
| `GET` | `/api/processes/{id}/analysis-runs` | Run history with provider, model, tokens and duration |
| `GET` | `/api/processes/{id}/analysis-runs/latest/trace` | The exact prompt and the raw model response |

The trace endpoint is what makes "the output is generated, not hard-coded" checkable rather than
asserted.

### Reference data

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/knowledge-snippets` | The curated research corpus (16 cited excerpts) |
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
| `AI_PROVIDER` | `gemini` | The provider tried first |
| `AI_FALLBACK_PROVIDERS` | `groq` | Tried in order when the primary fails; empty disables failover |
| `GEMINI_API_KEY` | *(empty)* | Primary provider. With no key at all, `/analyze` returns a clear `503` |
| `GEMINI_MODEL` | `gemini-3.1-flash-lite` | Any model the key can reach — see §9 |
| `GEMINI_TEMPERATURE` | `0.2` | Low, because the task is structured extraction, not prose |
| `GEMINI_MAX_OUTPUT_TOKENS` | `8192` | Ceiling for one response |
| `GEMINI_STRUCTURED_OUTPUT` | `true` | Server-side response schema; validation and repair run either way |
| `GEMINI_THINKING_BUDGET` | `0` | `0` disables thinking and saves free-tier tokens; `-1` restores the model default |
| `GROQ_API_KEY` | *(empty)* | Fallback provider |
| `GROQ_MODEL` | `llama-3.3-70b-versatile` | Completes reliably inside Groq's free per-minute budget |
| `GROQ_TEMPERATURE` | `0.2` | As above |
| `GROQ_MAX_OUTPUT_TOKENS` | `4096` | Lower than Gemini's on purpose — Groq reserves this against its per-minute budget |
| `GROQ_STRUCTURED_OUTPUT` | `true` | As above |

### Analysis behaviour

| Variable | Default | Purpose |
|---|---|---|
| `ANALYSIS_SNIPPET_COUNT` | `4` | Grounding snippets injected per analysis |
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

Flyway applies `V1`–`V4` and seeds the samples on first boot — there is no manual load step. Full
prerequisites and troubleshooting are in [../README.md](../README.md#run-it-locally).

### Tests

```bash
cd backend && ./mvnw test
```

129 tests. The integration tests run against a real embedded PostgreSQL rather than an in-memory
substitute, so Flyway migrations, JPA mappings and the `CHECK` constraints are all exercised as they
will behave in production. The AI provider is stubbed in tests
(`support/StubAiProvider`), so the suite needs no API key and no network.

| Test | What it protects |
|---|---|
| `AnalysisPipelineIntegrationTest` | The full eight-step path, including the repair retry |
| `KnowledgeRetrievalIntegrationTest` | Scoring, ordering and the zero-match fallback |
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
| `503` from `/analyze` | No provider API key configured | Set `GEMINI_API_KEY` and/or `GROQ_API_KEY` |
| `502` mentioning the model was not found | `GEMINI_MODEL` is not reachable by that key | Set a model the key can call — config only, no redeploy of the image |
| `422` after a repair retry | The model returned nothing usable twice | Check the trace endpoint; usually a model too small for the task |
| `429` | Analysis rate limit | Raise `ANALYSIS_RATE_LIMIT_PER_MINUTE` or wait |
| Startup fails on the database URL | Credentials embedded in `DATABASE_URL` | Move them into `DATABASE_USERNAME` / `DATABASE_PASSWORD` |

### A warning about model ids and free-tier quotas

Both of these are configuration problems, not code problems.

**Model availability changes without notice, and the `models` listing endpoint is not a reliable
guide to what a given key may actually call** — a model can be listed and still refuse a
newly-issued key.

**Free-tier quotas are small enough to be exhausted during a rehearsal.** The default is therefore
`gemini-3.1-flash-lite` with thinking disabled, which is much cheaper per request, and Groq covers
the case where even that runs out. Raise `GEMINI_MODEL` if you want higher-quality analysis and can
spare the quota.

To see what a key can reach:

```bash
curl -s https://generativelanguage.googleapis.com/v1beta/models \
  -H "x-goog-api-key: $GEMINI_API_KEY" \
  | grep -o '"name": "models/[^"]*"'
```

### Auditing a run

Everything needed to verify an analysis is in the database:

```sql
SELECT provider, model, prompt_tokens, output_tokens, duration_ms,
       repair_attempted, validation_warnings, provider_notes
FROM   analysis_run
WHERE  process_id = ?
ORDER  BY started_at DESC;
```

`prompt_text` and `raw_response` on the same row hold the exact prompt sent and the exact response
received. `analysis_run_snippet` holds which sources were retrieved, their scores and the matched
terms. `provider` and `model` record who actually answered — which, with a fallback chain, is not
always who was asked first.

---

## 10. Design decisions and their reasons

| Decision | Reason |
|---|---|
| `AiProvider` interface with a chain behind it | The model call is the piece most likely to change — pricing, quotas, retirement. Adding Groq after Gemini's free tier ran out mid-testing was a new class plus a config value, not a rewrite |
| Curated research corpus instead of live web search | A rate-limited search API can fail in front of an audience, and its results are neither reproducible nor auditable afterwards. 16 cited excerpts live in Postgres and are retrieved by keyword match |
| Retrieval separate from prompting | Retrieval is deterministic and testable alone; the prompt is text in a resource file. Neither needs the other to change |
| Parsing and validation separate from persistence | Nothing untrusted reaches the database |
| Persistence in its own transactional bean | The model call takes tens of seconds and must not hold a database connection open — a real constraint on serverless Postgres |
| One repair retry, not a loop | A model that ignores an explicit schema twice will not comply on the third attempt, and a caller is waiting |
| Individually broken items dropped, not fatal | One typo'd enum should not discard an otherwise good analysis; every drop is recorded as a warning |
| Ownership decided in one service | Read and write rules cannot drift apart between endpoints — the usual way authorisation bugs happen |
| Stateless JWT rather than a session cookie | Frontend and API are on different sites; a third-party cookie would be blocked |
| Client-side data fetching | Server rendering would block on a cold Render instance and time out; fetching in the browser lets the UI say "the backend is waking up" |
| Audit tables as first-class schema | "Not hard-coded" is a claim; `prompt_text` and `raw_response` make it checkable |
| UUID keys generated in the application | Portable across Neon, Supabase and local Postgres without an extension, and lets a whole object graph be built before touching the database |
| `VARCHAR` + `CHECK` instead of native Postgres enums | Adding a value later is a plain migration rather than a type alteration, and values stay readable in `psql` |

---

## 11. Known limitations and what Phase 2 would add

**Current limitations.** Retrieval is keyword-based over a 16-snippet corpus, not embedding-based
over a large one — good enough to ground the analysis and cheap enough to be free, but it will miss
a semantically relevant source that shares no vocabulary with the process. Analysis is synchronous:
the caller holds the request for the duration of the model call, which is acceptable at this scale
but would need a job queue under load. There is one account per person, with no organisations, no
password reset and no email verification. Shared sample processes are analysable by anyone, which
means one person's re-analysis replaces the future state everyone else sees — intentional for demo
material, wrong for real work.

**Phase 2.** Multi-tenant organisations · password reset and email verification · embedding-based
retrieval over a larger corpus · versioned prompts with side-by-side run comparison · export to BPMN
or Visio · effort and cost estimates per intervention · a review workflow so a human can accept or
reject each opportunity before it becomes part of the stored future state.
