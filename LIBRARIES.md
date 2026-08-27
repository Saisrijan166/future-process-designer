# Library, model and service inventory

Every dependency, model and hosted service used, with its version and licence.

**Nothing here requires a paid licence to run, develop or demo this application.** Every entry is
open source under a permissive licence, or a hosted service used entirely within its free tier.

Versions below are the ones resolved by the committed `pom.xml` and `package-lock.json`.

---

## Hosted services

| Service | Used for | Plan | Licence / terms | If it stopped being free |
|---|---|---|---|---|
| **Google AI Studio (Gemini API)** | Fallback AI provider, and the second quota that makes high-volume stages viable — `gemini-3.1-flash-lite` | Free tier | [Gemini API terms](https://ai.google.dev/gemini-api/terms); free tier has tight per-minute and per-day request limits | Groq already backs it up automatically (below). Beyond that, the model call sits behind a single `AiProvider` interface, so an OpenAI-compatible endpoint or a locally-run Ollama model is one new class plus a config value. |
| **Groq Cloud** | Primary AI provider — `openai/gpt-oss-120b`, `openai/gpt-oss-20b`, `qwen/qwen3.8-27b`, and `groq/compound` for agentic search | Free tier | [Groq terms](https://groq.com/terms-of-use/); measured free tier is ~1,000 requests/day per model with an **organisation-wide 8,000 tokens-per-minute ceiling shared across models** | Gemini already backs it up automatically, and Cerebras, OpenRouter and a local Ollama are configuration entries against the same OpenAI-compatible client — a base URL, a key and a model name. |
| **Neon** | PostgreSQL (serverless) | Free tier | Free plan, no card required | Any PostgreSQL 13+ works. Supabase's free tier is a drop-in: change `DATABASE_URL`. The schema uses no Neon-specific features. |
| **Render** | Backend hosting (Docker web service) | Free | Free plan; instance sleeps after 15 min idle, ~50s cold start | The deployable is a plain Spring Boot jar in a standard Dockerfile — Fly.io, Railway, Koyeb or any container host takes it unchanged. |
| **Vercel** | Frontend hosting | Free hobby | Hobby plan | A stock Next.js app; Netlify or Cloudflare Pages deploy it with no code change, or `next build && next start` on any Node host. |

### Model

| Model | Role | Provider | Configurable via | Cost |
|---|---|---|---|---|
| `openai/gpt-oss-120b` | Diagnosis, opportunities, future-state design — the three stages that genuinely need the strongest model | Groq (OpenAI weights) | `GROQ_MODEL`, `AI_ROUTE_*` | Free tier |
| `openai/gpt-oss-20b` | Claim extraction and query planning: high volume, close to mechanical | Groq (OpenAI weights) | `AI_ROUTE_CLAIM_EXTRACTION` | Free tier |
| `qwen/qwen3.8-27b` | The adversarial review, and the risk register | Groq (Alibaba weights) | `AI_ROUTE_CRITIQUE` | Free tier |
| `groq/compound` | Agentic web search: runs its own searches and returns the pages it read | Groq | `GROQ_RESEARCH_MODEL` | Free tier |
| `gemini-3.1-flash-lite` | Quantification and roadmap, plus fallback for every stage | Google | `GEMINI_MODEL` | Free tier |

The review deliberately runs on a **different model family** from the generation. A model
asked to check its own work agrees with itself; a Qwen model reviewing a GPT-OSS model's
proposals disagrees often enough to be worth reading, and that disagreement is what the
confidence scores are built from.

The original plan named `gemini-1.5-flash`. That model is retired. `gemini-2.5-flash` was tried
next and **also failed** — it is still returned by the `models` endpoint, but calling it with a
newly-issued key gives *"no longer available to new users"*. `gemini-3.7-flash` was then verified
by an actual `generateContent` call, with the structured-output response schema this pipeline sends,
on 14-08-2026.

The lesson, worth stating because it will happen again: **pin a model you have actually called, not
one a document recommends.** Free-tier model availability changes without notice and the model
listing endpoint is not a reliable guide to what a given key may use. The model id is configuration
(`GEMINI_MODEL`), so this is a dashboard edit rather than a redeploy of code. `gemini-flash-latest`
is an alias that auto-follows the newest Flash model — more resilient, at the cost of the output
changing under you between demos.

Other models verified working with this pipeline's request shape on the same date:
`gemini-flash-latest`, `gemini-3.5-flash-lite`, `gemini-3.7-flash`.

**Why a lite model is the default.** `gemini-3.7-flash` produces better analysis, but its free tier
allows roughly 20 requests a day — it was exhausted during a single testing session. The default is
therefore `gemini-3.1-flash-lite` with thinking disabled (thinking tokens are charged against the
same allowance and this task does not need them), which cut a typical run from 27 seconds to 8.

**On the Groq side, the same lesson arrived a second time.** `llama-3.3-70b-versatile` was the
default here until it was decommissioned: it is no longer in Groq's model list at all, and every
analysis using it would have failed with a 404. The models above were verified with live
`chat/completions` calls on 27-08-2026, and the router now falls back through the configured
provider chain so that a stale route degrades a run instead of ending it.

**The free tier's real shape**, measured rather than read off a documentation page:

| Model | Requests/day | Tokens/minute | Measured cost per call |
|---|---|---|---|
| `openai/gpt-oss-120b`, `gpt-oss-20b`, `qwen/qwen3.*` | 1,000 | 8,000 | 3,000–7,000 |
| `groq/compound`, `compound-mini` | 250 | 70,000 | 10,000–17,000 |

The important part is not in that table. Groq publishes per-model limits **and enforces an
organisation-wide tokens-per-minute ceiling across every model** — observed directly, by watching a
call to `groq/compound-mini` refused with *"Rate limit reached for model openai/gpt-oss-120b"*. So
routing a stage to a second Groq model buys a second daily request allowance, not extra throughput;
the only real throughput multiplier is a second provider, which is why Gemini serves the
quantification and roadmap stages and why claim extraction alternates between the two.

---

## Research connectors

Eleven sources, every one free and **none requiring an API key or an account**. Each was verified
with a live request on 27-08-2026; the date matters, because this is the layer most likely to rot.

| Connector | Endpoint | Terms | Notes |
|---|---|---|---|
| Bing web search | `bing.com/search?format=rss` | Public RSS output | The backbone general-web connector. A documented output format rather than scraping. |
| Bing News | `bing.com/news/search?format=RSS` | Public RSS output | A second, independently-indexed news source. |
| Google News | `news.google.com/rss/search` | Public RSS output | Links are redirects; the real publisher comes from the feed's `<source url>`. |
| Wikipedia | `en.wikipedia.org/w/api.php` | CC BY-SA content, open API | Establishes a domain's vocabulary so the other connectors get better queries. |
| OpenAlex | `api.openalex.org` | CC0 data, open API, polite pool via `mailto` | ~250M scholarly works. Abstracts arrive inverted and are reassembled. |
| Crossref | `api.crossref.org` | Open API, polite pool via `mailto` | The DOI registry. Overlaps OpenAlex deliberately — different results for the same words is what corroboration needs. |
| arXiv | `export.arxiv.org/api/query` | Open API, request-rate terms | Preprints, and its abstract pages are HTML rather than PDF so they can actually be quoted. |
| Europe PMC | `ebi.ac.uk/europepmc/webservices/rest` | Open API | Medical-education literature: examiner reliability, rater agreement, OSCE grading. |
| Hacker News | `hn.algolia.com/api/v1` | Free public API | What happened when someone shipped it, which no paper reports. Scored as practitioner evidence. |
| Stack Exchange | `api.stackexchange.com/2.3` | Free, 300 requests/day unauthenticated | Implementation constraints, for the feasibility judgement. |
| Groq agentic search | `groq/compound` via the chat API | Groq free tier | Runs its own searches server-side and returns the pages it read, which are stored and quote-verified like any other source. |

**Deliberately absent.** DuckDuckGo's HTML endpoint returns an anomaly page to server-side
requests, public SearX instances return a captcha, and GDELT did not respond from a server at all.
They are excluded rather than left in place to fail silently — a connector that always returns
nothing looks identical to a topic with no coverage.

**Optional and dormant:** Tavily and Brave are wired up and stay switched off unless a key is
supplied. Nothing in the default configuration depends on them.

---

## Backend — Java 21

Build tool: **Apache Maven 3.9.16** via the committed wrapper (`./mvnw`) — Apache-2.0.
Runtime: **Eclipse Temurin JDK 21** (GPL-2.0 with Classpath Exception).

### Direct dependencies

| Library | Version | Licence | Why it is here |
|---|---|---|---|
| `spring-boot-starter-web` | 3.5.16 | Apache-2.0 | REST API, embedded Tomcat, Jackson |
| `spring-boot-starter-data-jpa` | 3.5.16 | Apache-2.0 | Repositories and transaction management |
| `spring-boot-starter-validation` | 3.5.16 | Apache-2.0 | Bean Validation on request DTOs |
| `spring-boot-starter-actuator` | 3.5.16 | Apache-2.0 | `/actuator/health` for the Render health check |
| `spring-boot-starter-security` | 3.5.16 | Apache-2.0 | Authentication filter chain and BCrypt password hashing |
| `spring-boot-starter-oauth2-resource-server` | 3.5.16 | Apache-2.0 | JWT signing and verification through Nimbus JOSE, so no third-party JWT library is needed |
| `flyway-core` | 11.7.2 | Apache-2.0 | Versioned schema migrations |
| `flyway-database-postgresql` | 11.7.2 | Apache-2.0 | Postgres support for Flyway 11 |
| `postgresql` (JDBC driver) | 42.7.11 | BSD-2-Clause | Database connectivity |
| `jsoup` | 1.23.2 | MIT | HTML parsing and main-content extraction for the research layer, and XML parsing for the RSS and Atom connectors. Fetched pages are hostile — unclosed tags, script-injected bodies, navigation outweighing the article — and a strict parser rejects a whole document over one bad character. |
| `springdoc-openapi-starter-webmvc-ui` | 2.8.17 | Apache-2.0 | OpenAPI document and Swagger UI at `/swagger-ui.html` |
| `lombok` | 1.18.46 | MIT | Getters/setters on JPA entities; compile-time only, excluded from the jar |
| `spring-boot-configuration-processor` | 3.5.16 | Apache-2.0 | IDE metadata for `@ConfigurationProperties`; compile-time only |

### Test-only dependencies

| Library | Version | Licence | Why it is here |
|---|---|---|---|
| `spring-boot-starter-test` | 3.5.16 | Apache-2.0 | JUnit 5, AssertJ, Mockito, `TestRestTemplate` |
| `spring-security-test` | 6.5.x | Apache-2.0 | Security-aware test support |
| `embedded-postgres` | 2.2.2 | Apache-2.0 | Starts a real PostgreSQL 16 from bundled binaries — integration tests run the actual migrations without Docker or a preinstalled server |
| `embedded-postgres-binaries-*` | 16.14.0 | PostgreSQL Licence | The PostgreSQL binaries the above starts |

### Notable transitive dependencies

Pulled in by the starters above, listed because they do real work in this application:
`hibernate-core` 6.6.53 (LGPL-2.1 / Apache-2.0 dual), `HikariCP` 6.3.3 (Apache-2.0),
`jackson-databind` 2.21.4 (Apache-2.0), `logback-classic` 1.5.34 (EPL-1.0 / LGPL-2.1),
`hibernate-validator` 8.0.3 (Apache-2.0), `tomcat-embed-core` 10.1.55 (Apache-2.0),
`micrometer-core` 1.15.12 (Apache-2.0), `swagger-ui` 5.32.2 (Apache-2.0).

**No HTTP client library is used for the Gemini call.** It goes through Spring's `RestClient` on top
of the JDK's built-in `java.net.http.HttpClient` — one less dependency to justify, audit and update.

---

## Frontend — Node 20

| Library | Version | Licence | Why it is here |
|---|---|---|---|
| `next` | 16.3.1 | MIT | App Router, build and dev server |
| `react` | 19.2.8 | MIT | UI |
| `react-dom` | 19.2.8 | MIT | DOM renderer |
| `typescript` | 5.9.3 | Apache-2.0 | Types (dev only) |
| `tailwindcss` | 4.3.3 | MIT | Styling (dev only) |
| `@tailwindcss/postcss` | 4.3.3 | MIT | Tailwind's PostCSS plugin (dev only) |
| `eslint` | 9.39.5 | MIT | Linting (dev only) |
| `eslint-config-next` | 16.3.1 | MIT | Next.js and React lint rules (dev only) |
| `@types/node`, `@types/react`, `@types/react-dom` | 22.20.1 / 19.2.18 / 19.2.2 | MIT | Type definitions (dev only) |

**No component library, no state-management library, no data-fetching library, no charting
library, no icon package.** The UI is small enough that Tailwind plus a handful of local components
in `src/components/ui.tsx` is less code overall than wiring up a framework — and it keeps the
dependency surface something one person can actually vouch for.

---

## Development tooling

| Tool | Version | Licence | Notes |
|---|---|---|---|
| Docker Compose (optional) | any | Apache-2.0 | Only used to run local PostgreSQL; the app itself runs on the host. Not required — any Postgres works. |
| GitHub Actions | — | Free for public repositories | Build and test on every push |

---

## Licence summary

| Licence | Count | Commercial use permitted |
|---|---|---|
| Apache-2.0 | Majority of backend | Yes |
| MIT | All frontend runtime | Yes |
| BSD-2-Clause | PostgreSQL JDBC driver | Yes |
| EPL-1.0 / LGPL-2.1 | Logback | Yes (unmodified use) |
| LGPL-2.1 / Apache-2.0 | Hibernate ORM | Yes |
| PostgreSQL Licence | Test-only Postgres binaries | Yes |
| GPL-2.0 + Classpath Exception | Eclipse Temurin JDK | Yes (the exception is what makes linking fine) |

No copyleft obligation attaches to this application's own source: Logback and Hibernate are used as
unmodified libraries, and the Classpath Exception covers the JDK.

## Verifying this list

```bash
# Backend — full resolved dependency tree with versions
cd backend && ./mvnw dependency:tree

# Backend — licences, generated into target/site/
cd backend && ./mvnw license:aggregate-third-party-report

# Frontend — direct dependencies and versions
cd frontend && npm ls --depth=0
```
