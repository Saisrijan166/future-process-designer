# Architecture

Four deployed layers — interface, API, intelligence, data — each running as its own process and
each replaceable without rewriting the others. Inside the intelligence layer there are three more
that are worth separating, because they fail differently: the **pipeline** that sequences ten
stages, the **research layer** that gathers evidence from the public web, and the **model gateway**
that is the single seam to any language model.

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
        DETAIL["/processes/[id] — Ten tabs<br/>Overview · Today · Diagnosis · Recommendations<br/>Future · Impact · Risks · Roadmap · Evidence · Trace"]
        CONSOLE["Live analysis console<br/><i>streams every stage as it runs</i>"]
        SYS["/system — Engine<br/>routing · budgets · connectors"]
    end

    subgraph API["API — Spring Boot 3.5 · Java 21"]
        direction LR
        PC["ProcessController<br/>CRUD + comparison"]
        AC["AnalysisController<br/>analyze · analyze/stream (SSE)<br/>research · run trace · stages"]
        SC["SystemController<br/>model routing · live budgets"]
        EC["EvidenceController"]
        AC2["AuthController<br/>register · login · me"]
        SEC["SecurityFilterChain<br/>stateless JWT · BCrypt"]
        ACC["ProcessAccessService<br/><i>the one place ownership is decided</i>"]
        GEH["GlobalExceptionHandler<br/>RFC 7807 problem details"]
    end

    subgraph PIPE["ANALYSIS PIPELINE — ten stages, one model call each"]
        direction TB
        P1["1 Intake<br/><i>no model: counts and gaps</i>"]
        P2["2 Diagnosis<br/>problems + root causes"]
        P3["3 Research<br/><i>live, see below</i>"]
        P4["4 Opportunities<br/>must cite evidence by number"]
        P5["5 Critique<br/><i>a different model family</i>"]
        P6["6 Future design<br/>human/AI split + failure modes"]
        P7["7 Quantification<br/><i>model gives inputs, we do the maths</i>"]
        P8["8 Risks + obligations"]
        P9["9 Roadmap<br/>waves and dependencies"]
        P10["10 Scorecard<br/><i>no model: ratios over stored rows</i>"]
        P1 --> P2 --> P3 --> P4 --> P5 --> P6 --> P7 --> P8 --> P9 --> P10
    end

    subgraph RESEARCH["RESEARCH LAYER — eleven connectors, none needing a key"]
        direction TB
        QP["ResearchQueryPlanner<br/>model-planned, template fallback"]
        CONN["Connectors<br/>Bing web · Bing News · Google News · Wikipedia<br/>OpenAlex · Crossref · arXiv · Europe PMC<br/>Hacker News · Stack Exchange · Groq agentic"]
        FETCH["PageFetcher<br/>direct → reader → snippet<br/><i>robots.txt honoured</i>"]
        EXTRACT["ContentExtractor + ClaimExtractor<br/>article text → claims with quotes"]
        VERIFY["QuoteVerifier<br/><b>locates every quote in the stored text</b>"]
        SCORE["CredibilityScorer + CorroborationAnalyzer<br/>explainable score · independent domains only"]
        QP --> CONN --> FETCH --> EXTRACT --> VERIFY --> SCORE
    end

    subgraph MODEL["MODEL LAYER — one seam to every provider"]
        direction TB
        GW["AiGateway<br/><i>route → cache → budget → call</i>"]
        MR["ModelRouter<br/>per-task candidates"]
        TB["TokenBudgetGovernor<br/>per-model + org-wide buckets<br/><i>synced from rate-limit headers</i>"]
        CACHE["AiResponseCache<br/><i>in Postgres, survives restart</i>"]
        AP["AiProvider <i>(interface)</i>"]
        GW --> MR & TB & CACHE --> AP
    end

    subgraph DATA["DATA & KNOWLEDGE LAYER — PostgreSQL (Neon free tier)"]
        direction LR
        CUR[("CURRENT<br/>process · activity · problem<br/>role · system_tool")]
        TRANS[("TRANSITION<br/>ai_opportunity · opportunity_score<br/>ai_opportunity_claim")]
        FUT[("FUTURE<br/>future_activity · ai_intervention<br/>impact_estimate · risk_item · roadmap_item")]
        EVID[("EVIDENCE<br/>research_run · research_source<br/>evidence_claim · claim_relation<br/>web_document · knowledge_snippet")]
        AUDIT[("AUDIT<br/>analysis_run · analysis_stage<br/>analysis_scorecard · ai_cache")]
    end

    UI -->|"REST / JSON + Bearer token"| SEC
    AC -.->|"Server-Sent Events"| CONSOLE
    SEC --> API
    PC --> ACC
    AC --> ACC
    PC --> DATA
    EC --> EVID
    SC --> MODEL
    AC --> PIPE

    P3 --> RESEARCH
    RESEARCH --> EVID
    PIPE --> MODEL
    RESEARCH --> MODEL
    CONN -->|"HTTPS, keyless"| WEB(["Public search APIs<br/>and the pages they point at"])
    AP --> GROQ(["Groq Cloud — primary<br/>gpt-oss-120b · gpt-oss-20b · qwen3 · compound"])
    AP -.->|"when Groq refuses, and for<br/>the stages routed to it"| GEMINI(["Google AI Studio<br/>gemini-3.1-flash-lite"])
    PIPE --> DATA
    P10 --> AUDIT

    classDef ui fill:#eef2ff,stroke:#6366f1,color:#1e1b4b
    classDef api fill:#ecfeff,stroke:#0891b2,color:#083344
    classDef pipe fill:#f0fdf4,stroke:#16a34a,color:#052e16
    classDef research fill:#fff7ed,stroke:#ea580c,color:#431407
    classDef model fill:#faf5ff,stroke:#9333ea,color:#3b0764
    classDef data fill:#fefce8,stroke:#ca8a04,color:#422006
    classDef ext fill:#fdf2f8,stroke:#db2777,color:#500724

    class DASH,NEW,DETAIL,CONSOLE,SYS ui
    class PC,AC,SC,AC2,SEC,ACC,EC,GEH api
    class P1,P2,P3,P4,P5,P6,P7,P8,P9,P10 pipe
    class QP,CONN,FETCH,EXTRACT,VERIFY,SCORE research
    class GW,MR,TB,CACHE,AP model
    class CUR,TRANS,FUT,EVID,AUDIT data
    class GEMINI,GROQ,WEB ext
```

## The analysis pipeline, stage by stage

`POST /api/processes/{id}/analyze` — or `/analyze/stream`, which is the same run reporting itself —
is the only entry point, and it runs the same ten stages for every process. Seed data and a process
created seconds ago by a reviewer take an identical path; there is no branch anywhere on which
process is being analysed.

| # | Stage | Model | What it contributes |
|---|---|---|---|
| 1 | Intake | none | Counts activities, roles and systems, and names the gaps in them |
| 2 | Diagnosis | GPT-OSS 120B | Problems, each with a root cause distinct from its symptom |
| 3 | Research | eleven connectors | Sources found, read, quoted and cross-checked. Deliberately **after** the diagnosis: knowing the real problem produces far better queries |
| 4 | Opportunities | GPT-OSS 120B | Interventions that cite the evidence by number |
| 5 | Critique | Qwen3 27B | A different model family judging the citations rather than the prose |
| 6 | Future design | GPT-OSS 120B | The ordered future process, with a failure mode per AI step |
| 7 | Quantification | Gemini / GPT-OSS | Four operational inputs; the arithmetic happens in Java |
| 8 | Risks | Qwen3 27B | The register, with controls, owners and any obligation the research established |
| 9 | Roadmap | Gemini / GPT-OSS | Delivery waves, dependencies, and the enabling work |
| 10 | Scorecard | none | Six ratios over the rows the run actually produced |

Only stages 2 and 4/6 are load-bearing. The rest degrade: a run that lost its roadmap is worth far
more to the person waiting than no run, and both the trace and the scorecard say what was lost.

```mermaid
sequenceDiagram
    autonumber
    participant U as Browser
    participant C as AnalysisController
    participant S as AnalysisService
    participant PL as StagedAnalysisPipeline
    participant DB as PostgreSQL
    participant RO as ResearchOrchestrator
    participant WEB as Public search APIs<br/>and the pages themselves
    participant QV as QuoteVerifier
    participant GW as AiGateway<br/>(router · cache · budget)
    participant W as AnalysisPersistenceService

    U->>C: POST /api/processes/{id}/analyze/stream
    C->>S: analyze(processId, sseSink)
    S->>S: rate-limit check · reject if already running
    S->>DB: load process, activities, roles, systems, recorded problems
    S->>DB: INSERT analysis_run (RUNNING)

    Note over PL: Stage 1 — intake, no model
    PL->>DB: INSERT analysis_stage (RUNNING)
    PL-->>U: SSE: activities, roles, systems, and the gaps in them

    Note over PL,GW: Stage 2 — diagnosis
    PL->>GW: complete(DIAGNOSIS, prompt)
    GW->>GW: route → check cache → reserve token budget (wait if needed)
    GW-->>PL: problems with root causes
    PL->>DB: INSERT analysis_stage (prompt, response, model, tokens)

    Note over PL,WEB: Stage 3 — live research
    PL->>RO: run(process, activities, problems)
    RO->>GW: plan searches (falls back to templates if unavailable)
    RO->>WEB: eleven connectors, in parallel, per query
    WEB-->>RO: results
    RO->>DB: INSERT research_run, research_query, research_source
    RO->>WEB: fetch the best pages (robots.txt honoured)
    alt publisher refuses a direct request
        RO->>WEB: retry through a text reader
    else still unreadable
        RO->>RO: keep the source with its search snippet, marked not read
    end
    RO->>DB: INSERT web_document (cached across runs)
    RO->>GW: extract claims with verbatim quotes
    RO->>QV: locate each quote in the stored page text
    QV-->>RO: verified / unverified + match ratio + offset
    RO->>RO: score credibility, cross-check claims across domains
    RO->>DB: INSERT evidence_claim, claim_relation
    RO-->>U: SSE: each query, each source, each quote checked

    Note over PL,GW: Stages 4-9 — one model call each
    loop opportunities · critique · future design · quantification · risks · roadmap
        PL->>GW: complete(task, prompt with numbered evidence)
        alt no budget on any candidate model
            GW-->>PL: refusal with the reason
            PL->>PL: required stage stops the run<br/>otherwise record DEGRADED and carry on
        else answered
            GW-->>PL: response
            PL->>PL: parse, validate, one repair retry if unusable
        end
        PL->>DB: INSERT analysis_stage
        PL-->>U: SSE: stage summary, model used, tokens, warnings
    end

    Note over PL: Stage 10 — scorecard, no model
    PL->>PL: coverage · grounding · corroboration · agreement · specificity · traceability

    S->>W: replaceAnalysis(analysis, citations, scorecard)
    W->>W: resolve citations to stored claims<br/>drop any number the model was never shown
    W->>W: compute grounding from quote-verified claims only
    W->>W: compute hours and rupees from the model's four inputs
    W->>DB: DELETE previous AI rows, INSERT new rows with resolved FKs
    S->>DB: UPDATE analysis_run (SUCCEEDED, totals, warnings)
    S-->>U: SSE 'result': the full structured analysis
```

## Why these boundaries

| Boundary | Reason |
|---|---|
| `AiProvider` interface, with a gateway in front of it | The model call is the piece most likely to change — pricing, quotas, retirement. It has now changed twice: Gemini's free tier ran out mid-testing, and Groq's default model was decommissioned outright. Both were configuration changes. The pipeline is unaware that routing, caching, budgeting or failover exist. |
| Routing, caching and budgeting in the gateway, not the stages | Ten stages would otherwise each need to know about token buckets and cache keys. Concentrating it means a stage asks for a *task* to be done and the gateway decides where, whether the answer is already known, and whether there is budget to ask right now. |
| Each connector its own class, behind one interface | Eleven third parties fail independently and differently. A connector that cannot answer returns an empty list rather than throwing, so one being blocked degrades a run instead of ending it — and adding a twelfth is one class. |
| Quote verification separate from claim extraction | The model does the part it is good at (recognising which sentences carry a finding) and is given no opportunity to do the part it is bad at (being the witness). The check is ordinary string matching in its own class, unit-tested against fabricated quotes. |
| Impact arithmetic separate from the stage that gathers its inputs | The model supplies volume, handling time, automation share and cost; the multiplication happens in `ImpactCalculator` where it is deterministic and checkable by hand. |
| Retrieval separate from prompting | Retrieval is deterministic and testable on its own; the prompt is text in a resource file. Neither needs the other to change. |
| Parsing and validation separate from persistence | Nothing untrusted reaches the database. The validator emits a normalised structure, and persistence only ever writes that. |
| Persistence in its own transactional bean | The model call takes tens of seconds and must not hold a database connection open — a real constraint on a serverless Postgres with a small connection allowance. |
| Ownership decided in one service | Every route touching a single process calls `ProcessAccessService`, so read rules and write rules cannot drift apart between endpoints. It returns 404 rather than 403 for someone else's process, because a 403 would confirm the id exists. |
| Stateless JWT rather than a session cookie | The frontend and API are on different sites (Vercel and Render). A session cookie would have to be `SameSite=None` third-party, which browsers increasingly block; a bearer token is unaffected. |
| Audit trail as first-class tables | "Not hard-coded" is a claim; ten `analysis_stage` rows holding the exact prompt and the exact response make it checkable. |
| Progress as an interface, with a no-op default | The streamed run and the plain run are the same run. Branching on whether anyone is watching would mean two code paths, one of which is only exercised in a demo. |

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
