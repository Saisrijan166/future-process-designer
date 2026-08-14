# AI Future Process Designer

Takes a business process as it runs **today** and designs an AI-enabled **future** version of it —
storing the result as structured, queryable rows rather than a paragraph of advice.

Built for the Modus ETI Enterprise AI Build Challenge, Assignment 3. The demo domain is
**AssessWise**, a fictional online-assessment company, but the pipeline knows nothing about that
domain: you can create a process from any industry and get a real analysis with no code changes.

```
Current activities  →  Problems  →  AI opportunities  →  Future activities  →  Human vs AI  →  Benefit
    (rows)             (rows)         (rows + evidence)      (rows)            (columns)     (queryable)
```

| | |
|---|---|
| **Frontend** | Next.js 16 · React 19 · TypeScript · Tailwind 4 — deployed on Vercel |
| **Backend** | Spring Boot 3.5 · Java 21 — deployed on Render |
| **Database** | PostgreSQL 16 — Neon serverless |
| **AI** | Google Gemini (primary) with automatic failover to Groq — both free tier |
| **Accounts** | Email + password, BCrypt, stateless JWT — your processes are private to you |
| **Cost to run** | Nothing. Every service is free-tier or open source — see [LIBRARIES.md](LIBRARIES.md) |

**Live URLs** — fill these in after deploying:

- Frontend: `https://<your-app>.vercel.app`
- Backend: `https://<your-api>.onrender.com` · API docs at `/swagger-ui.html`

---

## What makes this more than a prompt wrapper

**The future process is data.** `future_activity` rows carry an ordered sequence, a human
responsibility, an AI responsibility and a responsibility type; `ai_intervention` rows join each of
them back to the `ai_opportunity` that justified it, which in turn joins to the `activity` it
targets and the `knowledge_snippet` rows that support it. The whole chain is one SQL query — see
[docs/data-model.md](docs/data-model.md).

**The reasoning is a pipeline, not one giant prompt.** Retrieval, prompt assembly, the model call,
response parsing, semantic validation, a repair retry and foreign-key resolution are separate,
separately tested components. The model contributes one step of eight.

**Nothing is hard-coded per process.** There is no branch anywhere on a process name. The six sample
processes ship with `status = CURRENT_ONLY` and no future state at all — the demo generates theirs
live, using the same code path as a process created thirty seconds earlier.

**Outputs are traceable.** Every run writes an `analysis_run` row holding the exact prompt sent, the
exact text the model returned, which snippets were retrieved and why, token counts, and whether the
repair retry fired. The UI exposes it behind a "Show prompt & raw response" button — the claim that
outputs are generated is a link, not an assertion.

**Citations can't be fabricated.** The model is only shown four snippets and is told to cite them by
exact title. Any title that doesn't resolve to a snippet actually retrieved for that run is
discarded, and the omission is recorded as a warning on the run.

**Your work is your own.** Sign in with an email and password. Processes you create are private
to your account — another signed-in user cannot list, search, read, analyse, edit or delete them,
and asking for one by id returns *404, not 403*, so the API cannot be used to probe for other
people's ids. The six sample processes are the deliberate exception: shared with everyone,
analysable by anyone, and editable by nobody, so one person cannot delete the demo data.

**It survives a provider outage.** Free AI tiers run out — Gemini's allows only a few dozen
requests a day. When it refuses, the request falls through to Groq automatically, and the result
page states plainly which service answered and why the first was passed over.

---

## Run it locally

### Prerequisites

- **JDK 21** (`java -version`)
- **Node 20+** (`node -v`)
- **PostgreSQL 13+** — Docker Compose file included, or point at any instance you have
- **At least one free AI API key** (needed only for `Analyze`; everything else works without one):
  - Gemini — <https://aistudio.google.com/apikey> (primary)
  - Groq — <https://console.groq.com/keys> (fallback; optional but recommended, since Gemini's free
    daily allowance is small)

Maven is not required — the repo ships the Maven wrapper.

### 1. Start PostgreSQL

With Docker:

```bash
docker compose up -d          # postgres:16 on localhost:5432, db "future_designer"
```

Without Docker or root — this creates a cluster in your home directory on port 55432, which is
what `backend/.env.example` points at by default:

```bash
./scripts/local-db.sh start   # also: stop | status | psql | reset
```

Any PostgreSQL 13 or newer works either way. The schema needs no extensions.

### 2. Start the backend

```bash
cd backend
cp .env.example .env          # then put your Gemini key in GEMINI_API_KEY
./run-local.sh                # loads .env and starts the app
```

`run-local.sh` exists because Spring Boot does not read `.env` files by itself. If you would rather
not use it:

```bash
set -a; source .env; set +a; ./mvnw spring-boot:run
```

Flyway creates the schema and loads the sample data on first start — there are no manual SQL steps.
The service is up when <http://localhost:8080/actuator/health> returns `{"status":"UP"}`.

- API documentation: <http://localhost:8080/swagger-ui.html>
- Sanity check: `curl http://localhost:8080/api/processes` should list six processes.

### 3. Start the frontend

```bash
cd frontend
npm install
cp .env.example .env.local    # defaults to http://localhost:8080 — Next.js reads this automatically
npm run dev
```

Open <http://localhost:3000> and sign in with **demo@assesswise.test** / **demo12345**, or create
your own account. The demo account is created on first start and can be switched off with
`AUTH_DEMO_ACCOUNT_ENABLED=false`.

### Without a Gemini API key

Everything except `POST /analyze` works: browse the sample processes, create your own, read the
evidence corpus. Pressing **Analyse** returns a clear `503` explaining that the key is missing,
rather than failing obscurely.

---

## Try the thing it was built for

1. Open <http://localhost:3000> and click **+ New process**.
2. Click **Fill an example**, or better — describe a process from an industry that has nothing to do
   with online assessment. Bank account opening, vaccine cold chain, restaurant inventory,
   warehouse picking. Four or five steps is plenty.
3. Press **Create and analyse**.
4. Ten to forty seconds later you have AI opportunities with reasoning and risks, a redesigned
   process with an explicit human/AI split per step, and interventions tying the two together.
5. Press **Show prompt & raw response** to see exactly what was sent and returned.
6. Confirm it is really rows:

```bash
psql "$DATABASE_URL" -c "
  SELECT fa.sequence_order, fa.name, fa.responsibility_type,
         fa.human_responsibility, fa.ai_responsibility
  FROM future_activity fa
  JOIN process p ON p.id = fa.process_id
  WHERE p.name = 'Your Process Name'
  ORDER BY fa.sequence_order;"
```

Re-run **Analyse** as often as you like: the previous future state is cleared and regenerated in one
transaction, so no duplicate or orphaned rows accumulate.

---

## Tests

```bash
cd backend && ./mvnw verify        # 126 tests
cd frontend && npm run lint && npm run typecheck && npm run build
```

The backend integration tests start a **real PostgreSQL** from the `embedded-postgres` binaries — no
Docker daemon, no preinstalled server. They run the production Flyway migrations, and Hibernate is
configured to `validate` against the result, so a JPA mapping that drifts from a migration fails the
build.

What is covered:

| Area | Examples |
|---|---|
| The surprise-record path | A waste-management process, analysed end to end through the real HTTP layer, asserting the resulting rows and their foreign keys |
| Idempotent re-analysis | Running twice leaves exactly one set of rows and zero orphaned interventions |
| The repair retry | A prose response triggers exactly one repair prompt containing the specific complaint, and the second attempt succeeds |
| Honest failure | Two unusable responses produce a `422`, leave the process untouched, and record a `FAILED` run with the reason |
| Citation integrity | A fabricated snippet title is discarded, leaves no `ai_opportunity_evidence` row, and is reported as a warning |
| Both AI providers | Each runs against a real local HTTP server: request shape, response unpacking, 429 retry, non-retryable auth failure, safety block, truncation |
| Provider failover | Falls through on quota exhaustion, skips a provider with no key, records who actually answered and why the first was passed over, and fails distinctly when none are configured |
| Accounts and isolation | Registration, sign-in, token rejection, and — the ones that matter — that one account cannot list, search, read, analyse, edit or delete another's process, that the shared samples are visible to all but writable by none, and that the dashboard totals count only what the caller can see |
| Listing, paging and search | Walks every page asserting no row is dropped or repeated, that a status filter applies to the whole dataset rather than the visible page, that the headline stats ignore the filter, that a typed `%` is a literal rather than match-everything, and that absurd page parameters are clamped |
| Parsing and validation | Markdown fences, prose wrappers, braces and escaped quotes inside strings, enum synonyms, oversized payloads, duplicate items |

The model call itself is scripted in tests, by a stub that lives in `src/test/java` only and is
never packaged.

---

## Deploy it

### Database — Neon

1. Create a free project at <https://neon.tech> and copy the connection details.
2. Convert the connection string to JDBC form and keep `sslmode=require`:
   `jdbc:postgresql://ep-xxx-pooler.<region>.aws.neon.tech/neondb?sslmode=require`

Flyway will create the schema and seed data on the backend's first start.

### Backend — Render

Either use the committed [`render.yaml`](render.yaml) blueprint, or create a Web Service manually:

| Setting | Value |
|---|---|
| Runtime | Docker |
| Dockerfile path | `./backend/Dockerfile` |
| Docker context | `./backend` |
| Health check path | `/actuator/health` |
| Plan | Free |

Environment variables to set in the dashboard:

```
DATABASE_URL=jdbc:postgresql://...neon.tech/neondb?sslmode=require
DATABASE_USERNAME=...
DATABASE_PASSWORD=...
DATABASE_POOL_SIZE=3
GEMINI_API_KEY=...
GROQ_API_KEY=...
AUTH_JWT_SECRET=...            # openssl rand -base64 48
AUTH_DEMO_ACCOUNT_ENABLED=false
APP_CORS_ALLOWED_ORIGINS=https://your-app.vercel.app
```

### Frontend — Vercel

Import the repository, then:

| Setting | Value |
|---|---|
| Root directory | `frontend` |
| Framework preset | Next.js (auto-detected) |
| Environment variable | `NEXT_PUBLIC_API_BASE_URL=https://your-api.onrender.com` |

Then come back and add the Vercel URL to `APP_CORS_ALLOWED_ORIGINS` on Render.

### Before demoing

The free Render instance sleeps after 15 minutes idle and takes about a minute to wake. **Load the
site a couple of minutes before you present.** The UI handles a cold start gracefully — it says the
backend is waking up rather than showing an error — but a warm instance makes for a better demo.

---

## Configuration reference

Every knob is an environment variable; nothing needs a code change. Full list with defaults in
[`backend/.env.example`](backend/.env.example) and
[`backend/src/main/resources/application.yml`](backend/src/main/resources/application.yml).

| Variable | Default | Purpose |
|---|---|---|
| `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` | local Postgres | Connection |
| `DATABASE_POOL_SIZE` | `5` | Keep small on Neon's free tier |
| `AUTH_JWT_SECRET` | *(dev placeholder)* | **Set this in production.** Signs session tokens; min 32 chars. `openssl rand -base64 48` |
| `AUTH_TOKEN_TTL_HOURS` | `12` | How long a sign-in lasts |
| `AUTH_DEMO_ACCOUNT_ENABLED` | `true` | Creates demo@assesswise.test on first start. **Turn off in production** |
| `AI_PROVIDER` | `gemini` | The provider tried first |
| `AI_FALLBACK_PROVIDERS` | `groq` | Tried in order when the primary fails. Empty disables failover |
| `GEMINI_API_KEY` | *(empty)* | Primary provider. With no key at all, `/analyze` returns a clear 503 |
| `GEMINI_MODEL` | `gemini-3.1-flash-lite` | Any model your key can reach — see the warning below |
| `GEMINI_STRUCTURED_OUTPUT` | `true` | Server-side response schema. Validation and repair still run either way |
| `GEMINI_THINKING_BUDGET` | `0` | `0` disables thinking, which saves free-tier tokens; `-1` restores the model default |
| `GROQ_API_KEY` | *(empty)* | Fallback provider |
| `GROQ_MODEL` | `llama-3.3-70b-versatile` | Completes reliably inside Groq's free per-minute budget |
| `GROQ_MAX_OUTPUT_TOKENS` | `4096` | Lower than Gemini's on purpose — Groq reserves this against its per-minute budget |
| `ANALYSIS_SNIPPET_COUNT` | `4` | Grounding snippets injected per analysis |
| `ANALYSIS_RATE_LIMIT_PER_MINUTE` | `20` | Protects the free AI quota from a double-clicked button |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Comma-separated frontend origins |
| `NEXT_PUBLIC_API_BASE_URL` | `http://localhost:8080` | Frontend → backend (frontend build/runtime) |

### A warning about model ids and free-tier quotas

Two things bite here, and both are configuration rather than code.

**Model availability changes without notice, and the `models` listing endpoint is not a reliable
guide to what your key may actually call.** `gemini-2.5-flash` is still listed but returns *"no
longer available to new users"* for a newly-issued key.

**Free-tier quotas are small.** `gemini-3.7-flash` allows roughly 20 requests a day — enough to be
exhausted during a rehearsal. The default is therefore `gemini-3.1-flash-lite` with thinking
disabled, which is much cheaper per request, and Groq covers the case where even that runs out.
Raise `GEMINI_MODEL` to `gemini-3.7-flash` if you want the higher-quality analysis and can spare
the quota.

If `Analyze` returns a `502` mentioning the model was not found, check what your key can reach and
set `GEMINI_MODEL` accordingly — no code change, no redeploy of the image:

```bash
curl -s https://generativelanguage.googleapis.com/v1beta/models \
  -H "x-goog-api-key: $GEMINI_API_KEY" \
  | grep -o '"name": "models/[^"]*"'
```

`gemini-flash-latest` is an alias that follows the newest Flash model — more resilient to retirement,
at the cost of output changing between runs.

---

## API

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/processes` | List processes — paginated, filterable and searchable (`page`, `size`, `status`, `q`, `sort`) |
| `POST` | `/api/processes` | Create a process and its current activities |
| `GET` | `/api/processes/{id}` | Full detail: current, transition, future, evidence |
| `PUT` | `/api/processes/{id}` | Replace the definition (clears any stale future state) |
| `DELETE` | `/api/processes/{id}` | Delete the process and everything derived from it |
| `POST` | `/api/processes/{id}/analyze` | Run the pipeline. Re-runnable and idempotent |
| `GET` | `/api/processes/{id}/comparison` | CURRENT / TRANSITION / FUTURE view with roll-up counters |
| `GET` | `/api/processes/{id}/analysis-runs` | Run history |
| `GET` | `/api/processes/{id}/analysis-runs/latest/trace` | The exact prompt and raw model response |
| `GET` | `/api/knowledge-snippets` | The curated research corpus |
| `GET` | `/api/roles`, `/api/systems` | Reference lookups |
| `GET` | `/actuator/health` | Liveness and readiness |

Everything under `/api` requires `Authorization: Bearer <token>` except the two sign-in routes and
the health check:

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/auth/register` | Create an account; returns a token |
| `POST` | `/api/auth/login` | Sign in; returns a token |
| `GET` | `/api/auth/me` | The account a token belongs to |

The UI also has a plain-language **How it works** page at `/how-it-works`, written for someone
meeting the project for the first time.

Errors are RFC 7807 problem documents with actionable messages: `400` validation (with per-field
detail), `404` not found, `409` an analysis is already running, `422` the model output was unusable
even after the repair retry, `429` rate limited, `502` the provider failed, `503` no API key
configured.

---

## Design decisions worth knowing about

**Curated research instead of live web search.** A rate-limited search API is a dependency that can
fail in front of judges, and its results are not reproducible or auditable afterwards. Instead, 16
cited excerpts live in Postgres and are retrieved by keyword match. Every URL is real and was
checked. The full reasoning, and the honest limitations, are in [docs/sources.md](docs/sources.md).

**Two AI providers, tried in order.** The original design had one provider behind an interface and
explicitly no failover, on the grounds that the brief asked for the "what if the free tier goes
away" risk to be *explained* rather than engineered around. That reasoning did not survive contact
with reality: Gemini's free tier ran out mid-testing at twenty requests, which would end a live
demo. Groq now backs Gemini up. The `AiProvider` interface is what made this a new class plus a
config value rather than a rewrite — which was the original point of having it.

**Client-side data fetching.** Server rendering would block on a cold Render instance and time out.
Fetching in the browser lets the UI say "the backend is waking up".

**A repair retry, once.** A model that ignores an explicit JSON schema twice will not comply on the
third attempt, and a caller is waiting. One retry, then an honest `422` naming what was wrong.

**Individually broken items are dropped, not fatal.** One intervention with a typo'd enum shouldn't
discard an otherwise good analysis. Those items are dropped, and every drop is recorded as a warning
visible on the run. Only a wholly unusable response triggers the retry.

## Who can see what

| Data | Scope |
|---|---|
| Research library, roles, systems | **Shared** — reference data; the same 16 sources ground everyone's analysis |
| The 6 sample processes | **Shared, read-only** — everyone sees and can analyse them; nobody can edit or delete them |
| Processes you create | **Private to your account** — invisible to everyone else, in listing, search and by id |
| Opportunities, future steps, runs | **Follow their process** — no separate ownership |

Analysing a shared sample updates it for everyone, which is intentional: they are common demo
material, not private work. Anything you need to keep to yourself, create as your own process.

The rules are enforced in one place — `ProcessAccessService` — so they cannot drift apart between
endpoints, which is the usual way authorisation bugs happen.

## What Phase 2 would add

Multi-tenant organisations (teams sharing a workspace, rather than one account per person) ·
password reset and email verification · embedding-based retrieval over a larger corpus ·
versioned prompts with side-by-side comparison of runs · export to BPMN or Visio · effort and cost
estimates per intervention · a review workflow so a human can accept or reject each opportunity
before it becomes part of the stored future state.

---

## Repository layout

```
backend/                     Spring Boot service
  src/main/java/com/assesswise/processdesigner/
    domain/                  JPA entities and enums
    repository/              Spring Data repositories
    dto/                     Request/response records; dto/ai/ is the model's contract
    service/                 The pipeline — retrieval, prompting, parsing, validation, persistence
    service/ai/              AiProvider interface, GeminiProvider, GroqProvider, FallbackAiProvider
    controller/              REST endpoints and the RFC 7807 exception handler
    config/                  Typed configuration properties, CORS, OpenAPI, provider chain
    security/                JWT issuing/decoding, security filter chain, current-user resolution
  src/main/resources/
    db/migration/            Flyway: V1 schema, V2 sample data, V3 provider audit, V4 accounts
    prompts/                 Prompt templates — text, not Java
  src/test/java/             126 tests, integration tests on real PostgreSQL

frontend/src/
  app/                       Login, dashboard, how-it-works, new-process form, detail page, evidence
  components/                Comparison strip, tab content, run trace panel, UI primitives
  lib/                       Typed API client, formatting, the resource-loading hook

docs/
  architecture-diagram.md    Layer diagram and pipeline sequence diagram
  data-model.md              ER diagram and the join path behind the chain
  sources.md                 The 16 curated sources and why the research layer works this way
  demo-script.md             10–15 minute walkthrough
  ai-tools-disclosure.md     How AI tooling was used to build this
```

## Documentation

- [Architecture](docs/architecture-diagram.md) — layers, pipeline sequence, deployment topology
- [Data model](docs/data-model.md) — ER diagram, the join path, schema decisions
- [Research sources](docs/sources.md) — all 16 sources with real URLs
- [Library inventory](LIBRARIES.md) — every dependency, version and licence
- [Demo script](docs/demo-script.md) — the 10–15 minute walkthrough
- [AI tools disclosure](docs/ai-tools-disclosure.md) — what was generated, what was decided
- [Seed data](data-seed.sql) — where the sample data lives and how to load it standalone
