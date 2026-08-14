import { Badge, Card, EmptyState, SectionHeading, Text } from "@/components/ui";
import { SnippetCard } from "@/components/snippet-card";
import {
  automationTone,
  humanise,
  interventionTone,
  responsibilityTone,
  severityTone,
} from "@/lib/format";
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
    <div className="space-y-6">
      <section>
        <SectionHeading
          title="Current activities"
          description="The process as it runs today, with the people and systems involved at each step and the problems recorded against them."
        />
        <Card className="overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full min-w-4xl border-collapse text-left text-sm">
              <thead className="border-b border-ink-200 bg-ink-50 text-xs tracking-wide text-ink-600 uppercase">
                <tr>
                  <th scope="col" className="w-12 px-4 py-3 font-semibold">#</th>
                  <th scope="col" className="px-4 py-3 font-semibold">Activity</th>
                  <th scope="col" className="px-4 py-3 font-semibold">Roles</th>
                  <th scope="col" className="px-4 py-3 font-semibold">Systems</th>
                  <th scope="col" className="px-4 py-3 font-semibold">Problems</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-ink-100 align-top">
                {activities.map((activity) => (
                  <tr key={activity.id}>
                    <td className="px-4 py-3 tabular-nums text-ink-400">{activity.sequenceOrder}</td>
                    <th scope="row" className="max-w-sm px-4 py-3 text-left font-normal">
                      <p className="font-medium text-ink-900">{activity.name}</p>
                      {activity.description ? (
                        <p className="mt-1 text-xs leading-relaxed text-ink-600">
                          {activity.description}
                        </p>
                      ) : null}
                    </th>
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap gap-1">
                        {activity.roles.length > 0 ? (
                          activity.roles.map((role) => (
                            <Badge key={role} tone="neutral">
                              {role}
                            </Badge>
                          ))
                        ) : (
                          <span className="text-xs text-ink-400">Not recorded</span>
                        )}
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap gap-1">
                        {activity.systems.length > 0 ? (
                          activity.systems.map((system) => (
                            <Badge key={system} tone="info">
                              {system}
                            </Badge>
                          ))
                        ) : (
                          <span className="text-xs text-ink-400">Not recorded</span>
                        )}
                      </div>
                    </td>
                    <td className="max-w-sm px-4 py-3">
                      {activity.problems.length > 0 ? (
                        <ul className="space-y-1.5">
                          {activity.problems.map((problem) => (
                            <li key={problem.id} className="flex gap-2">
                              <Badge tone={severityTone[problem.severity]}>{problem.severity}</Badge>
                              <span className="text-xs leading-relaxed text-ink-700">
                                {problem.description}
                              </span>
                            </li>
                          ))}
                        </ul>
                      ) : (
                        <span className="text-xs text-ink-400">None</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      </section>

      {processWideProblems.length > 0 ? (
        <section>
          <SectionHeading
            title="Process-wide problems"
            description="Pain points that span the whole process rather than one step."
          />
          <ul className="space-y-2">
            {processWideProblems.map((problem) => (
              <Card as="li" key={problem.id} className="flex items-start gap-3 p-3">
                <Badge tone={severityTone[problem.severity]}>{problem.severity}</Badge>
                <p className="text-sm text-ink-700">{problem.description}</p>
                {problem.source === "AI_GENERATED" ? (
                  <Badge tone="accent" title="Identified by the analysis pipeline">
                    AI-identified
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
        title="No AI opportunities yet"
        description="Run the analysis to generate opportunities for this process."
      />
    );
  }

  return (
    <div className="space-y-4">
      <SectionHeading
        title={`AI opportunities (${opportunities.length})`}
        description="Each opportunity records the capability, the benefit, the risk, the reasoning, and the research sources that informed it."
      />
      <ol className="space-y-4">
        {opportunities.map((opportunity, index) => (
          <Card as="li" key={opportunity.id} className="p-5">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div className="flex gap-3">
                <span className="mt-0.5 grid size-6 shrink-0 place-items-center rounded-full bg-brand-50 text-xs font-semibold text-brand-700">
                  {index + 1}
                </span>
                <div>
                  <h3 className="text-sm font-semibold text-ink-900">{opportunity.description}</h3>
                  <p className="mt-1 text-xs text-ink-500">
                    Capability: <span className="text-ink-700">{opportunity.aiCapability}</span>
                    {opportunity.activityName ? (
                      <>
                        {" · "}Targets: <span className="text-ink-700">{opportunity.activityName}</span>
                      </>
                    ) : (
                      <>{" · "}Applies to the whole process</>
                    )}
                  </p>
                </div>
              </div>
              <Badge tone={automationTone[opportunity.automationPotential]}>
                {humanise(opportunity.automationPotential)} automation potential
              </Badge>
            </div>

            <dl className="mt-4 grid gap-3 sm:grid-cols-3">
              <div className="rounded-lg bg-emerald-50/60 p-3">
                <dt className="text-xs font-semibold text-emerald-900">Business benefit</dt>
                <dd className="mt-1 text-xs leading-relaxed text-ink-700">
                  <Text value={opportunity.businessBenefit} fallback="Not stated" />
                </dd>
              </div>
              <div className="rounded-lg bg-rose-50/60 p-3">
                <dt className="text-xs font-semibold text-rose-900">Risk</dt>
                <dd className="mt-1 text-xs leading-relaxed text-ink-700">
                  <Text value={opportunity.risk} fallback="Not stated" />
                </dd>
              </div>
              <div className="rounded-lg bg-ink-50 p-3">
                <dt className="text-xs font-semibold text-ink-800">Reasoning</dt>
                <dd className="mt-1 text-xs leading-relaxed text-ink-700">
                  <Text value={opportunity.reasoningNote} fallback="Not stated" />
                </dd>
              </div>
            </dl>

            <div className="mt-3 flex flex-wrap items-center gap-2 border-t border-ink-100 pt-3">
              <span className="text-xs font-medium text-ink-600">Evidence:</span>
              {opportunity.evidence.length > 0 ? (
                opportunity.evidence.map((snippet) => (
                  <a
                    key={snippet.id}
                    href={snippet.sourceUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="rounded-full bg-brand-50 px-2 py-0.5 text-xs font-medium text-brand-700 ring-1 ring-brand-200 ring-inset hover:bg-brand-100"
                  >
                    {snippet.title} ↗
                  </a>
                ))
              ) : (
                <span className="text-xs text-ink-400">
                  No cited source — this opportunity rests on the process description alone.
                </span>
              )}
            </div>
          </Card>
        ))}
      </ol>
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
        title="No future state yet"
        description="Run the analysis to design the AI-enabled future process."
      />
    );
  }

  const unlinked = interventions.filter((intervention) => !intervention.futureActivityId);

  return (
    <div className="space-y-6">
      <section>
        <SectionHeading
          title={`Future process (${futureActivities.length} steps)`}
          description="The redesigned process, step by step, with what a person stays accountable for and what the AI does."
        />
        <Card className="overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full min-w-4xl border-collapse text-left text-sm">
              <thead className="border-b border-ink-200 bg-ink-50 text-xs tracking-wide text-ink-600 uppercase">
                <tr>
                  <th scope="col" className="w-12 px-4 py-3 font-semibold">#</th>
                  <th scope="col" className="px-4 py-3 font-semibold">Future activity</th>
                  <th scope="col" className="px-4 py-3 font-semibold">Human responsibility</th>
                  <th scope="col" className="px-4 py-3 font-semibold">AI responsibility</th>
                  <th scope="col" className="px-4 py-3 font-semibold">What changed</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-ink-100 align-top">
                {futureActivities.map((activity) => (
                  <tr key={activity.id}>
                    <td className="px-4 py-3 tabular-nums text-ink-400">{activity.sequenceOrder}</td>
                    <th scope="row" className="max-w-xs px-4 py-3 text-left font-normal">
                      <p className="font-medium text-ink-900">{activity.name}</p>
                      <div className="mt-1">
                        <Badge tone={responsibilityTone[activity.responsibilityType]}>
                          {humanise(activity.responsibilityType)}
                        </Badge>
                      </div>
                      {activity.description ? (
                        <p className="mt-1.5 text-xs leading-relaxed text-ink-600">
                          {activity.description}
                        </p>
                      ) : null}
                    </th>
                    <td className="max-w-xs px-4 py-3 text-xs leading-relaxed text-ink-700">
                      <Text value={activity.humanResponsibility} fallback="Nothing — fully automated" />
                    </td>
                    <td className="max-w-xs px-4 py-3 text-xs leading-relaxed text-ink-700">
                      <Text value={activity.aiResponsibility} fallback="No AI in this step" />
                    </td>
                    <td className="max-w-xs px-4 py-3">
                      {activity.interventions.length > 0 ? (
                        <ul className="space-y-2">
                          {activity.interventions.map((intervention) => (
                            <li key={intervention.id}>
                              <Badge tone={interventionTone[intervention.interventionType]}>
                                {humanise(intervention.interventionType)}
                              </Badge>
                              <p className="mt-1 text-xs leading-relaxed text-ink-700">
                                {intervention.description}
                              </p>
                              {intervention.relatedAiOpportunitySummary ? (
                                <p className="mt-1 text-[11px] text-ink-500">
                                  From opportunity: {intervention.relatedAiOpportunitySummary}
                                </p>
                              ) : null}
                            </li>
                          ))}
                        </ul>
                      ) : (
                        <span className="text-xs text-ink-400">Unchanged</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      </section>

      {unlinked.length > 0 ? (
        <section>
          <SectionHeading
            title="Other interventions"
            description="Changes that were not attached to a single future step."
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
        title="No evidence retrieved yet"
        description="Grounding sources are selected when the analysis runs. Run it to see which sources informed this process."
      />
    );
  }

  return (
    <div className="space-y-4">
      <SectionHeading
        title={`Grounding sources (${evidence.length})`}
        description="These are the curated research snippets the retriever selected for this process and injected into the prompt. The score and matched terms explain why each one was chosen."
      />
      <ul className="grid gap-4 md:grid-cols-2">
        {evidence.map((retrieved) => (
          <SnippetCard
            key={retrieved.snippet.id}
            snippet={retrieved.snippet}
            footer={
              <div className="mt-2 flex flex-wrap items-center gap-2 rounded-md bg-ink-50 px-2 py-1.5">
                <span className="text-[11px] font-medium text-ink-600">
                  Relevance {retrieved.relevanceScore.toFixed(2)}
                </span>
                {retrieved.matchedTerms.length > 0 ? (
                  <span
                    className="text-[11px] text-ink-500"
                    title="Query terms matched, after stemming"
                  >
                    matched: {retrieved.matchedTerms.slice(0, 10).join(", ")}
                  </span>
                ) : (
                  <span className="text-[11px] text-ink-500">
                    no keyword match — included as general context
                  </span>
                )}
              </div>
            }
          />
        ))}
      </ul>
    </div>
  );
}
