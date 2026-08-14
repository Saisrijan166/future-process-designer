/**
 * Mirrors the backend DTOs in `com.assesswise.processdesigner.dto`.
 * Kept hand-written and small rather than generated, so the API contract stays readable.
 */

export type ProcessStatus = "CURRENT_ONLY" | "ANALYZED";
export type ProcessOrigin = "SEED" | "USER";
export type Severity = "LOW" | "MEDIUM" | "HIGH";
export type ProblemSource = "SEED" | "AI_GENERATED";
export type AutomationPotential = "LOW" | "MEDIUM" | "HIGH";
export type ResponsibilityType = "AI_AUTOMATED" | "AI_AUGMENTED" | "HUMAN_LED";
export type InterventionType = "AUTOMATE" | "AUGMENT" | "ELIMINATE" | "NEW";
export type SourceType = "LAW" | "GUIDANCE" | "STANDARD" | "RESEARCH" | "VENDOR" | "GENERAL_WEB";
export type AnalysisRunStatus = "RUNNING" | "SUCCEEDED" | "FAILED";

export interface ProcessSummary {
  id: string;
  name: string;
  industry: string;
  description: string;
  status: ProcessStatus;
  origin: ProcessOrigin;
  activityCount: number;
  futureActivityCount: number;
  opportunityCount: number;
  createdAt: string;
  lastAnalyzedAt?: string | null;
}

export interface Problem {
  id: string;
  activityId?: string | null;
  activityName?: string | null;
  description: string;
  severity: Severity;
  source: ProblemSource;
}

export interface Activity {
  id: string;
  name: string;
  sequenceOrder: number;
  description?: string | null;
  roles: string[];
  systems: string[];
  problems: Problem[];
}

export interface KnowledgeSnippet {
  id: string;
  title: string;
  snippetText: string;
  sourceUrl: string;
  sourceType: SourceType;
  publisher?: string | null;
  tags: string[];
  retrievedAt: string;
}

export interface AiOpportunity {
  id: string;
  activityId?: string | null;
  activityName?: string | null;
  description: string;
  aiCapability: string;
  automationPotential: AutomationPotential;
  businessBenefit?: string | null;
  risk?: string | null;
  reasoningNote?: string | null;
  evidence: KnowledgeSnippet[];
}

export interface AiIntervention {
  id: string;
  futureActivityId?: string | null;
  futureActivityName?: string | null;
  relatedAiOpportunityId?: string | null;
  relatedAiOpportunitySummary?: string | null;
  interventionType: InterventionType;
  description: string;
}

export interface FutureActivity {
  id: string;
  name: string;
  sequenceOrder: number;
  description?: string | null;
  humanResponsibility?: string | null;
  aiResponsibility?: string | null;
  responsibilityType: ResponsibilityType;
  interventions: AiIntervention[];
}

export interface RetrievedSnippet {
  snippet: KnowledgeSnippet;
  relevanceScore: number;
  matchedTerms: string[];
}

export interface AnalysisRunSummary {
  id: string;
  status: AnalysisRunStatus;
  provider: string;
  model: string;
  repairAttempted: boolean;
  validationWarnings: string[];
  /** Providers skipped or failed before this run was served. Empty when the primary answered. */
  providerNotes: string[];
  errorMessage?: string | null;
  promptTokens?: number | null;
  outputTokens?: number | null;
  durationMs?: number | null;
  startedAt: string;
  finishedAt?: string | null;
  retrievedSnippets: RetrievedSnippet[];
}

export interface AnalysisRunTrace {
  run: AnalysisRunSummary;
  promptText?: string | null;
  rawResponse?: string | null;
}

export interface ProcessDetail {
  process: ProcessSummary;
  activities: Activity[];
  problems: Problem[];
  opportunities: AiOpportunity[];
  futureActivities: FutureActivity[];
  interventions: AiIntervention[];
  evidence: RetrievedSnippet[];
  latestRun?: AnalysisRunSummary | null;
}

export interface ComparisonSummary {
  currentActivityCount: number;
  futureActivityCount: number;
  problemCount: number;
  opportunityCount: number;
  interventionCount: number;
  evidenceCount: number;
  problemsBySeverity: Record<string, number>;
  opportunitiesByAutomationPotential: Record<string, number>;
  futureActivitiesByResponsibility: Record<string, number>;
  interventionsByType: Record<string, number>;
}

export interface Comparison {
  process: ProcessSummary;
  current: { activities: Activity[]; problems: Problem[]; roles: string[]; systems: string[] };
  transition: { opportunities: AiOpportunity[]; evidence: RetrievedSnippet[] };
  future: { activities: FutureActivity[]; interventions: AiIntervention[] };
  summary: ComparisonSummary;
  latestRun?: AnalysisRunSummary | null;
}

export interface AnalysisResult {
  processId: string;
  problemsGenerated: number;
  opportunitiesGenerated: number;
  futureActivitiesGenerated: number;
  interventionsGenerated: number;
  run: AnalysisRunSummary;
  detail: ProcessDetail;
}

export interface ActivityInput {
  name: string;
  description: string;
  roles: string[];
  systems: string[];
}

export interface CreateProcessRequest {
  name: string;
  industry: string;
  description: string;
  activities: ActivityInput[];
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
