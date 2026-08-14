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
| **AI** | Google Gemini `gemini-2.5-flash` via the free AI Studio API |
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

---

## Run it locally

### Prerequisites

- **JDK 21** (`java -version`)
- **Node 20+** (`node -v`)
- **PostgreSQL 13+** — Docker Compose file included, or point at any instance you have
- **A free Gemini API key** from <https://aistudio.google.com/apikey> (needed only for `Analyze`;
  everything else works without one)

Maven is not required — the repo ships the Maven wrapper.

### 1. Start PostgreSQL

```bash
docker compose up -d          # postgres:16 on localhost:5432, db "future_designer"
```

<details>
<summary>No Docker? Run a throwaway Postgres in your home directory</summary>

```bash
# Needs the postgres server binaries installed (e.g. apt install postgresql), but no root.
export PGDATA=/tmp/fd-pgdata
initdb -D "$PGDATA" -U postgres --auth=trust
pg_ctl -D "$PGDATA" -o "-p 5432" -l "$PGDATA/server.log" start
createdb -h localhost -U postgres future_designer
```
</details>

Any PostgreSQL 13 or newer works. The schema needs no extensions.

### 2. Start the backend

```bash
cd backend
cp .env.example .env          # then put your Gemini key in it

export DATABASE_URL=jdbc:postgresql://localhost:5432/future_designer
export DATABASE_USERNAME=postgres
export DATABASE_PASSWORD=postgres
export GEMINI_API_KEY=your-key-here

./mvnw spring-boot:run
```

Flyway creates the schema and loads the sample data on first start — there are no manual SQL steps.
The service is up when <http://localhost:8080/actuator/health> returns `{"status":"UP"}`.

- API documentation: <http://localhost:8080/swagger-ui.html>
- Sanity check: `curl http://localhost:8080/api/processes` should list six processes.

### 3. Start the frontend

```bash
cd frontend
npm install
cp .env.example .env.local    # defaults to http://localhost:8080
npm run dev
```

Open <http://localhost:3000>.

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
cd backend && ./mvnw verify        # 84 tests
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
| The Gemini provider | Runs against a real local HTTP server: request shape, response unpacking, 429 retry, non-retryable auth failure, safety block, truncation |
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
| `GEMINI_API_KEY` | *(empty)* | Required for `/analyze`; absent means a clear 503 |
| `GEMINI_MODEL` | `gemini-2.5-flash` | Any model your key can reach |
| `GEMINI_STRUCTURED_OUTPUT` | `true` | Server-side response schema. Validation and repair still run either way |
| `GEMINI_THINKING_BUDGET` | `-1` | `-1` keeps the model default; `0` disables thinking for a faster demo |
| `ANALYSIS_SNIPPET_COUNT` | `4` | Grounding snippets injected per analysis |
| `ANALYSIS_RATE_LIMIT_PER_MINUTE` | `20` | Protects the free AI quota from a double-clicked button |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Comma-separated frontend origins |
| `NEXT_PUBLIC_API_BASE_URL` | `http://localhost:8080` | Frontend → backend (frontend build/runtime) |

---

## API

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/processes` | List processes with activity, opportunity and future-step counts |
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

**One live AI provider behind an interface.** `AiProvider` has a single implementation. The
interface exists so that a provider swap is a class plus a config value — not because runtime
failover is implemented. It deliberately is not: the brief asks for that risk to be explained, and
the time was better spent on the pipeline.

**Client-side data fetching.** Server rendering would block on a cold Render instance and time out.
Fetching in the browser lets the UI say "the backend is waking up".

**A repair retry, once.** A model that ignores an explicit JSON schema twice will not comply on the
third attempt, and a caller is waiting. One retry, then an honest `422` naming what was wrong.

**Individually broken items are dropped, not fatal.** One intervention with a typo'd enum shouldn't
discard an otherwise good analysis. Those items are dropped, and every drop is recorded as a warning
visible on the run. Only a wholly unusable response triggers the retry.

## What Phase 2 would add

Multi-tenant organisations with authentication · embedding-based retrieval over a larger corpus ·
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
    service/ai/              AiProvider interface + GeminiProvider
    controller/              REST endpoints and the RFC 7807 exception handler
    config/                  Typed configuration properties, CORS, OpenAPI
  src/main/resources/
    db/migration/            Flyway: V1 schema, V2 sample data
    prompts/                 Prompt templates — text, not Java
  src/test/java/             84 tests, integration tests on real PostgreSQL

frontend/src/
  app/                       Dashboard, new-process form, detail page, evidence corpus
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
