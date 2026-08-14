import { CurrentFlow, FutureFlow } from "@/components/process-flow";
import { SnippetCard } from "@/components/snippet-card";
import { Badge, Card, EmptyState, SectionHeading } from "@/components/ui";
import { AutomationMeter, RelevanceMeter } from "@/components/viz";
import { humanise, interventionTone, severityTone } from "@/lib/format";
import type {
  Activity,
  AiIntervention,
  AiOpportunity,
  FutureActivity,
  Problem,
  RetrievedSnippet,
} from "@/lib/types";

/* ------------------------------------------------------------------ CURRENT */

export function CurrentTab({
  activities,
  problems,
}: {
  activities: Activity[];
  problems: Problem[];
}) {
  const processWideProblems = problems.filter((problem) => !problem.activityId);

  return (
    <div className="space-y-8">
      <section>
        <SectionHeading
          title="How it runs today"
          description="Each step in order, who performs it, which systems it touches, and what goes wrong."
        />
        <CurrentFlow activities={activities} />
      </section>

      {processWideProblems.length > 0 ? (
        <section>
          <SectionHeading
            title="Problems across the whole process"
            description="Pain points that are not tied to one particular step."
          />
          <ul className="space-y-2">
            {processWideProblems.map((problem) => (
              <Card as="li" key={problem.id} className="flex flex-wrap items-start gap-3 p-3">
                <Badge tone={severityTone[problem.severity]}>{humanise(problem.severity)}</Badge>
                <p className="flex-1 text-sm text-ink-700">{problem.description}</p>
                {problem.source === "AI_GENERATED" ? (
                  <Badge tone="accent" title="Spotted by the analysis, not recorded in advance">
                    Found by AI
                  </Badge>
                ) : null}
              </Card>
            ))}
          </ul>
        </section>
      ) : null}
    </div>
  );
}

/* --------------------------------------------------------------- TRANSITION */

export function TransitionTab({ opportunities }: { opportunities: AiOpportunity[] }) {
  if (opportunities.length === 0) {
    return (
      <EmptyState
        title="No AI ideas yet"
        description="Press Analyse at the top of the page to generate them."
      />
    );
  }

  return (
    <div className="space-y-4">
      <SectionHeading
        title={`Where AI could help — ${opportunities.length} ideas`}
        description="Each idea names the specific capability, what the business gains, what could go wrong, why it follows from this process, and which research backs it up."
      />
      <ol className="space-y-4">
        {opportunities.map((opportunity, index) => (
          <Card as="li" key={opportunity.id} className="overflow-hidden">
            <div className="grid gap-3 p-5 pb-4 sm:grid-cols-[1fr_auto] sm:items-start">
              <div className="flex min-w-0 gap-3">
                <span className="mt-0.5 grid size-7 shrink-0 place-items-center rounded-full bg-viz-automated-wash text-xs font-semibold text-viz-augmented">
                  {index + 1}
                </span>
                <div className="min-w-0">
                  <h3 className="text-sm leading-snug font-semibold text-ink-900">
                    {opportunity.description}
                  </h3>
                  <p className="mt-1.5 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-ink-500">
                    <span className="rounded-md bg-ink-100 px-1.5 py-0.5 font-medium text-ink-700">
                      {opportunity.aiCapability}
                    </span>
                    {opportunity.activityName ? (
                      <>applied to “{opportunity.activityName}”</>
                    ) : (
                      <>applies to the whole process</>
                    )}
                  </p>
                </div>
              </div>
              <div className="sm:pt-0.5">
                <AutomationMeter level={opportunity.automationPotential} />
              </div>
            </div>

            <dl className="grid gap-px border-t border-ink-100 bg-ink-100 sm:grid-cols-3">
              <Facet label="What the business gains" value={opportunity.businessBenefit} />
              <Facet label="What could go wrong" value={opportunity.risk} tone="risk" />
              <Facet label="Why this follows" value={opportunity.reasoningNote} />
            </dl>

            <div className="flex flex-wrap items-center gap-2 border-t border-ink-100 bg-ink-50/60 px-5 py-3">
              <span className="text-xs font-medium text-ink-600">Backed by:</span>
              {opportunity.evidence.length > 0 ? (
                opportunity.evidence.map((snippet) => (
                  <a
                    key={snippet.id}
                    href={snippet.sourceUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex items-center gap-1 rounded-full bg-white px-2.5 py-1 text-xs font-medium text-brand-700 ring-1 ring-brand-200 ring-inset transition-colors hover:bg-brand-50"
                  >
                    {snippet.title}
                    <span aria-hidden="true">↗</span>
                  </a>
                ))
              ) : (
                <span className="text-xs text-ink-500">
                  No source cited — this rests on the process description alone
                </span>
              )}
            </div>
          </Card>
        ))}
      </ol>
    </div>
  );
}

function Facet({
  label,
  value,
  tone = "neutral",
}: {
  label: string;
  value?: string | null;
  tone?: "neutral" | "risk";
}) {
  return (
    <div className="bg-white p-4">
      <dt
        className={`text-[11px] font-semibold tracking-wide uppercase ${
          tone === "risk" ? "text-status-critical-ink" : "text-ink-500"
        }`}
      >
        {label}
      </dt>
      <dd className="mt-1 text-xs leading-relaxed text-ink-700">
        {value?.trim() || <span className="text-ink-400">Not stated</span>}
      </dd>
    </div>
  );
}

/* ------------------------------------------------------------------- FUTURE */

export function FutureTab({
  futureActivities,
  interventions,
}: {
  futureActivities: FutureActivity[];
  interventions: AiIntervention[];
}) {
  if (futureActivities.length === 0) {
    return (
      <EmptyState
        title="No future process yet"
        description="Press Analyse at the top of the page to design it."
      />
    );
  }

  const unlinked = interventions.filter((intervention) => !intervention.futureActivityId);

  return (
    <div className="space-y-8">
      <section>
        <SectionHeading
          title={`The redesigned process — ${futureActivities.length} steps`}
          description="How the process would run with AI in it. Every step states what a person is still accountable for, what the AI does, and what changed from today."
        />
        <FutureFlow activities={futureActivities} />
      </section>

      {unlinked.length > 0 ? (
        <section>
          <SectionHeading
            title="Other changes"
            description="Changes that were not attached to a single step."
          />
          <ul className="space-y-2">
            {unlinked.map((intervention) => (
              <Card as="li" key={intervention.id} className="flex items-start gap-3 p-3">
                <Badge tone={interventionTone[intervention.interventionType]}>
                  {humanise(intervention.interventionType)}
                </Badge>
                <p className="text-sm text-ink-700">{intervention.description}</p>
              </Card>
            ))}
          </ul>
        </section>
      ) : null}
    </div>
  );
}

/* ----------------------------------------------------------------- EVIDENCE */

export function EvidenceTab({ evidence }: { evidence: RetrievedSnippet[] }) {
  if (evidence.length === 0) {
    return (
      <EmptyState
        title="No research selected yet"
        description="Sources are chosen when the analysis runs. Press Analyse to see which ones were used."
      />
    );
  }

  const maxScore = Math.max(...evidence.map((item) => item.relevanceScore), 0);

  return (
    <div className="space-y-4">
      <SectionHeading
        title={`Research used for this analysis — ${evidence.length} sources`}
        description="Out of the 16 sources in the library, these scored highest against this process and were shown to the AI. The score and the matched words explain why each was picked."
      />
      <ul className="grid gap-4 md:grid-cols-2">
        {evidence.map((retrieved) => (
          <SnippetCard
            key={retrieved.snippet.id}
            snippet={retrieved.snippet}
            footer={
              <div className="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1 rounded-lg bg-ink-50 px-3 py-2">
                <RelevanceMeter score={retrieved.relevanceScore} max={maxScore} />
                {retrieved.matchedTerms.length > 0 ? (
                  <span
                    className="text-[11px] text-ink-500"
                    title="Words shared between your process and this source, after trimming word endings"
                  >
                    matched on {retrieved.matchedTerms.slice(0, 8).join(", ")}
                  </span>
                ) : null}
              </div>
            }
          />
        ))}
      </ul>
    </div>
  );
}
