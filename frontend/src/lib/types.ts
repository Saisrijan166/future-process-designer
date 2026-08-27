/**
 * The API contract, mirrored from the backend DTOs.
 *
 * Hand-written rather than generated, and deliberately so: the field comments here are the ones a
 * developer reads while building a component, and the two that matter most — whether a quote was
 * verified, and whether a number was estimated or supplied — are easy to render past without
 * noticing unless something says so at the point of use.
 */

export type ProcessStatus = "CURRENT_ONLY" | "ANALYZED";
export type ProcessOrigin = "SEED" | "USER";
export type Severity = "LOW" | "MEDIUM" | "HIGH";
export type ProblemSource = "SEED" | "AI_GENERATED";
export type AutomationPotential = "LOW" | "MEDIUM" | "HIGH";
export type ResponsibilityType = "AI_AUTOMATED" | "AI_AUGMENTED" | "HUMAN_LED";
export type InterventionType = "AUTOMATE" | "AUGMENT" | "ELIMINATE" | "NEW";
export type AnalysisRunStatus = "RUNNING" | "SUCCEEDED" | "FAILED";
export type StageStatus = "RUNNING" | "SUCCEEDED" | "DEGRADED" | "SKIPPED" | "FAILED";
export type OpportunityVerdict = "STRONG" | "SOUND" | "QUALIFIED" | "WEAK" | "REJECTED";
export type EstimateBasis = "MODEL_ESTIMATE" | "USER_SUPPLIED" | "DERIVED" | "BENCHMARK";
export type EffortLevel = "LOW" | "MEDIUM" | "HIGH";
export type ResearchRunStatus = "RUNNING" | "SUCCEEDED" | "PARTIAL" | "FAILED" | "SKIPPED";

export type SourceType =
  | "LAW"
  | "GUIDANCE"
  | "STANDARD"
  | "RESEARCH"
  | "VENDOR"
  | "NEWS"
  | "ENCYCLOPEDIA"
  | "PRACTITIONER"
  | "GENERAL_WEB";

export type ClaimType =
  | "STATISTIC"
  | "REGULATION"
  | "CAPABILITY"
  | "RISK"
  | "PRACTICE"
  | "BENCHMARK"
  | "DEFINITION"
  | "OPINION";

export type FetchStatus =
  | "PENDING"
  | "FETCHED"
  | "READER_FALLBACK"
  | "SNIPPET_ONLY"
  | "BLOCKED"
  | "FAILED"
  | "SKIPPED";

export type RiskCategory =
  | "PRIVACY"
  | "BIAS"
  | "ACCURACY"
  | "SECURITY"
  | "COMPLIANCE"
  | "OPERATIONAL"
  | "CHANGE"
  | "VENDOR"
  | "TRANSPARENCY";

export type QueryIntent =
  | "DOMAIN_BASELINE"
  | "PAIN_POINT"
  | "AI_CAPABILITY"
  | "REGULATION"
  | "BENCHMARK"
  | "VENDOR_LANDSCAPE"
  | "RISK"
  | "CASE_STUDY";

// ---------------------------------------------------------------------------
// Auth
// ---------------------------------------------------------------------------

export interface AuthUser {
  id: string;
  email: string;
  displayName: string;
  createdAt: string;
  lastLoginAt: string | null;
}

export interface AuthResponse {
  token: string;
  expiresAt: string;
  user: AuthUser;
}

// ---------------------------------------------------------------------------
// Current state
// ---------------------------------------------------------------------------

export interface ProcessSummary {
  id: string;
  name: string;
  industry: string;
  description: string;
  status: ProcessStatus;
  origin: ProcessOrigin;
  /** A shared sample: readable and analysable by everyone, editable by nobody. */
  shared: boolean;
  activityCount: number;
  futureActivityCount: number;
  opportunityCount: number;
  createdAt: string;
  lastAnalyzedAt: string | null;
}

export interface Activity {
  id: string;
  name: string;
  sequenceOrder: number;
  description: string | null;
  roles: string[];
  systems: string[];
  problems: Problem[];
}

export interface Problem {
  id: string;
  activityId: string | null;
  activityName: string | null;
  description: string;
  severity: Severity;
  source: ProblemSource;
  /** Why it happens, as distinct from what happens. Null when nothing supported one. */
  rootCause: string | null;
  evidenceNote: string | null;
}

// ---------------------------------------------------------------------------
// Evidence
// ---------------------------------------------------------------------------

export interface CredibilityComponent {
  label: string;
  points: number;
  note: string;
}

export interface ResearchSource {
  id: string;
  connectorId: string;
  url: string;
  domain: string;
  title: string;
  snippet: string | null;
  publisher: string | null;
  publishedAt: string | null;
  sourceType: SourceType;
  relevanceScore: number;
  credibilityScore: number;
  credibilityBreakdown: CredibilityComponent[];
  fetchStatus: FetchStatus;
  httpStatus: number | null;
  contentChars: number;
  claimCount: number;
  fetchedAt: string | null;
}

export interface EvidenceClaim {
  id: string;
  /** The number rendered as [3]. Stable within a research run. */
  citationIndex: number;
  claimText: string;
  quote: string;
  /**
   * Set by locating the quote in the stored page text. False means the citation cannot be
   * checked, and the interface must say so rather than render it like any other reference.
   */
  quoteVerified: boolean;
  quoteMatchRatio: number;
  quoteStart: number | null;
  claimType: ClaimType;
  topic: string | null;
  numericValue: number | null;
  numericUnit: string | null;
  asOfDate: string | null;
  confidence: number;
  corroborationCount: number;
  contradictionCount: number;
  source: ResearchSource;
}

export interface ResearchQuery {
  id: string;
  queryText: string;
  intent: QueryIntent;
  origin: "MODEL" | "TEMPLATE";
  hitCount: number;
  durationMs: number | null;
}

export interface ResearchRun {
  id: string;
  status: ResearchRunStatus;
  connectorsUsed: string[];
  queryCount: number;
  hitCount: number;
  documentCount: number;
  claimCount: number;
  verifiedClaimCount: number;
  contradictionCount: number;
  distinctDomainCount: number;
  cacheHitCount: number;
  durationMs: number | null;
  notes: string[];
  errorMessage: string | null;
  startedAt: string;
  finishedAt: string | null;
  queries: ResearchQuery[];
  sources: ResearchSource[];
  claims: EvidenceClaim[];
}

export interface ResearchSummary {
  id: string;
  status: string;
  sourceCount: number;
  claimCount: number;
  verifiedClaimCount: number;
  contradictionCount: number;
  distinctDomainCount: number;
  finishedAt: string | null;
}

/** The curated corpus, still used to ground a run whose live research found nothing. */
export interface KnowledgeSnippet {
  id: string;
  title: string;
  snippetText: string;
  sourceUrl: string;
  sourceType: SourceType;
  publisher: string | null;
  tags: string[];
  retrievedAt: string;
}

export interface RetrievedSnippet {
  snippet: KnowledgeSnippet;
  relevanceScore: number;
  matchedTerms: string[];
}

// ---------------------------------------------------------------------------
// Transition and future state
// ---------------------------------------------------------------------------

export interface OpportunityScore {
  feasibility: number;
  evidenceStrength: number;
  businessImpact: number;
  /** Higher is worse. */
  riskLevel: number;
  /** Higher is more work. */
  implementationEffort: number;
  confidence: number;
  verdict: OpportunityVerdict;
  critique: string | null;
  reviewerModel: string | null;
  groundedClaimCount: number;
}

export interface ImpactEstimate {
  id: string;
  opportunityId: string | null;
  activityId: string | null;
  label: string;
  volumePerMonth: number;
  minutesPerItem: number;
  automationShare: number;
  hourlyCostInr: number;
  hoursSavedPerMonth: number;
  costSavedPerMonthInr: number;
  errorReductionPercent: number | null;
  oneOffEffortDays: number | null;
  runCostPerMonthInr: number | null;
  paybackMonths: number | null;
  /** Whether a person supplied these inputs or a model estimated them. Never render the same. */
  basis: EstimateBasis;
  assumptions: string | null;
}

export interface AiOpportunity {
  id: string;
  activityId: string | null;
  activityName: string | null;
  description: string;
  aiCapability: string;
  automationPotential: AutomationPotential;
  businessBenefit: string | null;
  risk: string | null;
  reasoningNote: string | null;
  rootCause: string | null;
  humanOversight: string | null;
  dataRequirement: string | null;
  successMetric: string | null;
  /** 0-100, computed from quote-verified citations. Zero means nothing checkable backs it. */
  groundingScore: number;
  evidence: KnowledgeSnippet[];
  citedClaims: EvidenceClaim[];
  review: OpportunityScore | null;
  impact: ImpactEstimate | null;
}

export interface AiIntervention {
  id: string;
  futureActivityId: string | null;
  futureActivityName: string | null;
  relatedOpportunityId: string | null;
  relatedOpportunitySummary: string | null;
  interventionType: InterventionType;
  description: string;
}

export interface FutureActivity {
  id: string;
  name: string;
  sequenceOrder: number;
  description: string | null;
  humanResponsibility: string | null;
  aiResponsibility: string | null;
  responsibilityType: ResponsibilityType;
  handoffNote: string | null;
  /** What happens when the AI part is wrong. Required of every AI step. */
  failureMode: string | null;
  replacesActivity: string | null;
  cycleTimeNote: string | null;
  interventions: AiIntervention[];
}

export interface RiskItem {
  id: string;
  opportunityId: string | null;
  title: string;
  description: string;
  category: RiskCategory;
  likelihood: number;
  impact: number;
  severityScore: number;
  mitigation: string | null;
  ownerRole: string | null;
  /** Only present where the research established one; never asserted from memory. */
  obligation: string | null;
  citedClaims: EvidenceClaim[];
}

export interface RoadmapItem {
  id: string;
  opportunityId: string | null;
  wave: number;
  title: string;
  description: string | null;
  effort: EffortLevel;
  impact: EffortLevel;
  durationWeeks: number | null;
  dependsOn: string | null;
  successMetric: string | null;
}

// ---------------------------------------------------------------------------
// Runs
// ---------------------------------------------------------------------------

export interface Scorecard {
  analysisRunId: string;
  coverageScore: number;
  groundingScore: number;
  corroborationScore: number;
  agreementScore: number;
  specificityScore: number;
  traceabilityScore: number;
  overallScore: number;
  grade: string;
  metrics: Record<string, unknown>;
  createdAt: string;
}

export interface AnalysisRunSummary {
  id: string;
  status: AnalysisRunStatus;
  provider: string;
  model: string;
  repairAttempted: boolean;
  validationWarnings: string[];
  providerNotes: string[];
  errorMessage: string | null;
  promptTokens: number | null;
  outputTokens: number | null;
  durationMs: number | null;
  startedAt: string;
  finishedAt: string | null;
  retrievedSnippets: RetrievedSnippet[];
  pipelineVersion: string | null;
  stageCount: number;
  totalPromptTokens: number;
  totalOutputTokens: number;
  cacheHitCount: number;
  throttledMs: number;
  researchRunId: string | null;
  scorecard: Scorecard | null;
}

export interface AnalysisStage {
  id: string;
  stageId: string;
  title: string;
  status: StageStatus;
  displayOrder: number;
  provider: string | null;
  model: string | null;
  promptTokens: number | null;
  outputTokens: number | null;
  durationMs: number | null;
  waitedMs: number | null;
  cached: boolean;
  attemptCount: number;
  summary: string | null;
  promptText: string | null;
  responseText: string | null;
  errorMessage: string | null;
  notes: string[];
  startedAt: string;
  finishedAt: string | null;
}

export interface AnalysisRunTrace {
  run: AnalysisRunSummary;
  promptText: string | null;
  rawResponse: string | null;
  stages: AnalysisStage[];
}

export interface ProcessDetail {
  process: ProcessSummary;
  activities: Activity[];
  problems: Problem[];
  opportunities: AiOpportunity[];
  futureActivities: FutureActivity[];
  interventions: AiIntervention[];
  evidence: RetrievedSnippet[];
  latestRun: AnalysisRunSummary | null;
  impacts: ImpactEstimate[];
  risks: RiskItem[];
  roadmap: RoadmapItem[];
  scorecard: Scorecard | null;
  research: ResearchSummary | null;
}

export interface AnalysisResult {
  processId: string;
  problemsGenerated: number;
  opportunitiesGenerated: number;
  futureActivitiesGenerated: number;
  interventionsGenerated: number;
  reviewsGenerated: number;
  impactsGenerated: number;
  risksGenerated: number;
  roadmapItemsGenerated: number;
  citationsStored: number;
  warnings: string[];
  run: AnalysisRunSummary | null;
  detail: ProcessDetail;
}

// ---------------------------------------------------------------------------
// System
// ---------------------------------------------------------------------------

export interface AiStatus {
  configured: boolean;
  pipeline: string;
  providers: { name: string; defaultModel: string; configured: boolean }[];
  routing: Record<string, string[]>;
  budgets: {
    key: string;
    tokensPerMinute: number;
    requestsPerDay: number;
    remainingTokens: number;
    remainingRequests: number;
    throttledMillis: number;
    admitted: number;
    rejected: number;
    cooling: boolean;
  }[];
  researchConnectors: { id: string; displayName: string; sourceType: string; enabled: boolean }[];
  researchEnabled: boolean;
  cacheEnabled: boolean;
}

// ---------------------------------------------------------------------------
// Live progress
// ---------------------------------------------------------------------------

export type ProgressEventType =
  | "STAGE_STARTED"
  | "STAGE_FINISHED"
  | "STAGE_DEGRADED"
  | "STAGE_FAILED"
  | "QUERY_PLANNED"
  | "SEARCH_RESULT"
  | "SOURCE_FETCHED"
  | "CLAIMS_EXTRACTED"
  | "MODEL_CALL"
  | "RUN_FINISHED"
  | "NOTE";

export interface ProgressEvent {
  type: ProgressEventType;
  stageId: string | null;
  title: string | null;
  message: string | null;
  at: string;
  data: Record<string, unknown>;
}

// ---------------------------------------------------------------------------
// Requests
// ---------------------------------------------------------------------------

export interface ActivityInput {
  name: string;
  description?: string;
  roles?: string[];
  systems?: string[];
}

export interface CreateProcessRequest {
  name: string;
  industry: string;
  description: string;
  activities: ActivityInput[];
  problems?: { description: string; severity: Severity; activityName?: string }[];
}

export interface ProcessListQuery {
  page?: number;
  size?: number;
  status?: ProcessStatus;
  q?: string;
  sort?: "recent" | "oldest" | "name" | "analysed";
}

export interface ProcessPage {
  items: ProcessSummary[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
  hasPrevious: boolean;
  hasNext: boolean;
  stats: {
    processes: number;
    analysed: number;
    opportunities: number;
    futureActivities: number;
  };
}

export interface Comparison {
  process: ProcessSummary;
  current: { activities: Activity[]; problems: Problem[]; roles: string[]; systems: string[] };
  transition: { opportunities: AiOpportunity[]; evidence: RetrievedSnippet[] };
  future: { activities: FutureActivity[]; interventions: AiIntervention[] };
  summary: Record<string, number>;
  latestRun: AnalysisRunSummary | null;
}

export interface Role {
  id: string;
  name: string;
}

export interface SystemTool {
  id: string;
  name: string;
  type: string;
}
