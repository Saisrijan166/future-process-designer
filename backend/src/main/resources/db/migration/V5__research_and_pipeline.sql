-- =====================================================================
-- V5 — live research, staged analysis, and the numbers that come out of it.
--
-- Why this migration exists
-- -------------------------
-- V1 grounded the analysis in a hand-curated table of research snippets. That
-- was honest but static: the same fifteen excerpts grounded every process in
-- every industry, and "traceable" meant "we can tell you which of our fifteen
-- paragraphs we showed the model".
--
-- This migration replaces that with real retrieval. For each analysis the
-- application plans search queries, runs them across free public search APIs,
-- fetches the pages it finds, extracts atomic claims with a VERBATIM QUOTE, and
-- then verifies programmatically that the quote actually occurs in the fetched
-- text before the claim is allowed to ground anything. A claim whose quote
-- cannot be found is kept and marked unverified rather than silently dropped —
-- the distinction is visible in the UI, because a citation you cannot check is
-- worse than no citation.
--
-- Structure of what follows:
--   1. research_run / research_query / research_source / web_document
--        - what was searched, what was found, what was actually read
--   2. evidence_claim / claim_relation
--        - the atomic, quoted, cross-checked units of evidence
--   3. analysis_stage
--        - one row per pipeline stage, so a run is a readable audit trail
--          rather than one opaque prompt and one opaque response
--   4. opportunity_score / impact_estimate / risk_item / roadmap_item /
--      analysis_scorecard
--        - the analysis output that used to be missing entirely: an adversarial
--          review of each idea, its quantified effect, its risks, its delivery
--          sequence, and a measured quality score for the run as a whole
--   5. ai_cache
--        - remembered model responses, so a restart does not throw away quota
--          that has already been spent
--
-- Nothing here is per-industry or per-seed-record. Every table is populated by
-- the same code path for any process, including one created seconds ago.
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. RESEARCH: what was searched, found and read
-- ---------------------------------------------------------------------

CREATE TABLE research_run (
    id                     UUID        PRIMARY KEY,
    process_id             UUID        NOT NULL REFERENCES process (id) ON DELETE CASCADE,
    analysis_run_id        UUID        REFERENCES analysis_run (id) ON DELETE SET NULL,
    status                 VARCHAR(15) NOT NULL,
    connectors_used        VARCHAR(500),
    query_count            INTEGER     NOT NULL DEFAULT 0,
    hit_count              INTEGER     NOT NULL DEFAULT 0,
    document_count         INTEGER     NOT NULL DEFAULT 0,
    claim_count            INTEGER     NOT NULL DEFAULT 0,
    verified_claim_count   INTEGER     NOT NULL DEFAULT 0,
    contradiction_count    INTEGER     NOT NULL DEFAULT 0,
    distinct_domain_count  INTEGER     NOT NULL DEFAULT 0,
    cache_hit_count        INTEGER     NOT NULL DEFAULT 0,
    duration_ms            BIGINT,
    error_message          TEXT,
    notes                  TEXT,
    started_at             TIMESTAMPTZ NOT NULL,
    finished_at            TIMESTAMPTZ,
    CONSTRAINT ck_research_run_status CHECK (status IN ('RUNNING', 'SUCCEEDED', 'PARTIAL', 'FAILED', 'SKIPPED'))
);

CREATE INDEX idx_research_run_process ON research_run (process_id, started_at DESC);
CREATE INDEX idx_research_run_analysis ON research_run (analysis_run_id);

-- One planned search. Intent is recorded because a run that only ever asked
-- "what is X" has not researched the regulatory or benchmark angle at all, and
-- the scorecard penalises exactly that.
CREATE TABLE research_query (
    id                UUID         PRIMARY KEY,
    research_run_id   UUID         NOT NULL REFERENCES research_run (id) ON DELETE CASCADE,
    query_text        VARCHAR(500) NOT NULL,
    intent            VARCHAR(24)  NOT NULL,
    origin            VARCHAR(12)  NOT NULL,
    display_order     INTEGER      NOT NULL DEFAULT 0,
    hit_count         INTEGER      NOT NULL DEFAULT 0,
    duration_ms       BIGINT,
    created_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_research_query_intent CHECK (intent IN (
        'DOMAIN_BASELINE', 'PAIN_POINT', 'AI_CAPABILITY', 'REGULATION',
        'BENCHMARK', 'VENDOR_LANDSCAPE', 'RISK', 'CASE_STUDY')),
    CONSTRAINT ck_research_query_origin CHECK (origin IN ('MODEL', 'TEMPLATE'))
);

CREATE INDEX idx_research_query_run ON research_query (research_run_id, display_order);

-- The page body, cached across runs and keyed by URL hash rather than URL so the
-- index stays small and the 600-character URL column never has to be unique.
-- Two analyses that both cite the same standards page fetch it once.
CREATE TABLE web_document (
    id             UUID         PRIMARY KEY,
    url_hash       VARCHAR(64)  NOT NULL,
    url            VARCHAR(1000) NOT NULL,
    canonical_url  VARCHAR(1000),
    domain         VARCHAR(253) NOT NULL,
    title          VARCHAR(500),
    author         VARCHAR(250),
    published_at   DATE,
    content_text   TEXT         NOT NULL,
    content_chars  INTEGER      NOT NULL,
    content_hash   VARCHAR(64)  NOT NULL,
    http_status    INTEGER,
    fetch_method   VARCHAR(20)  NOT NULL,
    language       VARCHAR(12),
    fetched_at     TIMESTAMPTZ  NOT NULL,
    expires_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_web_document_url_hash UNIQUE (url_hash),
    CONSTRAINT ck_web_document_fetch_method CHECK (fetch_method IN (
        'DIRECT', 'READER', 'SEARCH_SNIPPET', 'AGENT_TOOL', 'API'))
);

CREATE INDEX idx_web_document_domain ON web_document (domain);
CREATE INDEX idx_web_document_expires ON web_document (expires_at);

-- A discovered source, scoped to the run that discovered it. The credibility
-- breakdown is stored as JSON text alongside the score, because a number nobody
-- can interrogate is not evidence of anything.
CREATE TABLE research_source (
    id                     UUID          PRIMARY KEY,
    research_run_id        UUID          NOT NULL REFERENCES research_run (id) ON DELETE CASCADE,
    research_query_id      UUID          REFERENCES research_query (id) ON DELETE SET NULL,
    web_document_id        UUID          REFERENCES web_document (id) ON DELETE SET NULL,
    connector_id           VARCHAR(30)   NOT NULL,
    url                    VARCHAR(1000) NOT NULL,
    domain                 VARCHAR(253)  NOT NULL,
    title                  VARCHAR(500)  NOT NULL,
    snippet                TEXT,
    publisher              VARCHAR(250),
    published_at           DATE,
    source_type            VARCHAR(20)   NOT NULL,
    native_rank            INTEGER,
    relevance_score        DOUBLE PRECISION NOT NULL DEFAULT 0,
    credibility_score      INTEGER       NOT NULL DEFAULT 0,
    credibility_breakdown  TEXT,
    fetch_status           VARCHAR(20)   NOT NULL,
    http_status            INTEGER,
    content_chars          INTEGER       NOT NULL DEFAULT 0,
    claim_count            INTEGER       NOT NULL DEFAULT 0,
    display_order          INTEGER       NOT NULL DEFAULT 0,
    fetched_at             TIMESTAMPTZ,
    created_at             TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_research_source_type CHECK (source_type IN (
        'LAW', 'GUIDANCE', 'STANDARD', 'RESEARCH', 'VENDOR', 'NEWS',
        'ENCYCLOPEDIA', 'PRACTITIONER', 'GENERAL_WEB')),
    CONSTRAINT ck_research_source_fetch_status CHECK (fetch_status IN (
        'PENDING', 'FETCHED', 'READER_FALLBACK', 'SNIPPET_ONLY', 'BLOCKED', 'FAILED', 'SKIPPED'))
);

CREATE INDEX idx_research_source_run ON research_source (research_run_id, display_order);
CREATE INDEX idx_research_source_domain ON research_source (research_run_id, domain);

-- ---------------------------------------------------------------------
-- 2. EVIDENCE: atomic claims, each with a quote that was checked
-- ---------------------------------------------------------------------

CREATE TABLE evidence_claim (
    id                    UUID        PRIMARY KEY,
    research_run_id       UUID        NOT NULL REFERENCES research_run (id) ON DELETE CASCADE,
    research_source_id    UUID        NOT NULL REFERENCES research_source (id) ON DELETE CASCADE,
    claim_text            TEXT        NOT NULL,
    quote                 TEXT        NOT NULL,
    -- Set by comparing the model's quote against the fetched page text, not by
    -- asking the model whether it was telling the truth.
    quote_verified        BOOLEAN     NOT NULL DEFAULT FALSE,
    quote_match_ratio     DOUBLE PRECISION NOT NULL DEFAULT 0,
    quote_start           INTEGER,
    claim_type            VARCHAR(16) NOT NULL,
    topic                 VARCHAR(120),
    numeric_value         DOUBLE PRECISION,
    numeric_unit          VARCHAR(40),
    as_of_date            DATE,
    confidence            DOUBLE PRECISION NOT NULL DEFAULT 0,
    corroboration_count   INTEGER     NOT NULL DEFAULT 0,
    contradiction_count   INTEGER     NOT NULL DEFAULT 0,
    citation_index        INTEGER     NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_evidence_claim_type CHECK (claim_type IN (
        'STATISTIC', 'REGULATION', 'CAPABILITY', 'RISK', 'PRACTICE', 'BENCHMARK', 'DEFINITION', 'OPINION'))
);

CREATE INDEX idx_evidence_claim_run ON evidence_claim (research_run_id, citation_index);
CREATE INDEX idx_evidence_claim_source ON evidence_claim (research_source_id);

-- Two claims that say the same thing (from different domains, which is the only
-- kind that counts) or that disagree. Contradictions are surfaced rather than
-- resolved: the honest output is "these two sources disagree", not a coin toss.
CREATE TABLE claim_relation (
    id              UUID             PRIMARY KEY,
    claim_a_id      UUID             NOT NULL REFERENCES evidence_claim (id) ON DELETE CASCADE,
    claim_b_id      UUID             NOT NULL REFERENCES evidence_claim (id) ON DELETE CASCADE,
    relation_type   VARCHAR(14)      NOT NULL,
    similarity      DOUBLE PRECISION NOT NULL,
    same_domain     BOOLEAN          NOT NULL DEFAULT FALSE,
    note            TEXT,
    created_at      TIMESTAMPTZ      NOT NULL,
    CONSTRAINT ck_claim_relation_type CHECK (relation_type IN ('CORROBORATES', 'CONTRADICTS')),
    CONSTRAINT ck_claim_relation_distinct CHECK (claim_a_id <> claim_b_id)
);

CREATE INDEX idx_claim_relation_a ON claim_relation (claim_a_id);
CREATE INDEX idx_claim_relation_b ON claim_relation (claim_b_id);

-- The citation edge that makes an opportunity checkable: which quoted claims
-- does this recommendation actually rest on?
CREATE TABLE ai_opportunity_claim (
    ai_opportunity_id  UUID NOT NULL REFERENCES ai_opportunity (id) ON DELETE CASCADE,
    evidence_claim_id  UUID NOT NULL REFERENCES evidence_claim (id) ON DELETE CASCADE,
    PRIMARY KEY (ai_opportunity_id, evidence_claim_id)
);

-- ---------------------------------------------------------------------
-- 3. THE PIPELINE ITSELF: one row per stage per run
-- ---------------------------------------------------------------------

CREATE TABLE analysis_stage (
    id               UUID        PRIMARY KEY,
    analysis_run_id  UUID        NOT NULL REFERENCES analysis_run (id) ON DELETE CASCADE,
    stage_id         VARCHAR(40) NOT NULL,
    title            VARCHAR(120) NOT NULL,
    status           VARCHAR(12) NOT NULL,
    display_order    INTEGER     NOT NULL,
    provider         VARCHAR(60),
    model            VARCHAR(120),
    prompt_tokens    INTEGER,
    output_tokens    INTEGER,
    duration_ms      BIGINT,
    waited_ms        BIGINT,
    cached           BOOLEAN     NOT NULL DEFAULT FALSE,
    attempt_count    INTEGER     NOT NULL DEFAULT 1,
    summary          TEXT,
    prompt_text      TEXT,
    response_text    TEXT,
    error_message    TEXT,
    notes            TEXT,
    started_at       TIMESTAMPTZ NOT NULL,
    finished_at      TIMESTAMPTZ,
    CONSTRAINT ck_analysis_stage_status CHECK (status IN ('RUNNING', 'SUCCEEDED', 'DEGRADED', 'SKIPPED', 'FAILED'))
);

CREATE INDEX idx_analysis_stage_run ON analysis_stage (analysis_run_id, display_order);

-- ---------------------------------------------------------------------
-- 4. THE OUTPUT THAT USED TO BE MISSING
-- ---------------------------------------------------------------------

-- A second model, from a different family, marking the first one's homework.
-- Stored per opportunity because "the reviewer disagreed with this one" is the
-- single most useful thing a reader can know about a generated recommendation.
CREATE TABLE opportunity_score (
    ai_opportunity_id     UUID        PRIMARY KEY REFERENCES ai_opportunity (id) ON DELETE CASCADE,
    feasibility           SMALLINT    NOT NULL,
    evidence_strength     SMALLINT    NOT NULL,
    business_impact       SMALLINT    NOT NULL,
    risk_level            SMALLINT    NOT NULL,
    implementation_effort SMALLINT    NOT NULL,
    confidence            DOUBLE PRECISION NOT NULL,
    verdict               VARCHAR(16) NOT NULL,
    critique              TEXT,
    reviewer_provider     VARCHAR(60),
    reviewer_model        VARCHAR(120),
    grounded_claim_count  INTEGER     NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_opportunity_score_verdict CHECK (verdict IN ('STRONG', 'SOUND', 'QUALIFIED', 'WEAK', 'REJECTED')),
    CONSTRAINT ck_opportunity_score_ranges CHECK (
        feasibility BETWEEN 0 AND 5 AND evidence_strength BETWEEN 0 AND 5
        AND business_impact BETWEEN 0 AND 5 AND risk_level BETWEEN 0 AND 5
        AND implementation_effort BETWEEN 0 AND 5)
);

-- The impact model. Inputs are stored next to outputs on purpose: a saving of
-- "1,240 hours a month" means nothing without the volume and handling time it
-- was derived from, and those are estimates that a user is entitled to correct.
CREATE TABLE impact_estimate (
    id                        UUID        PRIMARY KEY,
    process_id                UUID        NOT NULL REFERENCES process (id) ON DELETE CASCADE,
    ai_opportunity_id         UUID        REFERENCES ai_opportunity (id) ON DELETE CASCADE,
    activity_id               UUID        REFERENCES activity (id) ON DELETE SET NULL,
    label                     VARCHAR(250) NOT NULL,
    volume_per_month          DOUBLE PRECISION NOT NULL DEFAULT 0,
    minutes_per_item          DOUBLE PRECISION NOT NULL DEFAULT 0,
    automation_share          DOUBLE PRECISION NOT NULL DEFAULT 0,
    hourly_cost_inr           DOUBLE PRECISION NOT NULL DEFAULT 0,
    hours_saved_per_month     DOUBLE PRECISION NOT NULL DEFAULT 0,
    cost_saved_per_month_inr  DOUBLE PRECISION NOT NULL DEFAULT 0,
    error_reduction_percent   DOUBLE PRECISION,
    one_off_effort_days       DOUBLE PRECISION,
    run_cost_per_month_inr    DOUBLE PRECISION,
    payback_months            DOUBLE PRECISION,
    basis                     VARCHAR(16) NOT NULL,
    assumptions               TEXT,
    display_order             INTEGER     NOT NULL DEFAULT 0,
    created_at                TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_impact_estimate_basis CHECK (basis IN ('MODEL_ESTIMATE', 'USER_SUPPLIED', 'DERIVED', 'BENCHMARK')),
    CONSTRAINT ck_impact_estimate_share CHECK (automation_share BETWEEN 0 AND 1)
);

CREATE INDEX idx_impact_estimate_process ON impact_estimate (process_id, display_order);
CREATE INDEX idx_impact_estimate_opportunity ON impact_estimate (ai_opportunity_id);

CREATE TABLE risk_item (
    id                 UUID        PRIMARY KEY,
    process_id         UUID        NOT NULL REFERENCES process (id) ON DELETE CASCADE,
    ai_opportunity_id  UUID        REFERENCES ai_opportunity (id) ON DELETE SET NULL,
    title              VARCHAR(250) NOT NULL,
    description        TEXT        NOT NULL,
    category           VARCHAR(16) NOT NULL,
    likelihood         SMALLINT    NOT NULL,
    impact             SMALLINT    NOT NULL,
    severity_score     INTEGER     NOT NULL,
    mitigation         TEXT,
    owner_role         VARCHAR(150),
    obligation         VARCHAR(400),
    display_order      INTEGER     NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_risk_item_category CHECK (category IN (
        'PRIVACY', 'BIAS', 'ACCURACY', 'SECURITY', 'COMPLIANCE',
        'OPERATIONAL', 'CHANGE', 'VENDOR', 'TRANSPARENCY')),
    CONSTRAINT ck_risk_item_ranges CHECK (likelihood BETWEEN 1 AND 5 AND impact BETWEEN 1 AND 5)
);

CREATE INDEX idx_risk_item_process ON risk_item (process_id, display_order);

CREATE TABLE risk_item_claim (
    risk_item_id       UUID NOT NULL REFERENCES risk_item (id) ON DELETE CASCADE,
    evidence_claim_id  UUID NOT NULL REFERENCES evidence_claim (id) ON DELETE CASCADE,
    PRIMARY KEY (risk_item_id, evidence_claim_id)
);

CREATE TABLE roadmap_item (
    id                 UUID         PRIMARY KEY,
    process_id         UUID         NOT NULL REFERENCES process (id) ON DELETE CASCADE,
    ai_opportunity_id  UUID         REFERENCES ai_opportunity (id) ON DELETE SET NULL,
    wave               SMALLINT     NOT NULL,
    title              VARCHAR(250) NOT NULL,
    description        TEXT,
    effort             VARCHAR(10)  NOT NULL,
    impact             VARCHAR(10)  NOT NULL,
    duration_weeks     INTEGER,
    depends_on         VARCHAR(500),
    success_metric     VARCHAR(400),
    display_order      INTEGER      NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_roadmap_item_effort CHECK (effort IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_roadmap_item_impact CHECK (impact IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_roadmap_item_wave CHECK (wave BETWEEN 1 AND 6)
);

CREATE INDEX idx_roadmap_item_process ON roadmap_item (process_id, wave, display_order);

-- How good was this run? Computed from the run's own data — what fraction of
-- activities were addressed, what fraction of citations verified, how many
-- independent domains agreed, how often the reviewer model disagreed. Recorded
-- so the answer to "is this any good?" is a measurement rather than a vibe.
CREATE TABLE analysis_scorecard (
    analysis_run_id      UUID        PRIMARY KEY REFERENCES analysis_run (id) ON DELETE CASCADE,
    process_id           UUID        NOT NULL REFERENCES process (id) ON DELETE CASCADE,
    coverage_score       INTEGER     NOT NULL,
    grounding_score      INTEGER     NOT NULL,
    corroboration_score  INTEGER     NOT NULL,
    agreement_score      INTEGER     NOT NULL,
    specificity_score    INTEGER     NOT NULL,
    traceability_score   INTEGER     NOT NULL,
    overall_score        INTEGER     NOT NULL,
    grade                VARCHAR(2)  NOT NULL,
    metrics              TEXT,
    created_at           TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_analysis_scorecard_process ON analysis_scorecard (process_id, created_at DESC);

-- ---------------------------------------------------------------------
-- 5. REMEMBERED MODEL RESPONSES
-- ---------------------------------------------------------------------

CREATE TABLE ai_cache (
    cache_key       VARCHAR(64)  PRIMARY KEY,
    task            VARCHAR(40)  NOT NULL,
    provider        VARCHAR(40)  NOT NULL,
    model           VARCHAR(120) NOT NULL,
    response_text   TEXT         NOT NULL,
    prompt_tokens   INTEGER,
    output_tokens   INTEGER,
    finish_reason   VARCHAR(40),
    executed_tools  TEXT,
    created_at      TIMESTAMPTZ  NOT NULL,
    hit_count       INTEGER      NOT NULL DEFAULT 0,
    last_hit_at     TIMESTAMPTZ
);

CREATE INDEX idx_ai_cache_created ON ai_cache (created_at);

-- ---------------------------------------------------------------------
-- 6. Additions to existing tables
-- ---------------------------------------------------------------------

-- The staged pipeline records its shape on the run: which pipeline version ran,
-- how much the whole thing cost, and how much of that was served from cache.
ALTER TABLE analysis_run ADD COLUMN pipeline_version   VARCHAR(20);
ALTER TABLE analysis_run ADD COLUMN stage_count        INTEGER NOT NULL DEFAULT 0;
ALTER TABLE analysis_run ADD COLUMN total_prompt_tokens INTEGER NOT NULL DEFAULT 0;
ALTER TABLE analysis_run ADD COLUMN total_output_tokens INTEGER NOT NULL DEFAULT 0;
ALTER TABLE analysis_run ADD COLUMN cache_hit_count    INTEGER NOT NULL DEFAULT 0;
ALTER TABLE analysis_run ADD COLUMN throttled_ms       BIGINT  NOT NULL DEFAULT 0;
ALTER TABLE analysis_run ADD COLUMN research_run_id    UUID REFERENCES research_run (id) ON DELETE SET NULL;

-- An opportunity now carries the reasoning chain that produced it, and a
-- grounding score derived from the claims it cites.
ALTER TABLE ai_opportunity ADD COLUMN root_cause        TEXT;
ALTER TABLE ai_opportunity ADD COLUMN human_oversight   TEXT;
ALTER TABLE ai_opportunity ADD COLUMN data_requirement  TEXT;
ALTER TABLE ai_opportunity ADD COLUMN success_metric    VARCHAR(400);
ALTER TABLE ai_opportunity ADD COLUMN grounding_score   INTEGER NOT NULL DEFAULT 0;

-- Future activities gain the operational detail a reader needs to believe them.
ALTER TABLE future_activity ADD COLUMN handoff_note      TEXT;
ALTER TABLE future_activity ADD COLUMN failure_mode      TEXT;
ALTER TABLE future_activity ADD COLUMN replaces_activity VARCHAR(250);
ALTER TABLE future_activity ADD COLUMN cycle_time_note   VARCHAR(400);

-- A problem can now name its own root cause and where the observation came from.
ALTER TABLE problem ADD COLUMN root_cause     TEXT;
ALTER TABLE problem ADD COLUMN evidence_note  TEXT;

COMMENT ON TABLE  evidence_claim IS
    'Atomic research claims. quote_verified is set by locating the quote in the fetched page text, not by trusting the model.';
COMMENT ON TABLE  analysis_stage IS
    'One row per pipeline stage per run: the audit trail that replaces a single opaque prompt.';
COMMENT ON TABLE  analysis_scorecard IS
    'Measured quality of one run, computed from its own evidence and coverage rather than asserted.';
