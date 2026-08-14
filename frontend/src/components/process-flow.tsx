import { Badge } from "@/components/ui";
import {
  RESPONSIBILITY_CHIP,
  RESPONSIBILITY_LABEL,
  ResponsibilityDot,
} from "@/components/viz";
import { humanise, interventionTone, severityTone } from "@/lib/format";
import type { Activity, FutureActivity } from "@/lib/types";

/**
 * Both states are rendered as a vertical timeline rather than a table.
 *
 * A process is a sequence, and a table of five columns makes the reader reconstruct
 * that sequence from row order. A numbered spine shows it directly, and gives each
 * step room for the thing that actually matters in the future state: the two-column
 * split between what a person owns and what the AI does.
 */

function Spine({
  index,
  total,
  children,
  marker,
}: {
  index: number;
  total: number;
  children: React.ReactNode;
  marker: React.ReactNode;
}) {
  const isLast = index === total - 1;
  return (
    <li className="relative flex gap-4 pb-4 last:pb-0">
      {!isLast ? (
        <span
          aria-hidden="true"
          className="absolute top-9 bottom-0 left-[15px] w-px bg-ink-200"
        />
      ) : null}
      <div className="relative z-10 shrink-0">{marker}</div>
      <div className="min-w-0 flex-1">{children}</div>
    </li>
  );
}

function StepNumber({
  n,
  tone = "ink",
}: {
  n: number;
  tone?: "ink" | "future";
}) {
  return (
    <span
      className={`grid size-8 place-items-center rounded-full text-sm font-semibold ring-4 ring-ink-50 ${
        tone === "future"
          ? "bg-viz-automated text-white"
          : "bg-ink-200 text-ink-700"
      }`}
    >
      {n}
    </span>
  );
}

/* ------------------------------------------------------------------- CURRENT */

export function CurrentFlow({ activities }: { activities: Activity[] }) {
  return (
    <ol className="relative">
      {activities.map((activity, index) => (
        <Spine
          key={activity.id}
          index={index}
          total={activities.length}
          marker={<StepNumber n={activity.sequenceOrder} />}
        >
          <div className="rounded-xl border border-ink-200 bg-white p-4">
            <h3 className="text-sm font-semibold text-ink-900">
              {activity.name}
            </h3>
            {activity.description ? (
              <p className="mt-1 text-sm leading-relaxed text-ink-600">
                {activity.description}
              </p>
            ) : null}
            {activity.roles.length > 0 || activity.systems.length > 0 ? (
              <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-2 text-xs">
                {activity.roles.length > 0 ? (
                  <span className="flex flex-wrap items-center gap-1.5">
                    <span className="text-ink-500">Done by</span>
                    {activity.roles.map((role) => (
                      <span
                        key={role}
                        className="rounded-md bg-ink-100 px-1.5 py-0.5 font-medium text-ink-700"
                      >
                        {role}
                      </span>
                    ))}
                  </span>
                ) : null}
                {activity.systems.length > 0 ? (
                  <span className="flex flex-wrap items-center gap-1.5">
                    <span className="text-ink-500">Using</span>
                    {activity.systems.map((system) => (
                      <span
                        key={system}
                        className="rounded-md border border-ink-200 px-1.5 py-0.5 font-medium text-ink-600"
                      >
                        {system}
                      </span>
                    ))}
                  </span>
                ) : null}
              </div>
            ) : null}
            {activity.problems.length > 0 ? (
              <ul className="mt-3 space-y-1.5 border-t border-ink-100 pt-3">
                {activity.problems.map((problem) => (
                  <li key={problem.id} className="flex items-start gap-2">
                    <Badge tone={severityTone[problem.severity]}>
                      {humanise(problem.severity)}
                    </Badge>
                    <span className="text-xs leading-relaxed text-ink-700">
                      {problem.description}
                    </span>
                  </li>
                ))}
              </ul>
            ) : null}
          </div>
        </Spine>
      ))}
    </ol>
  );
}

/* -------------------------------------------------------------------- FUTURE */

export function FutureFlow({ activities }: { activities: FutureActivity[] }) {
  return (
    <ol className="relative">
      {activities.map((activity, index) => (
        <Spine
          key={activity.id}
          index={index}
          total={activities.length}
          marker={<StepNumber n={activity.sequenceOrder} tone="future" />}
        >
          <div className="overflow-hidden rounded-xl border border-ink-200 bg-white">
            <div className="flex flex-wrap items-start justify-between gap-3 p-4 pb-3">
              <div className="min-w-0">
                <h3 className="text-sm font-semibold text-ink-900">
                  {activity.name}
                </h3>
                {activity.description ? (
                  <p className="mt-1 text-sm leading-relaxed text-ink-600">
                    {activity.description}
                  </p>
                ) : null}
              </div>
              <span
                className={`inline-flex shrink-0 items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium ring-1 ring-inset ${
                  RESPONSIBILITY_CHIP[activity.responsibilityType]
                }`}
              >
                {activity.responsibilityType !== "AI_AUTOMATED" ? (
                  <ResponsibilityDot type={activity.responsibilityType} />
                ) : null}
                {RESPONSIBILITY_LABEL[activity.responsibilityType]}
              </span>
            </div>
            {/* The split is the point of the future state, so it gets its own panel. */}
            <div className="grid gap-px border-t border-ink-100 bg-ink-100 sm:grid-cols-2">
              <div className="bg-white p-4">
                <p className="flex items-center gap-1.5 text-[11px] font-semibold tracking-wide text-ink-500 uppercase">
                  <PersonIcon /> The person
                </p>
                <p className="mt-1.5 text-sm leading-relaxed text-ink-700">
                  {activity.humanResponsibility?.trim() || (
                    <span className="text-ink-400">
                      Nothing — this step runs unattended
                    </span>
                  )}
                </p>
              </div>
              <div className="bg-white p-4">
                <p className="flex items-center gap-1.5 text-[11px] font-semibold tracking-wide text-viz-augmented uppercase">
                  <SparkIcon /> The AI
                </p>
                <p className="mt-1.5 text-sm leading-relaxed text-ink-700">
                  {activity.aiResponsibility?.trim() || (
                    <span className="text-ink-400">No AI in this step</span>
                  )}
                </p>
              </div>
            </div>
            {activity.interventions.length > 0 ? (
              <div className="border-t border-ink-100 bg-ink-50/60 p-4">
                <p className="text-[11px] font-semibold tracking-wide text-ink-500 uppercase">
                  What changed from today
                </p>
                <ul className="mt-2 space-y-2">
                  {activity.interventions.map((intervention) => (
                    <li key={intervention.id}>
                      <div className="flex flex-wrap items-baseline gap-2">
                        <Badge
                          tone={interventionTone[intervention.interventionType]}
                        >
                          {humanise(intervention.interventionType)}
                        </Badge>
                        <span className="flex-1 text-xs leading-relaxed text-ink-700">
                          {intervention.description}
                        </span>
                      </div>
                      {intervention.relatedAiOpportunitySummary ? (
                        <p className="mt-1 text-[11px] text-ink-500">
                          Because of: {intervention.relatedAiOpportunitySummary}
                        </p>
                      ) : null}
                    </li>
                  ))}
                </ul>
              </div>
            ) : null}
          </div>
        </Spine>
      ))}
    </ol>
  );
}

function PersonIcon() {
  return (
    <svg
      className="size-3.5"
      viewBox="0 0 16 16"
      fill="none"
      aria-hidden="true"
    >
      <circle cx="8" cy="5" r="2.6" stroke="currentColor" strokeWidth="1.4" />
      <path
        d="M2.8 13.5a5.2 5.2 0 0 1 10.4 0"
        stroke="currentColor"
        strokeWidth="1.4"
        strokeLinecap="round"
      />
    </svg>
  );
}

function SparkIcon() {
  return (
    <svg
      className="size-3.5"
      viewBox="0 0 16 16"
      fill="none"
      aria-hidden="true"
    >
      <path
        d="M8 1.5l1.5 4L13.5 7 9.5 8.5 8 12.5 6.5 8.5 2.5 7l4-1.5L8 1.5Z"
        stroke="currentColor"
        strokeWidth="1.3"
        strokeLinejoin="round"
      />
    </svg>
  );
}
