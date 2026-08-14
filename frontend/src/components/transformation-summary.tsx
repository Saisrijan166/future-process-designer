import {
  CountRow,
  HeroFigure,
  ResponsibilitySplitBar,
} from "@/components/viz";
import { humanise } from "@/lib/format";
import type { Comparison } from "@/lib/types";

function Column({
  step,
  title,
  subtitle,
  accent,
  children,
}: {
  step: string;
  title: string;
  subtitle: string;
  accent: string;
  children: React.ReactNode;
}) {
  return (
    <div className="flex flex-col rounded-xl border border-ink-200 bg-white p-4">
      <div className="flex items-center gap-2">
        <span className={`text-[11px] font-bold tracking-widest uppercase ${accent}`}>{step}</span>
      </div>
      <h3 className="mt-1 text-sm font-semibold text-ink-900">{title}</h3>
      <p className="mt-0.5 text-xs text-ink-500">{subtitle}</p>
      <div className="mt-3 flex-1">{children}</div>
    </div>
  );
}

/**
 * The three-column CURRENT → TRANSITION → FUTURE view, led by the two numbers that
 * actually answer "what would change?": how many steps there are before and after,
 * and how much of the work AI would take on.
 *
 * Every figure is a count of stored rows. That is the point of the whole schema, so
 * the summary is built from counts rather than from a paragraph of prose.
 */
export function TransformationSummary({ comparison }: { comparison: Comparison }) {
  const { summary } = comparison;
  const analysed = summary.futureActivityCount > 0;

  return (
    <section className="space-y-3">
      {analysed ? (
        <div className="rounded-xl border border-ink-200 bg-white p-5">
          <div className="flex flex-wrap items-start justify-between gap-6">
            <div>
              <p className="text-xs font-medium tracking-wide text-ink-500 uppercase">
                Steps in the process
              </p>
              <div className="mt-2">
                <HeroFigure
                  before={summary.currentActivityCount}
                  after={summary.futureActivityCount}
                  unit="steps"
                />
              </div>
              <p className="mt-2 text-xs text-ink-500">today → with AI</p>
            </div>

            <div className="min-w-64 flex-1">
              <p className="mb-2 text-xs font-medium tracking-wide text-ink-500 uppercase">
                Who does the work in the redesigned process
              </p>
              <ResponsibilitySplitBar
                counts={summary.futureActivitiesByResponsibility}
                total={summary.futureActivityCount}
              />
            </div>
          </div>
        </div>
      ) : null}

      <div className="grid gap-3 lg:grid-cols-3">
        <Column
          step="Now"
          title="How it runs today"
          subtitle="The steps, the people, the systems, the problems"
          accent="text-ink-400"
        >
          <CountRow label="Steps" value={summary.currentActivityCount} />
          <CountRow label="Roles involved" value={comparison.current.roles.length} />
          <CountRow label="Systems used" value={comparison.current.systems.length} />
          <CountRow label="Problems recorded" value={summary.problemCount} />
          {summary.problemCount > 0 ? (
            <SeverityRow counts={summary.problemsBySeverity} />
          ) : null}
        </Column>

        <Column
          step="Change"
          title="Where AI could help"
          subtitle="Reasoned ideas, each traced to evidence"
          accent="text-viz-augmented"
        >
          <CountRow label="AI ideas" value={summary.opportunityCount} />
          <CountRow label="Changes to the process" value={summary.interventionCount} />
          <CountRow label="Research sources used" value={summary.evidenceCount} />
          {summary.opportunityCount > 0 ? (
            <p className="mt-2 text-xs text-ink-500">
              {summary.opportunitiesByAutomationPotential.HIGH ?? 0} rated high automation potential
            </p>
          ) : null}
        </Column>

        <Column
          step="After"
          title="How it would run"
          subtitle="Redesigned steps, split between people and AI"
          accent="text-viz-automated"
        >
          <CountRow label="Steps" value={summary.futureActivityCount} />
          <CountRow
            label="Steps AI runs alone"
            value={summary.futureActivitiesByResponsibility.AI_AUTOMATED ?? 0}
          />
          <CountRow
            label="Steps AI assists with"
            value={summary.futureActivitiesByResponsibility.AI_AUGMENTED ?? 0}
          />
          <CountRow
            label="Steps a person still leads"
            value={summary.futureActivitiesByResponsibility.HUMAN_LED ?? 0}
          />
        </Column>
      </div>
    </section>
  );
}

/**
 * Severity is a status scale, so it is drawn with the reserved status colours — and
 * always beside its own label, because a status colour must never carry meaning alone.
 */
function SeverityRow({ counts }: { counts: Record<string, number> }) {
  const order = ["HIGH", "MEDIUM", "LOW"] as const;
  const fill: Record<string, string> = {
    HIGH: "var(--color-status-critical)",
    MEDIUM: "var(--color-status-warning)",
    LOW: "var(--color-ink-300)",
  };

  return (
    <div className="mt-2 flex flex-wrap gap-x-3 gap-y-1">
      {order
        .filter((level) => (counts[level] ?? 0) > 0)
        .map((level) => (
          <span key={level} className="inline-flex items-center gap-1.5 text-xs text-ink-600">
            <span
              aria-hidden="true"
              className="inline-block size-2 rounded-full"
              style={{ background: fill[level] }}
            />
            {humanise(level)}
            <span className="tabular font-semibold text-ink-900">{counts[level]}</span>
          </span>
        ))}
    </div>
  );
}
