"use client";

import { useMemo, useState } from "react";
import {
  AUTOMATION_COLOURS,
  BarList,
  ChartFrame,
  DistributionStrip,
  ImpactEffortMatrix,
  LegendSwatch,
  RESPONSIBILITY_COLOURS,
  RESPONSIBILITY_LABELS,
  RiskGrid,
  RoadmapTimeline,
  ScoreMeter,
  StackedBar,
  StatTile,
  TransformationFlow,
} from "@/components/charts";
import {
  CitationChips,
  GroundingBadge,
  SOURCE_TYPE_COLOURS,
  SOURCE_TYPE_LABELS,
  VerificationBadge,
  credibilityTone,
  FETCH_STATUS_TEXT,
  useEvidence,
} from "@/components/evidence";
import {
  Badge,
  Button,
  CopyButton,
  Disclosure,
  EmptyState,
  Field,
  Panel,
  SectionHeading,
  StatusDot,
  type Tone,
} from "@/components/ui";
import { formatDateTime, formatDuration, humanise } from "@/lib/format";
import type {
  AiOpportunity,
  AnalysisStage,
  EvidenceClaim,
  ImpactEstimate,
  ProcessDetail,
  ResearchRun,
  RiskItem,
  Scorecard,
  Severity,
} from "@/lib/types";

/**
 * The panels behind the tabs on a process.
 *
 * <p>Each one answers a different question a reader has, in the order they tend to ask them: what
 * is this, what is wrong with it, what did we find out, what should we do, what would it look like,
 * what is it worth, what could go wrong, in what order, and — last, and available for every claim
 * on every other tab — how do you know?
 */

// ---------------------------------------------------------------------------
// Shared bits
// ---------------------------------------------------------------------------

const SEVERITY_TONE: Record<Severity, Tone> = {
  HIGH: "critical",
  MEDIUM: "warning",
  LOW: "neutral",
};

const VERDICT_TONE: Record<string, Tone> = {
  STRONG: "good",
  SOUND: "good",
  QUALIFIED: "warning",
  WEAK: "serious",
  REJECTED: "critical",
};

export function formatInr(amount: number): string {
  const absolute = Math.abs(amount);
  if (absolute >= 10_000_000) return `₹${(amount / 10_000_000).toFixed(2)} cr`;
  if (absolute >= 100_000) return `₹${(amount / 100_000).toFixed(2)} L`;
  return `₹${Math.round(amount).toLocaleString("en-IN")}`;
}

// ---------------------------------------------------------------------------
// Overview
// ---------------------------------------------------------------------------

export function OverviewPanel({
  detail,
  onOpenTab,
}: {
  detail: ProcessDetail;
  onOpenTab: (tab: string) => void;
}) {
  // Ordered human -> AI, deliberately: the bar reads as a scale, so the segments must be in the
  // scale's order rather than in whatever order the counts happen to fall.
  const responsibility = useMemo(() => {
    const counts = { HUMAN_LED: 0, AI_AUGMENTED: 0, AI_AUTOMATED: 0 };
    for (const step of detail.futureActivities) {
      counts[step.responsibilityType] += 1;
    }
    return counts;
  }, [detail.futureActivities]);

  const responsibilitySegments = useMemo(
    () =>
      (["HUMAN_LED", "AI_AUGMENTED", "AI_AUTOMATED"] as const).map((key) => ({
        key,
        label: RESPONSIBILITY_LABELS[key],
        value: responsibility[key],
        colour: RESPONSIBILITY_COLOURS[key],
      })),
    [responsibility],
  );

  const monthlySaving = detail.impacts.reduce((sum, impact) => sum + impact.costSavedPerMonthInr, 0);
  const monthlyHours = detail.impacts.reduce((sum, impact) => sum + impact.hoursSavedPerMonth, 0);
  const grounded = detail.opportunities.filter((item) => item.groundingScore > 0).length;

  if (detail.process.status !== "ANALYZED") {
    return (
      <EmptyState
        title="Nothing to summarise yet"
        message="This view is the scorecard, the counts and the headline saving. It fills in once the analysis has run."
      />
    );
  }

  return (
    <div className="space-y-5">
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <StatTile
          label="Steps redesigned"
          value={`${detail.futureActivities.length}`}
          hint={`from ${detail.activities.length} today`}
        />
        <StatTile
          label="Recommendations"
          value={`${detail.opportunities.length}`}
          hint={`${grounded} cite verified evidence`}
          tone={grounded === 0 ? "warning" : "neutral"}
        />
        <StatTile
          label="Estimated monthly saving"
          value={monthlySaving > 0 ? formatInr(monthlySaving) : "—"}
          hint={
            monthlySaving > 0
              ? `${Math.round(monthlyHours).toLocaleString("en-IN")} hours a month, on stated assumptions`
              : "not quantified"
          }
        />
        <StatTile
          label="Analysis score"
          value={detail.scorecard ? `${detail.scorecard.overallScore}` : "—"}
          hint={detail.scorecard ? `grade ${detail.scorecard.grade} · measured, not asserted` : undefined}
          tone={
            !detail.scorecard
              ? "neutral"
              : detail.scorecard.overallScore >= 70
                ? "good"
                : detail.scorecard.overallScore >= 55
                  ? "neutral"
                  : "warning"
          }
        />
      </div>

      <div className="grid items-start gap-4 lg:grid-cols-[1.3fr_1fr]">
        <Panel className="p-4">
          <SectionHeading
            title="Who does the work afterwards"
            hint="Every future step falls into exactly one category, so the parts sum to the whole process."
          />
          <StackedBar segments={responsibilitySegments} height={32} />

          <ul className="mt-4 space-y-1.5 border-t border-[var(--border-subtle)] pt-3">
            {detail.futureActivities.map((step) => (
              <li key={step.id} className="flex items-center gap-2">
                <span
                  className="size-2 shrink-0 rounded-[2px]"
                  style={{ backgroundColor: RESPONSIBILITY_COLOURS[step.responsibilityType] }}
                  aria-hidden="true"
                />
                <span className="tabular w-4 shrink-0 text-[0.6875rem] text-[var(--text-muted)]">
                  {step.sequenceOrder}
                </span>
                <span className="min-w-0 flex-1 truncate text-xs text-[var(--text-secondary)]">
                  {step.name}
                </span>
                <span className="shrink-0 text-[0.6875rem] text-[var(--text-muted)]">
                  {RESPONSIBILITY_LABELS[step.responsibilityType]}
                </span>
              </li>
            ))}
          </ul>

          <p className="mt-3 text-xs leading-relaxed text-[var(--text-secondary)]">
            {responsibility.HUMAN_LED > 0
              ? `${responsibility.HUMAN_LED} step${responsibility.HUMAN_LED === 1 ? " stays" : "s stay"} entirely human. `
              : "No step remains entirely human, which is worth questioning in a process that affects people. "}
            {responsibility.AI_AUGMENTED > 0
              ? `${responsibility.AI_AUGMENTED} keep${responsibility.AI_AUGMENTED === 1 ? "s" : ""} a person in the decision with AI assisting. `
              : ""}
            {responsibility.AI_AUTOMATED > 0
              ? `${responsibility.AI_AUTOMATED} run${responsibility.AI_AUTOMATED === 1 ? "s" : ""} without a person in the loop.`
              : ""}
          </p>
        </Panel>

        {detail.scorecard ? <ScorecardPanel scorecard={detail.scorecard} /> : null}
      </div>

      {detail.research ? (
        <Panel className="p-4">
          <SectionHeading
            title="What the research found"
            hint="Gathered live for this process when it was last analysed."
            action={
              <Button size="sm" onClick={() => onOpenTab("evidence")}>
                Open the evidence
              </Button>
            }
          />
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-5">
            <StatTile label="Sources found" value={detail.research.sourceCount} />
            <StatTile label="Claims extracted" value={detail.research.claimCount} />
            <StatTile
              label="Quotes verified"
              value={detail.research.verifiedClaimCount}
              tone={detail.research.verifiedClaimCount > 0 ? "good" : "warning"}
              hint={
                detail.research.claimCount > 0
                  ? `${Math.round((detail.research.verifiedClaimCount / detail.research.claimCount) * 100)}% of claims`
                  : undefined
              }
            />
            <StatTile label="Independent domains" value={detail.research.distinctDomainCount} />
            <StatTile
              label="Contradictions"
              value={detail.research.contradictionCount}
              tone={detail.research.contradictionCount > 0 ? "warning" : "neutral"}
              hint={detail.research.contradictionCount > 0 ? "sources disagree; both are shown" : "none found"}
            />
          </div>
        </Panel>
      ) : null}

      {detail.futureActivities.length > 0 ? (
        <Panel className="p-4">
          <SectionHeading
            title="What changes"
            hint="Lines are drawn only where a future step records the current step it replaces."
          />
          <TransformationFlow
            current={detail.activities.map((activity) => ({ id: activity.id, name: activity.name }))}
            future={detail.futureActivities.map((step) => ({
              id: step.id,
              name: step.name,
              responsibility: step.responsibilityType,
              replaces: step.replacesActivity,
            }))}
          />
          <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1">
            {Object.entries(RESPONSIBILITY_LABELS).map(([key, label]) => (
              <LegendSwatch
                key={key}
                colour={RESPONSIBILITY_COLOURS[key as keyof typeof RESPONSIBILITY_COLOURS]}
                label={label}
              />
            ))}
          </div>
        </Panel>
      ) : null}
    </div>
  );
}

export function ScorecardPanel({ scorecard }: { scorecard: Scorecard }) {
  return (
    <Panel className="p-4">
      <SectionHeading
        title="How good is this analysis?"
        hint="Every component is arithmetic on stored rows. None of it is the model's opinion of itself."
      />
      <div className="mb-4 flex items-baseline gap-2">
        <span className="tabular text-3xl font-semibold leading-none">{scorecard.overallScore}</span>
        <span className="text-sm text-[var(--text-muted)]">/100</span>
        <Badge tone={scorecard.overallScore >= 70 ? "good" : scorecard.overallScore >= 55 ? "info" : "warning"}>
          Grade {scorecard.grade}
        </Badge>
      </div>
      <div className="space-y-2.5">
        <ScoreMeter
          label="Coverage"
          value={scorecard.coverageScore}
          hint="Share of today's activities the analysis actually engages with"
        />
        <ScoreMeter
          label="Grounding"
          value={scorecard.groundingScore}
          hint="Share of recommendations citing at least one quote-verified claim"
        />
        <ScoreMeter
          label="Corroboration"
          value={scorecard.corroborationScore}
          hint="Share of the evidence a second independent publisher agreed with"
        />
        <ScoreMeter
          label="Reviewer agreement"
          value={scorecard.agreementScore}
          hint="How the adversarial reviewer scored the proposals. A low number means it found problems."
        />
        <ScoreMeter
          label="Specificity"
          value={scorecard.specificityScore}
          hint="Whether the output names capabilities, metrics, data and failure modes"
        />
        <ScoreMeter
          label="Traceability"
          value={scorecard.traceabilityScore}
          hint="Share of generated rows that resolve back to something stored"
        />
      </div>
    </Panel>
  );
}

// ---------------------------------------------------------------------------
// Current state
// ---------------------------------------------------------------------------

export function CurrentPanel({ detail }: { detail: ProcessDetail }) {
  const roles = [...new Set(detail.activities.flatMap((activity) => activity.roles))];
  const systems = [...new Set(detail.activities.flatMap((activity) => activity.systems))];

  return (
    <div className="space-y-4">
      <div className="grid gap-3 sm:grid-cols-3">
        <StatTile label="Steps today" value={detail.activities.length} />
        <StatTile label="Roles involved" value={roles.length} hint={roles.slice(0, 3).join(", ")} />
        <StatTile label="Systems in use" value={systems.length} hint={systems.slice(0, 3).join(", ")} />
      </div>

      <ol className="space-y-2.5">
        {detail.activities.map((activity) => (
          <li key={activity.id}>
            <Panel className="p-4">
              <div className="flex items-start gap-3">
                <span className="tabular mt-0.5 flex size-6 shrink-0 items-center justify-center rounded-md bg-[var(--surface-3)] text-xs font-semibold text-[var(--text-secondary)]">
                  {activity.sequenceOrder}
                </span>
                <div className="min-w-0 flex-1">
                  <h3 className="text-sm font-semibold text-[var(--text-primary)]">{activity.name}</h3>
                  {activity.description ? (
                    <p className="mt-1 text-[0.8125rem] leading-relaxed text-[var(--text-secondary)]">
                      {activity.description}
                    </p>
                  ) : null}

                  <div className="mt-2.5 flex flex-wrap gap-1.5">
                    {activity.roles.map((role) => (
                      <Badge key={role} tone="neutral">
                        {role}
                      </Badge>
                    ))}
                    {activity.systems.map((system) => (
                      <Badge key={system} tone="info">
                        {system}
                      </Badge>
                    ))}
                    {activity.roles.length === 0 && activity.systems.length === 0 ? (
                      <span className="text-xs italic text-[var(--text-muted)]">
                        No roles or systems recorded for this step
                      </span>
                    ) : null}
                  </div>

                  {activity.problems.length > 0 ? (
                    <ul className="mt-3 space-y-1.5 border-t border-[var(--border-subtle)] pt-2.5">
                      {activity.problems.map((problem) => (
                        <li key={problem.id} className="flex items-start gap-2">
                          <Badge tone={SEVERITY_TONE[problem.severity]}>{problem.severity.toLowerCase()}</Badge>
                          <span className="text-xs leading-relaxed text-[var(--text-secondary)]">
                            {problem.description}
                          </span>
                        </li>
                      ))}
                    </ul>
                  ) : null}
                </div>
              </div>
            </Panel>
          </li>
        ))}
      </ol>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Diagnosis
// ---------------------------------------------------------------------------

export function DiagnosisPanel({ detail }: { detail: ProcessDetail }) {
  const problems = [...detail.problems].sort((left, right) => {
    const order = { HIGH: 0, MEDIUM: 1, LOW: 2 };
    return order[left.severity] - order[right.severity];
  });

  if (problems.length === 0) {
    return (
      <EmptyState
        title="No problems recorded"
        message="Run the analysis to diagnose this process, or add the problems your team already knows about when you edit it."
      />
    );
  }

  return (
    <div className="space-y-4">
      <div className="grid gap-3 sm:grid-cols-3">
        {(["HIGH", "MEDIUM", "LOW"] as Severity[]).map((severity) => (
          <StatTile
            key={severity}
            label={`${severity.toLowerCase()} severity`}
            value={problems.filter((problem) => problem.severity === severity).length}
            tone={severity === "HIGH" ? "critical" : severity === "MEDIUM" ? "warning" : "neutral"}
          />
        ))}
      </div>

      <ul className="space-y-2.5">
        {problems.map((problem) => (
          <li key={problem.id}>
            <Panel className="p-4">
              <div className="flex flex-wrap items-center gap-2">
                <Badge tone={SEVERITY_TONE[problem.severity]}>{problem.severity.toLowerCase()}</Badge>
                {problem.activityName ? (
                  <Badge tone="neutral">{problem.activityName}</Badge>
                ) : (
                  <Badge tone="neutral">whole process</Badge>
                )}
                <Badge tone={problem.source === "AI_GENERATED" ? "info" : "neutral"}>
                  {problem.source === "AI_GENERATED" ? "diagnosed" : "reported by the team"}
                </Badge>
              </div>

              <p className="mt-2 text-[0.875rem] leading-relaxed text-[var(--text-primary)]">
                {problem.description}
              </p>

              <div className="mt-3 grid gap-3 border-t border-[var(--border-subtle)] pt-3 sm:grid-cols-2">
                <Field
                  label="Root cause"
                  value={problem.rootCause}
                  fallback="Not established from the information supplied"
                />
                <Field label="What supports this" value={problem.evidenceNote} />
              </div>
            </Panel>
          </li>
        ))}
      </ul>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Opportunities
// ---------------------------------------------------------------------------

export function OpportunitiesPanel({ detail }: { detail: ProcessDetail }) {
  const [selected, setSelected] = useState<string | null>(null);

  const matrix = useMemo(
    () =>
      detail.opportunities
        .filter((opportunity) => opportunity.review)
        .map((opportunity) => ({
          id: opportunity.id,
          label: opportunity.aiCapability,
          impact: opportunity.review!.businessImpact,
          effort: opportunity.review!.implementationEffort,
          tone: AUTOMATION_COLOURS[opportunity.automationPotential],
          detail: `${opportunity.review!.verdict.toLowerCase()}, grounding ${opportunity.groundingScore}`,
        })),
    [detail.opportunities],
  );

  if (detail.opportunities.length === 0) {
    return (
      <EmptyState
        title="No recommendations yet"
        message="Run the analysis to research this domain and propose interventions that cite what they rest on."
      />
    );
  }

  return (
    <div className="space-y-4">
      {matrix.length > 0 ? (
        <Panel className="p-4">
          <ChartFrame
            title="What is worth doing first"
            subtitle="Scored by the reviewing model, not the one that proposed them."
            legend={
              <>
                <LegendSwatch colour={AUTOMATION_COLOURS.LOW} label="Low automation potential" />
                <LegendSwatch colour={AUTOMATION_COLOURS.MEDIUM} label="Medium" />
                <LegendSwatch colour={AUTOMATION_COLOURS.HIGH} label="High" />
              </>
            }
          >
            <ImpactEffortMatrix points={matrix} onSelect={setSelected} />
          </ChartFrame>
        </Panel>
      ) : null}

      <ul className="space-y-3">
        {detail.opportunities.map((opportunity, index) => (
          <li key={opportunity.id}>
            <OpportunityCard
              opportunity={opportunity}
              index={index + 1}
              expanded={selected === opportunity.id}
            />
          </li>
        ))}
      </ul>
    </div>
  );
}

export function OpportunityCard({
  opportunity,
  index,
  expanded = false,
}: {
  opportunity: AiOpportunity;
  index: number;
  expanded?: boolean;
}) {
  const review = opportunity.review;
  const impact = opportunity.impact;

  return (
    <Panel className="p-4">
      <div className="flex items-start gap-3">
        <span className="tabular mt-0.5 flex size-6 shrink-0 items-center justify-center rounded-md bg-[var(--surface-3)] text-xs font-semibold text-[var(--text-secondary)]">
          {index}
        </span>

        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-1.5">
            <Badge
              tone="neutral"
              title="How much of this step the intervention could take over"
            >
              {opportunity.automationPotential.toLowerCase()} automation potential
            </Badge>
            <GroundingBadge score={opportunity.groundingScore} claimCount={opportunity.citedClaims.length} />
            {review ? (
              <Badge tone={VERDICT_TONE[review.verdict] ?? "neutral"} title={review.critique ?? undefined}>
                reviewer: {review.verdict.toLowerCase()}
              </Badge>
            ) : null}
            {opportunity.activityName ? <Badge tone="neutral">{opportunity.activityName}</Badge> : null}
          </div>

          <h3 className="mt-2 text-sm font-semibold leading-snug text-[var(--text-primary)]">
            {opportunity.aiCapability}
          </h3>
          <p className="mt-1 text-[0.8125rem] leading-relaxed text-[var(--text-secondary)]">
            {opportunity.description}
            <CitationChips claims={opportunity.citedClaims} className="ml-1" />
          </p>

          <div className="mt-3 grid gap-3 sm:grid-cols-2">
            <Field label="Business benefit" value={opportunity.businessBenefit} />
            <Field
              label="Human oversight"
              value={opportunity.humanOversight}
              fallback="Not stated — treat that as a gap, not as an absence of need"
            />
            <Field label="Risk in this domain" value={opportunity.risk} />
            <Field label="Data it needs to exist" value={opportunity.dataRequirement} />
          </div>

          {impact ? (
            <div className="mt-3 rounded-lg border border-[var(--border-subtle)] bg-[var(--surface-1)] p-3">
              <div className="flex flex-wrap items-baseline justify-between gap-2">
                <p className="eyebrow">What it is worth</p>
                <Badge
                  tone={impact.basis === "USER_SUPPLIED" ? "good" : "warning"}
                  title={
                    impact.basis === "USER_SUPPLIED"
                      ? "The inputs were supplied by a person."
                      : "The inputs were estimated by a model. Treat the figure as an order of magnitude, and check the assumptions."
                  }
                >
                  {impact.basis === "USER_SUPPLIED"
                    ? "your figures"
                    : impact.basis === "BENCHMARK"
                      ? "cites research"
                      : "model estimate"}
                </Badge>
              </div>
              <dl className="mt-2 grid grid-cols-2 gap-2 sm:grid-cols-4">
                <ImpactFigure label="Hours a month" value={Math.round(impact.hoursSavedPerMonth).toLocaleString("en-IN")} />
                <ImpactFigure label="Net saving" value={formatInr(impact.costSavedPerMonthInr)} />
                <ImpactFigure
                  label="Build effort"
                  value={impact.oneOffEffortDays ? `${Math.round(impact.oneOffEffortDays)} days` : "—"}
                />
                <ImpactFigure
                  label="Payback"
                  value={
                    impact.paybackMonths == null
                      ? "—"
                      : impact.paybackMonths < 1
                        ? "immediate"
                        : `${impact.paybackMonths.toFixed(1)} months`
                  }
                />
              </dl>
              <p className="mt-2 text-[0.6875rem] leading-relaxed text-[var(--text-muted)]">
                {Math.round(impact.volumePerMonth).toLocaleString("en-IN")} items a month ×{" "}
                {impact.minutesPerItem} minutes × {Math.round(impact.automationShare * 100)}% removed, at{" "}
                {formatInr(impact.hourlyCostInr)}/hour.
              </p>
            </div>
          ) : null}

          {review?.critique ? (
            <div className="mt-3 rounded-lg border border-[color-mix(in_oklab,var(--status-warning-ink)_25%,transparent)] bg-[var(--status-warning-wash)] p-3">
              <p className="eyebrow" style={{ color: "var(--status-warning-ink)" }}>
                What the reviewing model objected to
              </p>
              <p className="mt-1 text-xs leading-relaxed text-[var(--text-secondary)]">{review.critique}</p>
              <div className="mt-2 grid grid-cols-2 gap-x-4 gap-y-1 sm:grid-cols-5">
                <ReviewScore label="Feasibility" value={review.feasibility} />
                <ReviewScore label="Evidence" value={review.evidenceStrength} />
                <ReviewScore label="Impact" value={review.businessImpact} />
                <ReviewScore label="Risk" value={review.riskLevel} inverted />
                <ReviewScore label="Effort" value={review.implementationEffort} inverted />
              </div>
            </div>
          ) : null}

          <Disclosure
            className="mt-3"
            defaultOpen={expanded}
            summary={`Reasoning, success metric and ${opportunity.citedClaims.length} citation${
              opportunity.citedClaims.length === 1 ? "" : "s"
            }`}
          >
            <div className="space-y-3">
              <Field label="Why this follows" value={opportunity.reasoningNote} />
              <Field label="Root cause it addresses" value={opportunity.rootCause} />
              <Field label="How you would know it worked" value={opportunity.successMetric} />
              {opportunity.citedClaims.length > 0 ? (
                <div>
                  <p className="eyebrow mb-1.5">Evidence cited</p>
                  <ul className="space-y-1.5">
                    {opportunity.citedClaims.map((claim) => (
                      <ClaimLine key={claim.id} claim={claim} />
                    ))}
                  </ul>
                </div>
              ) : (
                <p className="text-xs italic text-[var(--text-muted)]">
                  This recommendation cites no evidence from the research run. It may still be sound —
                  but nothing gathered here supports it, and its grounding score is zero.
                </p>
              )}
            </div>
          </Disclosure>
        </div>
      </div>
    </Panel>
  );
}

function ImpactFigure({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="eyebrow">{label}</dt>
      <dd className="tabular text-sm font-semibold text-[var(--text-primary)]">{value}</dd>
    </div>
  );
}

function ReviewScore({ label, value, inverted = false }: { label: string; value: number; inverted?: boolean }) {
  const good = inverted ? value <= 2 : value >= 4;
  return (
    <div className="flex items-baseline justify-between gap-1">
      <span className="text-[0.6875rem] text-[var(--text-muted)]">{label}</span>
      <span
        className="tabular text-xs font-semibold"
        style={{ color: good ? "var(--status-good-ink)" : "var(--text-secondary)" }}
      >
        {value}/5
      </span>
    </div>
  );
}

/** One cited claim, compact, opening the drawer on click. */
export function ClaimLine({ claim }: { claim: EvidenceClaim }) {
  const evidence = useEvidence();
  return (
    <li>
      <button
        type="button"
        onClick={() => evidence.open([claim])}
        className="flex w-full items-start gap-2 rounded-md p-1.5 text-left hover:bg-[var(--surface-3)]"
      >
        <span className={`citation shrink-0 ${claim.quoteVerified ? "" : "citation-unverified"}`}>
          {claim.citationIndex}
        </span>
        <span className="min-w-0 flex-1">
          <span className="block text-xs leading-snug text-[var(--text-secondary)]">{claim.claimText}</span>
          <span className="mt-0.5 block text-[0.6875rem] text-[var(--text-muted)]">
            {claim.source.domain} · {SOURCE_TYPE_LABELS[claim.source.sourceType]} ·{" "}
            {claim.quoteVerified ? "quote verified" : "quote unverified"}
          </span>
        </span>
      </button>
    </li>
  );
}

// ---------------------------------------------------------------------------
// Future state
// ---------------------------------------------------------------------------

export function FuturePanel({ detail }: { detail: ProcessDetail }) {
  if (detail.futureActivities.length === 0) {
    return (
      <EmptyState
        title="No future process designed yet"
        message="Run the analysis to produce an ordered future-state process with an explicit human and AI split at every step."
      />
    );
  }

  return (
    <ol className="space-y-2.5">
      {detail.futureActivities.map((step) => (
        <li key={step.id}>
          <Panel className="overflow-hidden">
            <div className="flex">
              <div
                className="w-1 shrink-0"
                style={{ backgroundColor: RESPONSIBILITY_COLOURS[step.responsibilityType] }}
                aria-hidden="true"
              />
              <div className="min-w-0 flex-1 p-4">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="tabular flex size-6 items-center justify-center rounded-md bg-[var(--surface-3)] text-xs font-semibold text-[var(--text-secondary)]">
                    {step.sequenceOrder}
                  </span>
                  <h3 className="text-sm font-semibold text-[var(--text-primary)]">{step.name}</h3>
                  <Badge tone="neutral">{RESPONSIBILITY_LABELS[step.responsibilityType]}</Badge>
                  {step.replacesActivity ? (
                    <Badge tone="info" title="The current step this replaces">
                      replaces {step.replacesActivity}
                    </Badge>
                  ) : (
                    <Badge tone="brand">new step</Badge>
                  )}
                </div>

                {step.description ? (
                  <p className="mt-2 text-[0.8125rem] leading-relaxed text-[var(--text-secondary)]">
                    {step.description}
                  </p>
                ) : null}

                <div className="mt-3 grid gap-3 sm:grid-cols-2">
                  <Field label="The person is accountable for" value={step.humanResponsibility} />
                  <Field label="The AI does" value={step.aiResponsibility} fallback="Nothing at this step" />
                  <Field
                    label="When the AI is wrong or unavailable"
                    value={step.failureMode}
                    fallback={
                      step.responsibilityType === "HUMAN_LED"
                        ? "No AI at this step, so nothing to fall back from"
                        : "Not stated — a step involving AI needs an answer to this"
                    }
                  />
                  <Field label="How work passes between them" value={step.handoffNote} />
                </div>

                {step.cycleTimeNote ? (
                  <p className="mt-2.5 text-xs text-[var(--text-muted)]">⏱ {step.cycleTimeNote}</p>
                ) : null}

                {step.interventions.length > 0 ? (
                  <ul className="mt-3 space-y-1.5 border-t border-[var(--border-subtle)] pt-2.5">
                    {step.interventions.map((intervention) => (
                      <li key={intervention.id} className="flex items-start gap-2">
                        <Badge tone="brand">{intervention.interventionType.toLowerCase()}</Badge>
                        <span className="text-xs leading-relaxed text-[var(--text-secondary)]">
                          {intervention.description}
                        </span>
                      </li>
                    ))}
                  </ul>
                ) : null}
              </div>
            </div>
          </Panel>
        </li>
      ))}
    </ol>
  );
}

// ---------------------------------------------------------------------------
// Impact
// ---------------------------------------------------------------------------

export function ImpactPanel({ detail }: { detail: ProcessDetail }) {
  if (detail.impacts.length === 0) {
    return (
      <EmptyState
        title="Nothing quantified yet"
        message="The quantification stage estimates the volume, handling time and automation share behind each recommendation, then computes the saving from those inputs."
      />
    );
  }

  const totalHours = detail.impacts.reduce((sum, item) => sum + item.hoursSavedPerMonth, 0);
  const totalSaving = detail.impacts.reduce((sum, item) => sum + item.costSavedPerMonthInr, 0);
  const totalBuild = detail.impacts.reduce((sum, item) => sum + (item.oneOffEffortDays ?? 0), 0);
  const modelEstimated = detail.impacts.filter((item) => item.basis !== "USER_SUPPLIED").length;

  return (
    <div className="space-y-4">
      <div className="grid gap-3 sm:grid-cols-4">
        <StatTile
          label="Hours saved a month"
          value={Math.round(totalHours).toLocaleString("en-IN")}
          hint="across every recommendation"
        />
        <StatTile label="Net saving a month" value={formatInr(totalSaving)} hint="after running costs" />
        <StatTile label="Build effort" value={`${Math.round(totalBuild)} days`} hint="one-off, all items" />
        <StatTile
          label="Estimated inputs"
          value={`${modelEstimated} of ${detail.impacts.length}`}
          tone={modelEstimated > 0 ? "warning" : "good"}
          hint="figures a model estimated rather than a person supplied"
        />
      </div>

      {modelEstimated > 0 ? (
        <div className="rounded-[var(--radius-card)] border border-[color-mix(in_oklab,var(--status-warning-ink)_25%,transparent)] bg-[var(--status-warning-wash)] p-3">
          <p className="text-xs leading-relaxed text-[var(--text-secondary)]">
            <strong className="text-[var(--status-warning-ink)]">These are estimates.</strong> The volumes,
            handling times and hourly costs below were estimated by a model from the process description,
            not measured. Every one is shown with the assumptions it rests on so you can replace it with a
            real number — the arithmetic on top of them is deterministic and will follow.
          </p>
        </div>
      ) : null}

      <Panel className="p-4">
        <SectionHeading title="Where the saving comes from" hint="Net of running cost, per month." />
        <BarList
          data={detail.impacts.map((impact) => ({
            key: impact.id,
            label: impact.label,
            value: Math.round(impact.costSavedPerMonthInr),
            note: `${Math.round(impact.hoursSavedPerMonth)} hours · ${Math.round(
              impact.automationShare * 100,
            )}% of the step removed`,
          }))}
          format={(value) => formatInr(value)}
        />
      </Panel>

      <div className="space-y-2.5">
        {detail.impacts.map((impact) => (
          <ImpactRow key={impact.id} impact={impact} />
        ))}
      </div>
    </div>
  );
}

function ImpactRow({ impact }: { impact: ImpactEstimate }) {
  return (
    <Panel className="p-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <h3 className="text-sm font-semibold text-[var(--text-primary)]">{impact.label}</h3>
        <Badge tone={impact.basis === "USER_SUPPLIED" ? "good" : "warning"}>
          {humanise(impact.basis).toLowerCase()}
        </Badge>
      </div>

      <dl className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-7">
        <ImpactFigure label="Items a month" value={Math.round(impact.volumePerMonth).toLocaleString("en-IN")} />
        <ImpactFigure label="Minutes each" value={String(impact.minutesPerItem)} />
        <ImpactFigure label="Share removed" value={`${Math.round(impact.automationShare * 100)}%`} />
        <ImpactFigure label="Hourly cost" value={formatInr(impact.hourlyCostInr)} />
        <ImpactFigure label="Hours saved" value={Math.round(impact.hoursSavedPerMonth).toLocaleString("en-IN")} />
        <ImpactFigure label="Net saving" value={formatInr(impact.costSavedPerMonthInr)} />
        <ImpactFigure
          label="Payback"
          value={impact.paybackMonths == null ? "—" : `${impact.paybackMonths.toFixed(1)} mo`}
        />
      </dl>

      {impact.assumptions ? (
        <Disclosure className="mt-3" summary="The assumptions behind these numbers">
          <ul className="space-y-1">
            {impact.assumptions
              .split("\n")
              .map((line) => line.trim())
              .filter(Boolean)
              .map((line, index) => (
                <li key={index} className="text-xs leading-relaxed text-[var(--text-secondary)]">
                  • {line}
                </li>
              ))}
          </ul>
        </Disclosure>
      ) : (
        <p className="mt-2 text-xs italic text-[var(--text-muted)]">
          No assumptions were stated, so these figures cannot be checked. Treat them as indicative only.
        </p>
      )}
    </Panel>
  );
}

// ---------------------------------------------------------------------------
// Risks
// ---------------------------------------------------------------------------

export function RisksPanel({ detail }: { detail: ProcessDetail }) {
  const [focused, setFocused] = useState<string | null>(null);

  if (detail.risks.length === 0) {
    return (
      <EmptyState
        title="No risk register yet"
        message="The risk stage reviews the proposed design and records what could go wrong, who owns the control, and which obligations the research established."
      />
    );
  }

  const byCategory = new Map<string, number>();
  for (const risk of detail.risks) {
    byCategory.set(risk.category, (byCategory.get(risk.category) ?? 0) + 1);
  }
  const withObligation = detail.risks.filter((risk) => risk.obligation).length;
  const severe = detail.risks.filter((risk) => risk.severityScore >= 12).length;

  return (
    <div className="space-y-4">
      <div className="grid items-start gap-4 lg:grid-cols-[minmax(0,24rem)_1fr]">
        <Panel className="p-4">
          <SectionHeading title="The register at a glance" hint="Likelihood against impact, 1 to 5." />
          <RiskGrid
            risks={detail.risks.map((risk) => ({
              id: risk.id,
              title: risk.title,
              likelihood: risk.likelihood,
              impact: risk.impact,
              category: risk.category,
            }))}
            onSelect={setFocused}
          />
        </Panel>

        <div className="space-y-3">
          <div className="grid gap-3 sm:grid-cols-3">
            <StatTile label="Risks" value={detail.risks.length} />
            <StatTile
              label="Severe"
              value={severe}
              tone={severe > 0 ? "warning" : "good"}
              hint="likelihood × impact ≥ 12"
            />
            <StatTile
              label="Cite an obligation"
              value={withObligation}
              hint="a requirement the research established"
            />
          </div>
          <Panel className="p-4">
            <SectionHeading title="Categories covered" />
            <DistributionStrip
              items={[...byCategory.entries()].map(([category, count], index) => ({
                key: category,
                label: category.toLowerCase(),
                value: count,
                colour: `var(--cat-${(index % 8) + 1})`,
              }))}
            />
          </Panel>
        </div>
      </div>

      <ul className="space-y-2.5">
        {[...detail.risks]
          .sort((left, right) => right.severityScore - left.severityScore)
          .map((risk) => (
            <li key={risk.id}>
              <RiskCard risk={risk} highlighted={focused === risk.id} />
            </li>
          ))}
      </ul>
    </div>
  );
}

function RiskCard({ risk, highlighted }: { risk: RiskItem; highlighted: boolean }) {
  const tone: Tone =
    risk.severityScore >= 16 ? "critical" : risk.severityScore >= 9 ? "serious" : risk.severityScore >= 4 ? "warning" : "neutral";

  return (
    <Panel className={`p-4 ${highlighted ? "ring-2 ring-[var(--border-focus)]" : ""}`}>
      <div className="flex flex-wrap items-center gap-2">
        <Badge tone={tone}>severity {risk.severityScore}</Badge>
        <Badge tone="neutral">{risk.category.toLowerCase()}</Badge>
        <span className="text-[0.6875rem] text-[var(--text-muted)]">
          likelihood {risk.likelihood}/5 · impact {risk.impact}/5
        </span>
      </div>

      <h3 className="mt-2 text-sm font-semibold text-[var(--text-primary)]">{risk.title}</h3>
      <p className="mt-1 text-[0.8125rem] leading-relaxed text-[var(--text-secondary)]">
        {risk.description}
        <CitationChips claims={risk.citedClaims} className="ml-1" />
      </p>

      <div className="mt-3 grid gap-3 border-t border-[var(--border-subtle)] pt-3 sm:grid-cols-[2fr_1fr]">
        <Field
          label="Control"
          value={risk.mitigation}
          fallback="No control was proposed, so this risk is currently unmanaged"
        />
        <Field label="Owner" value={risk.ownerRole} />
      </div>

      {risk.obligation ? (
        <div className="mt-3 rounded-lg border border-[color-mix(in_oklab,var(--text-link)_25%,transparent)] bg-[var(--brand-wash)] p-2.5">
          <p className="eyebrow" style={{ color: "var(--text-link)" }}>
            Obligation established by the research
          </p>
          <p className="mt-1 text-xs leading-relaxed text-[var(--text-secondary)]">{risk.obligation}</p>
        </div>
      ) : null}
    </Panel>
  );
}

// ---------------------------------------------------------------------------
// Roadmap
// ---------------------------------------------------------------------------

export function RoadmapPanel({ detail }: { detail: ProcessDetail }) {
  if (detail.roadmap.length === 0) {
    return (
      <EmptyState
        title="No delivery plan yet"
        message="The roadmap stage sequences the interventions into waves, adds the enabling work they depend on, and states how each piece would be judged."
      />
    );
  }

  return (
    <div className="space-y-4">
      <Panel className="p-4">
        <SectionHeading title="Delivery sequence" hint="Wave 1 is what can start now." />
        <RoadmapTimeline
          items={detail.roadmap.map((item) => ({
            id: item.id,
            title: item.title,
            wave: item.wave,
            durationWeeks: item.durationWeeks,
            effort: item.effort,
            impact: item.impact,
          }))}
        />
      </Panel>

      <div className="space-y-2.5">
        {detail.roadmap.map((item) => (
          <Panel key={item.id} className="p-4">
            <div className="flex flex-wrap items-center gap-2">
              <Badge tone="brand">wave {item.wave}</Badge>
              <Badge tone="neutral">{item.effort.toLowerCase()} effort</Badge>
              <Badge tone="neutral">{item.impact.toLowerCase()} impact</Badge>
              {item.durationWeeks ? <Badge tone="neutral">{item.durationWeeks} weeks</Badge> : null}
              {!item.opportunityId ? (
                <Badge tone="info" title="Work that makes the interventions possible rather than an intervention itself">
                  enabling work
                </Badge>
              ) : null}
            </div>
            <h3 className="mt-2 text-sm font-semibold text-[var(--text-primary)]">{item.title}</h3>
            {item.description ? (
              <p className="mt-1 text-[0.8125rem] leading-relaxed text-[var(--text-secondary)]">
                {item.description}
              </p>
            ) : null}
            <div className="mt-3 grid gap-3 border-t border-[var(--border-subtle)] pt-3 sm:grid-cols-2">
              <Field label="Depends on" value={item.dependsOn} fallback="Nothing — it can start now" />
              <Field label="How you would know it worked" value={item.successMetric} />
            </div>
          </Panel>
        ))}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Evidence
// ---------------------------------------------------------------------------

export function EvidencePanel({
  research,
  loading,
  detail,
}: {
  research: ResearchRun | null;
  loading: boolean;
  detail: ProcessDetail;
}) {
  const evidence = useEvidence();
  const [filter, setFilter] = useState<"all" | "verified" | "unverified">("all");

  if (loading) {
    return <p className="py-8 text-center text-sm text-[var(--text-muted)]">Loading the research…</p>;
  }

  if (!research) {
    // Three different situations reached this branch with the same sentence, one of which claimed
    // an analysis had run when none had, and pointed at a corpus that was not on the page.
    const analysed = detail.process.status === "ANALYZED";
    return (
      <div className="space-y-4">
        <EmptyState
          title={analysed ? "No live research recorded" : "No evidence gathered yet"}
          message={
            !analysed
              ? "Evidence is gathered while the analysis runs: the searches it planned, the pages it read, and every quote checked against the page it came from."
              : detail.evidence.length > 0
                ? "This analysis ran without the live research layer, or predates it. The curated corpus below is what grounded it instead."
                : "This analysis ran without the live research layer, or predates it, and no curated sources were recorded against it either."
          }
        />
        {detail.evidence.length > 0 ? (
          <Panel className="p-4">
            <SectionHeading title="Curated corpus used" />
            <ul className="space-y-2">
              {detail.evidence.map((retrieved) => (
                <li key={retrieved.snippet.id} className="border-b border-[var(--border-subtle)] pb-2 last:border-0">
                  <a
                    href={retrieved.snippet.sourceUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-[0.8125rem] font-medium text-[var(--text-link)] hover:underline"
                  >
                    {retrieved.snippet.title}
                  </a>
                  <p className="mt-1 text-xs text-[var(--text-secondary)]">{retrieved.snippet.snippetText}</p>
                </li>
              ))}
            </ul>
          </Panel>
        ) : null}
      </div>
    );
  }

  const claims = research.claims.filter((claim) =>
    filter === "all" ? true : filter === "verified" ? claim.quoteVerified : !claim.quoteVerified,
  );

  const bySourceType = new Map<string, number>();
  for (const source of research.sources) {
    bySourceType.set(source.sourceType, (bySourceType.get(source.sourceType) ?? 0) + 1);
  }

  return (
    <div className="space-y-4">
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
        <StatTile label="Queries planned" value={research.queryCount} />
        <StatTile label="Sources found" value={research.sources.length} hint={`${research.hitCount} raw results`} />
        <StatTile label="Read in full" value={research.documentCount} />
        <StatTile
          label="Quotes verified"
          value={`${research.verifiedClaimCount}/${research.claimCount}`}
          tone={research.verifiedClaimCount === research.claimCount ? "good" : "warning"}
        />
        <StatTile
          label="Independent domains"
          value={research.distinctDomainCount}
          hint="corroboration only counts across these"
        />
      </div>

      <Panel className="p-4">
        <SectionHeading
          title="What was searched"
          hint="Planned by a model from the process and its diagnosis, then extended to cover the angles it skipped."
        />
        <ul className="space-y-1.5">
          {research.queries.map((query) => (
            <li key={query.id} className="flex flex-wrap items-baseline gap-2">
              <Badge tone={query.origin === "MODEL" ? "brand" : "neutral"}>
                {humanise(query.intent).toLowerCase()}
              </Badge>
              <span className="mono text-[var(--text-secondary)]">{query.queryText}</span>
              <span className="text-[0.6875rem] text-[var(--text-muted)]">
                {query.hitCount} result{query.hitCount === 1 ? "" : "s"}
              </span>
            </li>
          ))}
        </ul>
      </Panel>

      <div className="grid items-start gap-4 lg:grid-cols-[minmax(0,20rem)_1fr]">
        <Panel className="p-4">
          <SectionHeading title="What kind of sources" />
          <DistributionStrip
            items={[...bySourceType.entries()].map(([type, count]) => ({
              key: type,
              label: SOURCE_TYPE_LABELS[type as keyof typeof SOURCE_TYPE_LABELS] ?? type,
              value: count,
              colour: SOURCE_TYPE_COLOURS[type as keyof typeof SOURCE_TYPE_COLOURS] ?? "var(--text-muted)",
            }))}
          />
          {research.notes.length > 0 ? (
            <Disclosure className="mt-3" summary={`${research.notes.length} notes from this run`}>
              <ul className="space-y-1">
                {research.notes.map((note, index) => (
                  <li key={index} className="text-xs leading-relaxed text-[var(--text-secondary)]">
                    • {note}
                  </li>
                ))}
              </ul>
            </Disclosure>
          ) : null}
        </Panel>

        <Panel className="p-4">
          <SectionHeading
            title="Sources"
            hint="Ranked as the run ranked them. Credibility is computed; open one to see the arithmetic."
          />
          <ul className="max-h-[32rem] space-y-1.5 overflow-y-auto pr-1">
            {research.sources.map((source) => {
              const status = FETCH_STATUS_TEXT[source.fetchStatus];
              return (
                <li key={source.id}>
                  <button
                    type="button"
                    onClick={() =>
                      evidence.openSource(
                        source,
                        research.claims.filter((claim) => claim.source.id === source.id),
                      )
                    }
                    className="flex w-full items-start gap-2 rounded-md p-1.5 text-left hover:bg-[var(--surface-3)]"
                  >
                    <span
                      className="mt-1 size-2 shrink-0 rounded-[2px]"
                      style={{ backgroundColor: SOURCE_TYPE_COLOURS[source.sourceType] }}
                      aria-hidden="true"
                    />
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-xs font-medium text-[var(--text-primary)]">
                        {source.title}
                      </span>
                      <span className="mt-0.5 flex flex-wrap items-center gap-x-2 text-[0.6875rem] text-[var(--text-muted)]">
                        <span>{source.domain}</span>
                        <StatusDot tone={credibilityTone(source.credibilityScore)} label={`${source.credibilityScore}/100`} />
                        <span>{status.label}</span>
                        {source.claimCount > 0 ? <span>{source.claimCount} claims</span> : null}
                      </span>
                    </span>
                  </button>
                </li>
              );
            })}
          </ul>
        </Panel>
      </div>

      <Panel className="p-4">
        <SectionHeading
          title="Every claim, with the quote that was checked"
          hint="A verified quote was located in the stored page text by string matching — not by asking a model."
          action={
            <div className="flex gap-1 rounded-lg border border-[var(--border-subtle)] p-0.5">
              {(["all", "verified", "unverified"] as const).map((option) => (
                <button
                  key={option}
                  type="button"
                  onClick={() => setFilter(option)}
                  aria-pressed={filter === option}
                  className={`rounded-md px-2 py-1 text-[0.6875rem] font-medium ${
                    filter === option
                      ? "bg-[var(--surface-inverse)] text-[var(--text-inverse)]"
                      : "text-[var(--text-secondary)]"
                  }`}
                >
                  {option}
                </button>
              ))}
            </div>
          }
        />
        {claims.length === 0 ? (
          <p className="py-6 text-center text-xs text-[var(--text-muted)]">
            No claims in this category.
          </p>
        ) : (
          <ul className="space-y-2">
            {claims.map((claim) => (
              <li key={claim.id} className="border-b border-[var(--border-subtle)] pb-2 last:border-0">
                <div className="flex items-start gap-2">
                  <span className={`citation shrink-0 ${claim.quoteVerified ? "" : "citation-unverified"}`}>
                    {claim.citationIndex}
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="text-[0.8125rem] leading-snug text-[var(--text-primary)]">{claim.claimText}</p>
                    <blockquote className={`mt-1.5 ${claim.quoteVerified ? "quote" : "quote quote-unverified"}`}>
                      &ldquo;{claim.quote}&rdquo;
                    </blockquote>
                    <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
                      <VerificationBadge claim={claim} />
                      <a
                        href={claim.source.url}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="text-[0.6875rem] text-[var(--text-link)] hover:underline"
                      >
                        {claim.source.domain}
                      </a>
                      <span className="text-[0.6875rem] text-[var(--text-muted)]">
                        {SOURCE_TYPE_LABELS[claim.source.sourceType]} · credibility{" "}
                        {claim.source.credibilityScore}/100
                      </span>
                    </div>
                  </div>
                </div>
              </li>
            ))}
          </ul>
        )}
      </Panel>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Trace
// ---------------------------------------------------------------------------

export function TracePanel({
  stages,
  detail,
  loading,
}: {
  stages: AnalysisStage[];
  detail: ProcessDetail;
  loading: boolean;
}) {
  const run = detail.latestRun;

  if (loading) {
    return <p className="py-8 text-center text-sm text-[var(--text-muted)]">Loading the trace…</p>;
  }
  if (!run) {
    return (
      <EmptyState
        title="No run recorded"
        message="Once this process has been analysed, every stage appears here with the exact prompt sent and the exact text the model returned."
      />
    );
  }

  const statusTone: Record<string, Tone> = {
    SUCCEEDED: "good",
    DEGRADED: "warning",
    SKIPPED: "neutral",
    FAILED: "critical",
    RUNNING: "info",
  };

  return (
    <div className="space-y-4">
      <Panel className="p-4">
        <SectionHeading
          title="This is the evidence that nothing here is hard-coded"
          hint="Every prompt sent and every response received, stored per stage."
        />
        <dl className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-6">
          <ImpactFigure label="Pipeline" value={run.pipelineVersion ?? "—"} />
          <ImpactFigure label="Stages" value={String(run.stageCount || stages.length)} />
          <ImpactFigure
            label="Tokens in / out"
            value={`${run.totalPromptTokens.toLocaleString("en-IN")} / ${run.totalOutputTokens.toLocaleString("en-IN")}`}
          />
          <ImpactFigure label="Served from cache" value={String(run.cacheHitCount)} />
          <ImpactFigure label="Waiting on limits" value={formatDuration(run.throttledMs)} />
          <ImpactFigure label="Total time" value={formatDuration(run.durationMs)} />
        </dl>
        {run.validationWarnings.length > 0 ? (
          <Disclosure className="mt-3" summary={`${run.validationWarnings.length} validation warnings`}>
            <ul className="space-y-1">
              {run.validationWarnings.map((warning, index) => (
                <li key={index} className="text-xs leading-relaxed text-[var(--text-secondary)]">
                  • {warning}
                </li>
              ))}
            </ul>
          </Disclosure>
        ) : null}
      </Panel>

      <ol className="space-y-2.5">
        {stages.map((stage) => (
          <li key={stage.id}>
            <Panel className="p-4">
              <div className="flex flex-wrap items-center gap-2">
                <span className="tabular flex size-6 items-center justify-center rounded-md bg-[var(--surface-3)] text-xs font-semibold text-[var(--text-secondary)]">
                  {stage.displayOrder + 1}
                </span>
                <h3 className="text-sm font-semibold text-[var(--text-primary)]">{stage.title}</h3>
                <Badge tone={statusTone[stage.status] ?? "neutral"}>{stage.status.toLowerCase()}</Badge>
                {stage.model ? <Badge tone="neutral">{stage.model}</Badge> : null}
                {stage.cached ? <Badge tone="info">from cache</Badge> : null}
                {stage.attemptCount > 1 ? <Badge tone="warning">retried</Badge> : null}
                <span className="ml-auto text-[0.6875rem] text-[var(--text-muted)]">
                  {formatDuration(stage.durationMs)}
                  {stage.waitedMs ? ` · waited ${formatDuration(stage.waitedMs)}` : ""}
                  {stage.outputTokens ? ` · ${stage.outputTokens} tokens out` : ""}
                </span>
              </div>

              {stage.summary ? (
                <p className="mt-2 text-[0.8125rem] text-[var(--text-secondary)]">{stage.summary}</p>
              ) : null}
              {stage.errorMessage ? (
                <p className="mt-2 rounded-md bg-[var(--status-critical-wash)] p-2 text-xs text-[var(--status-critical-ink)]">
                  {stage.errorMessage}
                </p>
              ) : null}
              {stage.notes.length > 0 ? (
                <ul className="mt-2 space-y-0.5">
                  {stage.notes.map((note, index) => (
                    <li key={index} className="text-[0.6875rem] leading-relaxed text-[var(--text-muted)]">
                      • {note}
                    </li>
                  ))}
                </ul>
              ) : null}

              {stage.promptText ? (
                <Disclosure className="mt-3" summary="The exact prompt sent">
                  <div className="flex justify-end">
                    <CopyButton value={stage.promptText} label="Copy prompt" />
                  </div>
                  <pre className="mono max-h-72 overflow-auto whitespace-pre-wrap break-words rounded bg-[var(--surface-inset)] p-3 text-[var(--text-secondary)]">
                    {stage.promptText}
                  </pre>
                </Disclosure>
              ) : null}
              {stage.responseText ? (
                <Disclosure className="mt-2" summary="The exact response received">
                  <div className="flex justify-end">
                    <CopyButton value={stage.responseText} label="Copy response" />
                  </div>
                  <pre className="mono max-h-72 overflow-auto whitespace-pre-wrap break-words rounded bg-[var(--surface-inset)] p-3 text-[var(--text-secondary)]">
                    {stage.responseText}
                  </pre>
                </Disclosure>
              ) : null}
            </Panel>
          </li>
        ))}
      </ol>

      <p className="text-[0.6875rem] text-[var(--text-muted)]">
        Run started {formatDateTime(run.startedAt)} · provider {run.provider} · model {run.model}
      </p>
    </div>
  );
}
