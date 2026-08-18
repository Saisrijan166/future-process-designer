# Architecture

Four real layers, each running as its own process, each replaceable without rewriting the others.

> **Diagrams as images.** Every diagram below is also exported as a full-resolution PNG in
> [`docs/diagrams/`](diagrams/) — [system architecture](diagrams/system-architecture.png) ·
> [analysis pipeline](diagrams/analysis-pipeline-sequence.png) ·
> [deployment topology](diagrams/deployment-topology.png). The Mermaid source for each is checked
> in beside it as a `.mmd` file, so the diagrams are regenerated from the same text that renders on
> this page rather than redrawn by hand.

```mermaid
flowchart TB
    subgraph UI["UI — Next.js 16 · React 19 · TypeScript · Tailwind 4"]
        direction LR
        DASH["/ — Process list"]
        NEW["/processes/new — Create a process"]
        DETAIL["/processes/[id] — Comparison strip<br/>Current · Transition · Future · Evidence"]
        EVID["/evidence — Curated corpus"]
    end

    subgraph API["API — Spring Boot 3.5 · Java 21"]
        direction LR
        PC["ProcessController<br/>CRUD + comparison"]
        AC["AnalysisController<br/>analyze + run trace"]
        EC["EvidenceController"]
        LC["LookupController"]
        AC2["AuthController<br/>register · login · me"]
        SEC["SecurityFilterChain<br/>stateless JWT · BCrypt"]
        ACC["ProcessAccessService<br/><i>the one place ownership is decided</i>"]
        GEH["GlobalExceptionHandler<br/>RFC 7807 problem details"]
    end

    subgraph AI["AI INTELLIGENCE LAYER"]
        direction TB
        AS["AnalysisService<br/><i>orchestrates the pipeline</i>"]
        KRS["KnowledgeRetrievalService<br/>TF-IDF-weighted keyword match"]
        PB["PromptBuilder + PromptTemplateRenderer<br/>templates in resources/prompts/"]
        AP["AiProvider <i>(interface)</i>"]
        FB["FallbackAiProvider<br/><i>tries each in order</i>"]
        GP["GeminiProvider<br/><i>primary</i>"]
        GQ["GroqProvider<br/><i>fallback</i>"]
        PARSE["AnalysisResponseParser<br/>fence stripping · brace matching"]
        VAL["AnalysisPayloadValidator<br/>enum coercion · caps · drops"]
        PERS["AnalysisPersistenceService<br/>FK resolution · idempotent replace"]
    end

    subgraph DATA["DATA & KNOWLEDGE LAYER — PostgreSQL (Neon free tier)"]
        direction LR
        CUR[("CURRENT<br/>process · activity · problem<br/>role · system_tool")]
        TRANS[("TRANSITION<br/>ai_opportunity<br/>ai_opportunity_evidence")]
        FUT[("FUTURE<br/>future_activity<br/>ai_intervention")]
        KNOW[("KNOWLEDGE<br/>knowledge_snippet")]
        AUDIT[("AUDIT<br/>analysis_run<br/>analysis_run_snippet")]
    end

    UI -->|"REST / JSON + Bearer token"| SEC
    SEC --> API
    AC2 --> DATA
    PC --> ACC
    AC --> ACC
    PC --> DATA
    EC --> KNOW
    LC --> DATA
    AC --> AS

    AS --> KRS --> KNOW
    AS --> PB
    AS --> AP
    AP -.->|"the only seam to a model"| FB
    FB --> GP
    FB -.->|"only if Gemini fails"| GQ
    GP -->|"HTTPS"| GEMINI(["Google AI Studio<br/>gemini-3.1-flash-lite · free tier"])
    GQ -->|"HTTPS"| GROQ(["Groq Cloud<br/>llama-3.3-70b · free tier"])
    AS --> PARSE --> VAL --> PERS
    PERS --> CUR & TRANS & FUT
    AS --> AUDIT

    classDef ui fill:#eef2ff,stroke:#6366f1,color:#1e1b4b
    classDef api fill:#ecfeff,stroke:#0891b2,color:#083344
    classDef ai fill:#f0fdf4,stroke:#16a34a,color:#052e16
    classDef data fill:#fefce8,stroke:#ca8a04,color:#422006
    classDef ext fill:#fdf2f8,stroke:#db2777,color:#500724

    class DASH,NEW,DETAIL,EVID ui
    class PC,AC,AC2,SEC,ACC,EC,LC,GEH api
    class AS,KRS,PB,AP,FB,GP,GQ,PARSE,VAL,PERS ai
    class CUR,TRANS,FUT,KNOW,AUDIT data
    class GEMINI,GROQ ext
```

## The analysis pipeline, step by step

`POST /api/processes/{id}/analyze` is the only entry point, and it runs the same eight steps for
every process — seed data and a process created seconds ago by a judge take an identical path.

```mermaid
sequenceDiagram
    autonumber
    participant U as Browser
    participant C as AnalysisController
    participant S as AnalysisService
    participant DB as PostgreSQL
    participant R as KnowledgeRetrievalService
    participant P as PromptBuilder
    participant G as Provider chain<br/>(Gemini → Groq)
    participant V as Parser + Validator
    participant W as AnalysisPersistenceService

    U->>C: POST /api/processes/{id}/analyze
    C->>S: analyze(processId)
    S->>S: rate-limit check · reject if already running
    S->>DB: load process, activities, roles, systems, recorded problems
    S->>R: retrieve(process, activities)
    R->>DB: SELECT * FROM knowledge_snippet
    R-->>S: top 4 snippets + score + matched terms
    S->>P: render prompts/analyze-process.txt
    P-->>S: prompt text
    S->>DB: INSERT analysis_run (RUNNING) + retrieved snippets
    S->>G: complete(prompt)
    G->>G: transport retry on 429/5xx
    alt primary provider unusable (quota, outage, bad key)
        G->>G: fall through to the next provider in the chain
    end
    G-->>S: raw text + token counts + which provider answered
    S->>V: parse + validate
    alt response unusable
        V-->>S: errors
        S->>P: render prompts/repair-json.txt with the errors
        S->>G: complete(repair prompt) — one retry only
        G-->>S: raw text
        S->>V: parse + validate
    end
    alt still unusable
        S->>DB: UPDATE analysis_run (FAILED)
        S-->>U: 422 with the specific reason
    else usable
        S->>W: replaceAnalysis(...)
        W->>DB: DELETE previous AI rows, INSERT new rows with resolved FKs
        S->>DB: UPDATE analysis_run (SUCCEEDED) + warnings
        S-->>U: 200 with the full structured result
    end
```

## Why these boundaries

| Boundary | Reason |
|---|---|
| `AiProvider` interface, with a chain behind it | The model call is the piece most likely to change — pricing, quotas, retirement. Isolating it meant that adding Groq as a fallback, after Gemini's free tier ran out mid-testing, was a new class plus a config value rather than a rewrite. The pipeline is unaware that failover exists. |
| Retrieval separate from prompting | Retrieval is deterministic and testable on its own; the prompt is text in a resource file. Neither needs the other to change. |
| Parsing and validation separate from persistence | Nothing untrusted reaches the database. The validator emits a normalised structure, and persistence only ever writes that. |
| Persistence in its own transactional bean | The model call takes tens of seconds and must not hold a database connection open — a real constraint on a serverless Postgres with a small connection allowance. |
| Ownership decided in one service | Every route touching a single process calls `ProcessAccessService`, so read rules and write rules cannot drift apart between endpoints. It returns 404 rather than 403 for someone else's process, because a 403 would confirm the id exists. |
| Stateless JWT rather than a session cookie | The frontend and API are on different sites (Vercel and Render). A session cookie would have to be `SameSite=None` third-party, which browsers increasingly block; a bearer token is unaffected. |
| Audit trail as first-class tables | "Not hard-coded" is a claim; `analysis_run.prompt_text` and `analysis_run.raw_response` make it checkable. |

## Deployment topology

```mermaid
flowchart LR
    B(["Browser"]) -->|HTTPS| V["Vercel<br/>Next.js static + edge<br/><i>free hobby tier</i>"]
    B -->|"HTTPS · REST"| R["Render<br/>Docker web service<br/><i>free tier, sleeps when idle</i>"]
    R -->|"TLS · JDBC"| N[("Neon<br/>PostgreSQL<br/><i>free serverless tier</i>")]
    R -->|HTTPS| G(["Google AI Studio<br/><i>free tier · primary</i>"])
    R -.->|"HTTPS, on failure"| GQ(["Groq Cloud<br/><i>free tier · fallback</i>"])
```

The browser talks to both hosts directly: the frontend fetches client-side rather than
server-rendering, precisely because the free Render instance cold-starts in about a minute and a
server-rendered page would time out. The UI shows that as a "waking up" state instead.
