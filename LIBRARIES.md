# Library, model and service inventory

Every dependency, model and hosted service used, with its version and licence.

**Nothing here requires a paid licence to run, develop or demo this application.** Every entry is
open source under a permissive licence, or a hosted service used entirely within its free tier.

Versions below are the ones resolved by the committed `pom.xml` and `package-lock.json`.

---

## Hosted services

| Service | Used for | Plan | Licence / terms | If it stopped being free |
|---|---|---|---|---|
| **Google AI Studio (Gemini API)** | The one live AI provider — `gemini-2.5-flash` | Free tier | [Gemini API terms](https://ai.google.dev/gemini-api/terms); free tier has per-minute and per-day request limits | The model call sits behind a single `AiProvider` interface. Swapping to Groq's free tier, an OpenAI-compatible endpoint, or a locally-run Ollama model is one new class plus `AI_PROVIDER=<name>` — no change to the pipeline, the schema or the UI. Deliberately **not** implemented: no second live provider and no runtime failover ship, because the brief asks for this risk to be explained, not engineered around. |
| **Neon** | PostgreSQL (serverless) | Free tier | Free plan, no card required | Any PostgreSQL 13+ works. Supabase's free tier is a drop-in: change `DATABASE_URL`. The schema uses no Neon-specific features. |
| **Render** | Backend hosting (Docker web service) | Free | Free plan; instance sleeps after 15 min idle, ~50s cold start | The deployable is a plain Spring Boot jar in a standard Dockerfile — Fly.io, Railway, Koyeb or any container host takes it unchanged. |
| **Vercel** | Frontend hosting | Free hobby | Hobby plan | A stock Next.js app; Netlify or Cloudflare Pages deploy it with no code change, or `next build && next start` on any Node host. |

### Model

| Model | Provider | Version pinned | Cost |
|---|---|---|---|
| `gemini-2.5-flash` | Google | Configurable via `GEMINI_MODEL` | Free tier |

Chosen over the `gemini-1.5-flash` named in the original plan because 1.5 Flash is no longer the
current free-tier default; 2.5 Flash is its successor, is available on the free tier, and supports
the structured-output response schema the pipeline uses. The model id is configuration, not code.

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
| `flyway-core` | 11.7.2 | Apache-2.0 | Versioned schema migrations |
| `flyway-database-postgresql` | 11.7.2 | Apache-2.0 | Postgres support for Flyway 11 |
| `postgresql` (JDBC driver) | 42.7.11 | BSD-2-Clause | Database connectivity |
| `springdoc-openapi-starter-webmvc-ui` | 2.8.17 | Apache-2.0 | OpenAPI document and Swagger UI at `/swagger-ui.html` |
| `lombok` | 1.18.46 | MIT | Getters/setters on JPA entities; compile-time only, excluded from the jar |
| `spring-boot-configuration-processor` | 3.5.16 | Apache-2.0 | IDE metadata for `@ConfigurationProperties`; compile-time only |

### Test-only dependencies

| Library | Version | Licence | Why it is here |
|---|---|---|---|
| `spring-boot-starter-test` | 3.5.16 | Apache-2.0 | JUnit 5, AssertJ, Mockito, `TestRestTemplate` |
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
