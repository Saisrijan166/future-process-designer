# AI Future Process Designer

Takes a business process as it runs **today** and designs an AI-enabled **future** version of it —
researched live against public sources, with every recommendation citing a quote that was checked
against the page it came from, and the result stored as structured, queryable rows rather than a
paragraph of advice.

Built for the Modus ETI Enterprise AI Build Challenge, Assignment 3. The demo domain is
**AssessWise**, a fictional online-assessment company, but the pipeline knows nothing about that
domain: you can create a process from any industry and get a real analysis with no code changes.

```
Current activities → Problems → Live research → AI opportunities → Adversarial review →
     (rows)          (rows)     (sources +      (rows + verified    (verdict + critique
                                 quoted claims)    citations)          per recommendation)

  → Future activities → Human vs AI → Quantified impact → Risks → Roadmap → Measured score
       (rows)            (columns)      (rows, INR)      (rows)   (waves)    (six components)
```

Ten stages, each its own model call with its own prompt and its own stored audit row. Every prompt
and every response is readable in the running application.

| | |
|---|---|
| **Frontend** | Next.js 16 · React 19 · TypeScript · Tailwind 4 — deployed on Vercel |
| **Backend** | Spring Boot 3.5 · Java 21 — deployed on Render |
| **Database** | PostgreSQL 16 — Neon serverless |
| **AI** | Groq (primary — GPT-OSS 120B/20B, Qwen3, and `groq/compound` for agentic search) with automatic failover to Google Gemini — both free tier |
| **Research** | Eleven live connectors: Bing web and news, Google News, Wikipedia, OpenAlex, Crossref, arXiv, Europe PMC, Hacker News, Stack Exchange, agentic search — **none requiring an API key** |
| **Accounts** | Email + password, BCrypt, stateless JWT — your processes are private to you |
| **Cost to run** | Nothing. Every service is free-tier or open source — see [LIBRARIES.md](LIBRARIES.md) |

**Live URLs**

- Frontend: <https://future-process-designer.vercel.app>
- Backend: <https://future-process-designer.onrender.com> · API docs at `/swagger-ui.html`

> The backend sleeps after 15 minutes idle on Render's free plan and takes about a minute to wake.
> Open the frontend and give it a moment before judging it unresponsive.

---

## Submission deliverables

| Required | Where it is |
|---|---|
| **README / setup instructions** | This file — [Run it locally](#run-it-locally) for a from-scratch local run, [DEPLOYMENT.md](DEPLOYMENT.md) for the hosted one |
| **Architecture documentation** | [docs/architecture-diagram.md](docs/architecture-diagram.md) — the layers, the ten-stage analysis pipeline, and the request path end to end. Exported as PNG in [docs/diagrams/](docs/diagrams/) |
| **AI architecture** | [docs/ai-architecture.md](docs/ai-architecture.md) — the eight layers of the intelligence stack, from live research through quote verification to the guardrails, and which decisions belong to software rather than the model |
| **Technical documentation** | [docs/technical-documentation.md](docs/technical-documentation.md) — module map, full API reference, pipeline internals, security model, every configuration variable, testing and operations. [docs/](docs/) indexes everything |
| **Database documentation** | [docs/data-model.md](docs/data-model.md) — every table and column, the current/transition/future/evidence/audit split, and the SQL that walks a future step back to the verified quote behind it. ER diagram as PNG in [docs/diagrams/](docs/diagrams/) |
| **AI tools / model disclosure** | [docs/ai-tools-disclosure.md](docs/ai-tools-disclosure.md) — how this was built with AI assistance, what was generated, what was corrected, and what the assistant got wrong |
| **Library disclosure** | [LIBRARIES.md](LIBRARIES.md) — every dependency, its licence, and why it is there |
| **Sample data** | [V2__seed_data.sql](backend/src/main/resources/db/migration/V2__seed_data.sql) — 6 processes, 32 activities, 18 problems, 16 roles, 13 systems. Applied by Flyway at first start, so there is no manual load step. [data-seed.sql](data-seed.sql) documents the contents and how to run it by hand |
| **Research sources** | [docs/sources.md](docs/sources.md) — 16 cited excerpts, each with a URL verified to return HTTP 200, plus the corpus's limitations. Browsable in the running app under **Evidence** |

Two more that were not asked for but make the build checkable: [docs/demo-script.md](docs/demo-script.md)
walks the whole system in 10–15 minutes including the surprise-record test, and
[.github/workflows/ci.yml](.github/workflows/ci.yml) runs the test suite against a real PostgreSQL
on every push.

---

## What makes this more than a prompt wrapper

**It researches the actual domain, live.** Every analysis plans its own searches in the domain's
vocabulary, runs them across eleven free public sources, fetches the best results and reads them.
A process from an industry nothing here anticipates gets real sources about that industry — not a
fixed corpus retrieved by keyword.

**Quotes are checked mechanically, not asserted.** Each claim arrives with the words from the
source that support it, and that quote is then located in the stored page text by string matching.
Not found means the claim is kept and marked **unverified**, and can no longer raise anything's
grounding score. No model is asked whether it was telling the truth — a model that will invent a
quote will also confirm one.

**A second model marks the first one's homework.** The review runs on a different model family from
the generation, deliberately: a model checking its own work agrees with itself. Where the two
disagree you see the objection, scored on feasibility, evidence strength, impact, risk and effort.

**The analysis scores itself, and is allowed to score badly.** Six components — coverage, grounding,
corroboration, reviewer agreement, specificity, traceability — each a ratio over stored rows rather
than a model's opinion. A run whose sources were all blocked *should* score badly, and does.

**The future process is data.** `future_activity` rows carry an ordered sequence, a human
responsibility, an AI responsibility and a responsibility type; `ai_intervention` rows join each of
them back to the `ai_opportunity` that justified it, which in turn joins to the `activity` it
targets and the `knowledge_snippet` rows that support it. The whole chain is one SQL query — see
[docs/data-model.md](docs/data-model.md).

**The reasoning is a pipeline, not one giant prompt.** Ten stages, each with its own model, prompt
and stored row: read the process, diagnose it, research the domain, propose grounded interventions,
review them adversarially, design the future state, quantify it, assess risk, sequence delivery,
score the result. Two are load-bearing; the rest degrade rather than failing the run, which is what
makes it survivable on a free tier where a stage can lose its model mid-analysis.

**Nothing is hard-coded per process.** There is no branch anywhere on a process name. The six sample
processes ship with `status = CURRENT_ONLY` and no future state at all — the demo generates theirs
live, using the same code path as a process created thirty seconds earlier.

**Outputs are traceable.** Every stage writes an `analysis_stage` row holding the exact prompt sent,
the exact text the model returned, which model answered, what it cost in tokens, how long it waited
for rate-limit budget and whether it was served from cache. The interface shows all of it on a Run
trace tab — the claim that outputs are generated is a link, not an assertion.

**Citations cannot be fabricated.** The model cites evidence by the numbers it was shown. Any number
it was not shown is dropped and recorded as a fabricated citation; any citation whose quote failed
verification is stored and displayed differently from one that passed. A recommendation citing
nothing is kept and labelled ungrounded rather than quietly deleted — an idea with no supporting
literature can still be a good idea, and hiding that it has none would be the dishonest choice.

**Your work is your own.** Sign in with an email and password. Processes you create are private
to your account — another signed-in user cannot list, search, read, analyse, edit or delete them,
and asking for one by id returns *404, not 403*, so the API cannot be used to probe for other
people's ids. The six sample processes are the deliberate exception: shared with everyone,
analysable by anyone, and editable by nobody, so one person cannot delete the demo data.

**It survives a provider outage, and it is honest about the free tier.** Groq's free allowance is
roughly 8,000 tokens a minute, enforced across the whole organisation rather than per model — which
was measured, not read: a call to one model was refused with a different model's name in the error.
A token governor synchronises itself from each provider's rate-limit headers and makes stages wait
rather than fail; responses are cached in Postgres so a restart does not discard quota already
spent; and the high-volume work alternates providers, because a second provider is a second quota.
When a model refuses anyway, the run says which one answered instead and why. The **Engine** page
shows the remaining budget live.

---

## Run it locally

### Prerequisites

- **JDK 21** (`java -version`)
- **Node 20+** (`node -v`)
- **PostgreSQL 13+** — Docker Compose file included, or point at any instance you have
- **At least one free AI API key** (needed only for `Analyze`; everything else works without one):
  - Groq — <https://console.groq.com/keys> (primary; a thousand requests a day per model)
  - Gemini — <https://aistudio.google.com/apikey> (fallback, and a second quota the high-volume
    stages alternate into — worth setting even though the application runs on Groq alone)

  No key of any kind is needed for the research layer: all eleven connectors are keyless.

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
cp .env.example .env          # then put your Groq key in GROQ_API_KEY
./run-local.sh                # prints what it is connecting to, then starts the app
```

Or plainly, which works the same way — the application imports `backend/.env` itself:

```bash
cd backend && ./mvnw spring-boot:run
```

`run-local.sh` is still the better one to use while developing: it fails immediately if `.env` is
missing, warns if a provider key is empty, and prints the database, the provider chain and the
pipeline mode before it starts, which is what you want to see when a run does something unexpected.

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

### Without an AI API key

Everything except `POST /analyze` works: browse the sample processes, create your own, read the
evidence corpus. Pressing **Analyse** returns a clear `503` naming the environment variable to set,
rather than failing obscurely.

---

## Try the thing it was built for

1. Open <http://localhost:3000> and click **New process**.
2. Click **Start from an example**, or better — describe a process from an industry that has nothing
   to do with online assessment. Bank account opening, vaccine cold chain, restaurant inventory,
   warehouse picking. Four or five steps is plenty.
3. Press **Create and analyse**, and watch it run. The console shows each search being planned,
   each connector answering, each page being fetched and each quote being checked.
4. **A first analysis of a new process takes three to six minutes**, and most of that is waiting on
   the free tier's tokens-per-minute ceiling rather than on the models thinking. That is why the run
   streams its progress instead of showing a spinner. Re-running an unchanged process is close to
   instant, because every model response is cached.
5. What comes back: a diagnosis with root causes, recommendations that cite quote-verified evidence,
   a reviewing model's objections to each one, a redesigned process with an explicit human/AI split
   and a stated failure mode per step, the impact in rupees with its assumptions, a risk register,
   a delivery roadmap, and a measured score for the analysis itself.
6. Open **Evidence** to read every claim beside the quote that was checked against its source, and
   **Run trace** to see the exact prompt and response for all ten stages.
7. Confirm it is really rows:

```bash
psql "$DATABASE_URL" -c "
  SELECT fa.sequence_order, fa.name, fa.responsibility_type,
         fa.human_responsibility, fa.ai_responsibility, fa.failure_mode
  FROM future_activity fa
  JOIN process p ON p.id = fa.process_id
  WHERE p.name = 'Your Process Name'
  ORDER BY fa.sequence_order;"
```

And that the citations are real — every recommendation, with the quote behind it and whether that
quote was found in the page it came from:

```bash
psql "$DATABASE_URL" -c "
  SELECT left(o.description, 60) AS recommendation,
         c.quote_verified, s.domain, left(c.quote, 80) AS quote
  FROM ai_opportunity o
  JOIN ai_opportunity_claim link ON link.ai_opportunity_id = o.id
  JOIN evidence_claim c ON c.id = link.evidence_claim_id
  JOIN research_source s ON s.id = c.research_source_id
  JOIN process p ON p.id = o.process_id
  WHERE p.name = 'Your Process Name';"
```

Re-run **Analyse** as often as you like: the previous future state is cleared and regenerated in one
transaction, so no duplicate or orphaned rows accumulate.

---

## Tests

```bash
cd backend && ./mvnw verify        # 165 tests
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
| Quote verification | A quote copied from a page verifies; one that differs only in typography verifies; a fabricated sentence in the same register **does not**, and neither does the same vocabulary rearranged |
| The staged pipeline | All ten stages end to end: each stage's output in its own rows, reviews attached to the right recommendations, impact arithmetic computed here rather than by the model, and a non-essential stage failing without losing the run |
| Free-tier budgeting | A second model does not get a second per-minute allowance, a second provider does, a refused reservation is handed back, and a rate-limited model is taken out of rotation |
| Corroboration | Two publishers agreeing counts; one publisher repeating itself does not; two figures far apart are recorded as a disagreement rather than averaged |
| Honest failure | Two unusable responses produce a `422`, leave the process untouched, and record a `FAILED` run with the reason |
| Citation integrity | A fabricated snippet title is discarded, leaves no `ai_opportunity_evidence` row, and is reported as a warning |
| Both AI providers | Each runs against a real local HTTP server: request shape, response unpacking, 429 retry, non-retryable auth failure, safety block, truncation |
| Provider failover | Falls through on quota exhaustion, skips a provider with no key, records who actually answered and why the first was passed over, and fails distinctly when none are configured |
| Deployment misconfiguration | A `DATABASE_URL` with the credentials still embedded — the mistake every hosted-Postgres connection string invites — is caught at startup with the corrected URL printed, rather than failing deep inside the connection pool |
| Accounts and isolation | Registration, sign-in, token rejection, and — the ones that matter — that one account cannot list, search, read, analyse, edit or delete another's process, that the shared samples are visible to all but writable by none, and that the dashboard totals count only what the caller can see |
| Listing, paging and search | Walks every page asserting no row is dropped or repeated, that a status filter applies to the whole dataset rather than the visible page, that the headline stats ignore the filter, that a typed `%` is a literal rather than match-everything, and that absurd page parameters are clamped |
| Parsing and validation | Markdown fences, prose wrappers, braces and escaped quotes inside strings, enum synonyms, oversized payloads, duplicate items |

The model call itself is scripted in tests, by a stub that lives in `src/test/java` only and is
never packaged. **One test deliberately talks to the real internet** and is skipped unless asked
for, because the research layer's dependencies are eleven third parties with no contract to this
project — a mocked connector test only proves the mock still matches what the connector was written
against, which is precisely the thing that goes stale:

```bash
RESEARCH_LIVE_TESTS=true GROQ_API_KEY=... ./mvnw test -Dtest=LiveResearchSmokeTest
```

It asserts what would actually break: that some connectors still answer, that a page can still be
fetched and read, and that a quote taken from real page text still verifies against it while an
invented one does not. Worth running before a demo.

---

## Deploy it

Full step-by-step instructions, with the exact environment variables for each platform, are in
**[DEPLOYMENT.md](DEPLOYMENT.md)**. The short version:

| Step | Service | What you set |
|---|---|---|
| 1 | **Neon** — database | Nothing. Copy the *pooled* connection string; Flyway creates the schema and seeds the samples on first boot |
| 2 | **Render** — backend | `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD`, `AUTH_JWT_SECRET`, `GEMINI_API_KEY`, `GROQ_API_KEY`, `APP_CORS_ALLOWED_ORIGINS`. [`render.yaml`](render.yaml) sets the rest |
| 3 | **Vercel** — frontend | Root directory `frontend`, and `NEXT_PUBLIC_API_BASE_URL` pointing at the Render URL |
| 4 | back to **Render** | Put the Vercel URL into `APP_CORS_ALLOWED_ORIGINS` — until you do, the browser blocks every call |

Two things that catch people out, both covered in the guide: Vercel's **Root Directory must be
`frontend`**, and `NEXT_PUBLIC_API_BASE_URL` is baked in **at build time**, so changing it needs a
redeploy rather than a restart.

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
| `ANALYSIS_PIPELINE` | `staged` | `staged` = ten stages with live research; `single` = the original one-prompt analysis, which costs one request instead of eight |
| `AI_PROVIDER` | `groq` | The provider tried first |
| `AI_FALLBACK_PROVIDERS` | `gemini` | Tried in order when the primary fails. Empty disables failover |
| `GROQ_API_KEY` | *(empty)* | Primary provider. With no key at all, `/analyze` returns a clear 503 naming the variable |
| `GROQ_MODEL` | `openai/gpt-oss-120b` | The default when a per-task route names a model that has gone away — see the warning below |
| `GROQ_RESEARCH_MODEL` | `groq/compound` | The agentic model used as a research connector |
| `GEMINI_API_KEY` | *(empty)* | Fallback, and the second quota the high-volume stages alternate into |
| `GEMINI_MODEL` | `gemini-3.1-flash-lite` | Any model your key can reach |
| `GEMINI_THINKING_BUDGET` | `0` | `0` disables thinking, which saves free-tier tokens; `-1` restores the model default |
| `AI_ROUTE_*` | *(built-in defaults)* | Per-task model routing, e.g. `AI_ROUTE_DIAGNOSIS=gemini:,groq:openai/gpt-oss-120b`. One per stage |
| `AI_CACHE_ENABLED` | `true` | Remembers model responses in Postgres, so a restart does not discard spent quota |
| `AI_MAX_RATE_LIMIT_WAIT_SECONDS` | `60` | How long a stage waits for token budget before trying another model |
| `RESEARCH_ENABLED` | `true` | Live research across the eleven keyless connectors |
| `RESEARCH_MAX_DOCUMENTS` | `6` | **The main lever on run time.** Each document read costs a model call against a shared per-minute ceiling |
| `RESEARCH_MAX_QUERIES` | `5` | Searches the planner may produce |
| `RESEARCH_RESPECT_ROBOTS` | `true` | Honour robots.txt. A disallowed path is skipped and the source kept with its snippet |
| `RESEARCH_TAVILY_API_KEY` / `RESEARCH_BRAVE_API_KEY` | *(empty)* | Optional keyed search. Dormant without a key; nothing depends on them |
| `ANALYSIS_SNIPPET_COUNT` | `4` | Curated snippets used to ground a run whose live research found nothing |
| `ANALYSIS_RATE_LIMIT_PER_MINUTE` | `20` | Protects the free AI quota from a double-clicked button |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Comma-separated frontend origins |
| `NEXT_PUBLIC_API_BASE_URL` | `http://localhost:8080` | Frontend → backend (frontend build/runtime) |

### A warning about model ids and free-tier quotas

Three things bite here, and all three are configuration rather than code.

**Model availability changes without notice, and a listing endpoint is not a reliable guide to what
your key may actually call.** `gemini-2.5-flash` is still listed by Google but returns *"no longer
available to new users"* for a newly-issued key. On the Groq side the same lesson arrived a second
time: `llama-3.3-70b-versatile` — this project's own previous default — has been decommissioned
outright, and every analysis using it would have failed with a 404. Pin a model you have actually
called. The router now falls back through the provider chain, so a stale route degrades a run
rather than ending it.

**The tokens-per-minute ceiling is shared across models, not per model.** Groq publishes per-model
limits and also enforces an organisation-wide cap — measured by watching a call to
`groq/compound-mini` refused with *"Rate limit reached for model openai/gpt-oss-120b"*. Routing a
stage to a second Groq model therefore buys a second daily request allowance, not extra throughput.
The only real throughput multiplier is a second provider, which is why setting `GEMINI_API_KEY`
alongside `GROQ_API_KEY` makes a visible difference to how long a run takes.

**A full analysis costs about 30,000 tokens.** At 8,000 a minute that is a floor of roughly four
minutes for a fresh run, most of it waiting. Lower `RESEARCH_MAX_DOCUMENTS` to trade evidence for
speed; the response cache makes any re-run of an unchanged process near-instant.

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
| `POST` | `/api/processes/{id}/analyze/stream` | The same run, streaming its progress as Server-Sent Events, then the result |
| `GET` | `/api/processes/{id}/comparison` | CURRENT / TRANSITION / FUTURE view with roll-up counters |
| `GET` | `/api/processes/{id}/research` | The live research behind the stored analysis: queries, sources with their credibility arithmetic, and every claim with its checked quote |
| `GET` | `/api/processes/{id}/analysis-runs/active` | The run happening right now, with its stage progress — `204` when idle |
| `GET` | `/api/processes/{id}/analysis-runs` | Run history |
| `GET` | `/api/processes/{id}/analysis-runs/latest/trace` | Every stage, with the exact prompt sent and the exact text returned |
| `GET` | `/api/processes/{id}/analysis-runs/{runId}/stages` | The same, for a specific run |
| `GET` | `/api/system/ai-status` | Providers, per-task model routing, remaining free-tier budget, connector health |
| `GET` | `/api/knowledge-snippets` | The curated fallback corpus |
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
meeting the project for the first time, and an **Engine** page at `/system` showing which model each
stage will use and how much free-tier budget is left right now.

Errors are RFC 7807 problem documents with actionable messages: `400` validation (with per-field
detail), `404` not found, `409` an analysis is already running, `422` the model output was unusable
even after the repair retry, `429` rate limited, `502` the provider failed, `503` no API key
configured.

---

## Design decisions worth knowing about

**Live research, with the curated corpus kept as the fallback.** The first version of this
application deliberately avoided live search: a rate-limited API is a dependency that can fail in
front of judges, and its results are not auditable afterwards. Both halves of that turned out to be
solvable, and the reason to solve them was strong — a fixed corpus of sixteen excerpts grounded
every process in every industry identically, which is not research.

What made it defensible was auditing rather than trusting. Every source found is stored, every page
read is stored, and every claim carries a quote that is then located in that stored text by string
matching. The unreproducibility problem is answered by keeping the artefact; the fragility problem
is answered by having eleven independent connectors, so one being blocked degrades a run instead of
ending it. The curated corpus is still there and still used, as the grounding for a run that finds
nothing. [docs/sources.md](docs/sources.md) covers both layers and their limitations.

**Nothing a model says about its own sources is believed.** The single most important line of
defence is twenty lines of string matching. A model asked to check its own citation will confirm it;
a `String.indexOf` will not. Claims whose quotes fail that check are kept, marked unverified, and
excluded from every grounding score — visible rather than hidden, because a citation nobody can
check is worse than no citation if it is presented as though it could be.

**The reviewer is a different model family.** Self-review is close to worthless: a model marking its
own homework agrees with itself. Routing the review to Qwen while generation runs on GPT-OSS
produces real disagreement, and that disagreement is the most useful thing a reader of a generated
recommendation gets.

**Groq primary, Gemini fallback — and the reason is arithmetic.** Gemini's free tier allows a few
dozen requests a day, which a ten-stage pipeline exhausts in three runs; Groq allows a thousand a
day per model. Gemini stays configured because a second provider is a genuinely separate quota, and
on a free tier where the token ceiling is organisation-wide that is the only thing that actually
multiplies throughput. Both sit behind one `AiProvider` interface, which is what made switching the
primary a config change rather than a rewrite.

**Client-side data fetching.** Server rendering would block on a cold Render instance and time out.
Fetching in the browser lets the UI say "the backend is waking up".

**A repair retry, once.** A model that ignores an explicit JSON schema twice will not comply on the
third attempt, and a caller is waiting. One retry, then an honest `422` naming what was wrong.

**Individually broken items are dropped, not fatal.** One intervention with a typo'd enum shouldn't
discard an otherwise good analysis. Those items are dropped, and every drop is recorded as a warning
visible on the run. Only a wholly unusable response triggers the retry.

**Two stages are load-bearing; the other eight are not.** Without a diagnosis and without
opportunities and a future state there is no analysis, and the run stops with a message naming the
stage. Everything else may fail: a run that lost its roadmap is worth far more to the person waiting
than no run at all, and the trace and the scorecard both say what was lost.

**The impact model asks for inputs, never for answers.** A model asked "how much would this save?"
returns a confident, unfalsifiable figure. Asked instead for volume, handling time, the share of
that time genuinely removed and the hourly cost, it produces four numbers a reader can argue with —
and the arithmetic happens in ordinary Java where it can be checked by hand.

**Progress is streamed because the waiting is real.** A fresh analysis takes minutes, most of it
queued behind a token bucket. Hiding that behind a spinner would make a working system look hung,
and would hide the part worth seeing: the searches being planned, the sources arriving, the quotes
being checked one at a time.

## Who can see what

| Data | Scope |
|---|---|
| Curated corpus, roles, systems | **Shared** — reference data and the fallback grounding |
| Live research runs and their sources | **Follow the process they were gathered for** |
| Fetched page text | **Shared cache** — the same statute is read once a week rather than once per analysis, for politeness as much as speed |
| The 6 sample processes | **Shared, read-only** — everyone sees and can analyse them; nobody can edit or delete them |
| Processes you create | **Private to your account** — invisible to everyone else, in listing, search and by id |
| Opportunities, future steps, runs | **Follow their process** — no separate ownership |

Analysing a shared sample updates it for everyone, which is intentional: they are common demo
material, not private work. Anything you need to keep to yourself, create as your own process.

The rules are enforced in one place — `ProcessAccessService` — so they cannot drift apart between
endpoints, which is the usual way authorisation bugs happen.

## What Phase 2 would add

Multi-tenant organisations (teams sharing a workspace, rather than one account per person) ·
password reset and email verification · embedding-based retrieval so claims cluster by meaning
rather than by shared vocabulary, which is what currently limits corroboration detection ·
editable impact assumptions, so a user can replace a model's estimate with a measured figure and
watch the business case recompute · versioned prompts with side-by-side comparison of runs · export
to BPMN · a review workflow so a human can accept or reject each recommendation before it becomes
part of the stored future state · PDF parsing, which would open up the statutes and standards that
currently come back as snippet-only.

---

## Repository layout

```
backend/                     Spring Boot service
  src/main/java/com/assesswise/processdesigner/
    domain/                  JPA entities and enums
    repository/              Spring Data repositories
    dto/                     Request/response records; dto/ai/ is the model's contract
    service/                 Orchestration, persistence, validation, entity resolution
    service/ai/              The model layer: AiGateway, ModelRouter, TokenBudgetGovernor,
                             AiResponseCache, and the providers behind one interface
    service/research/        The research layer: connectors, page fetcher, content extractor,
                             claim extractor, QuoteVerifier, credibility scorer, corroboration
    service/research/connector/  Eleven search connectors, one class each
    service/pipeline/        The ten stages, the runner, the impact calculator, the scorecard
    service/progress/        Progress events and the SSE sink
    controller/              REST endpoints and the RFC 7807 exception handler
    config/                  Typed configuration properties, CORS, OpenAPI, provider wiring
    security/                JWT issuing/decoding, security filter chain, current-user resolution
  src/main/resources/
    db/migration/            Flyway: V1 schema, V2 sample data, V3 provider audit, V4 accounts,
                             V5 research, staged pipeline, impact, risk, roadmap, scorecard, cache
    prompts/                 Ten prompt templates — text, not Java
  src/test/java/             165 tests, integration tests on real PostgreSQL

frontend/src/
  app/                       Login, dashboard, process workspace, evidence, engine, how-it-works
  components/                app-shell, charts (inline SVG), evidence (citations and the drawer),
                             process-views (the ten tab panels), analysis-console (the live run),
                             ui (primitives)
  lib/                       Typed API client including the SSE reader, formatting, resource hook

docs/
  README.md                  Index of every document
  architecture-diagram.md    Layer diagram and pipeline sequence diagram
  ai-architecture.md         The AI layers and the software/model decision split
  data-model.md              ER diagram and the join path behind the chain
  technical-documentation.md Module map, API reference, config, security, operations
  diagrams/                  Each diagram as a PNG, with its Mermaid source beside it
  sources.md                 The research layer: live connectors, the curated fallback, limitations
  demo-script.md             10–15 minute walkthrough
  ai-tools-disclosure.md     How AI tooling was used to build this
```

## Documentation

Everything is indexed in **[docs/README.md](docs/README.md)**, and the required items are listed in
[Submission deliverables](#submission-deliverables) at the top of this file alongside the
[deployment guide](DEPLOYMENT.md). The three most substantial documents are
[architecture](docs/architecture-diagram.md), [data model](docs/data-model.md) and
[technical documentation](docs/technical-documentation.md).
