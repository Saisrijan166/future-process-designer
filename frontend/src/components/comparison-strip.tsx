import { Badge } from "@/components/ui";
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
      <div className="flex items-baseline gap-2">
        <span className={`text-xs font-bold tracking-wider uppercase ${accent}`}>{step}</span>
        <h3 className="text-sm font-semibold text-ink-900">{title}</h3>
      </div>
      <p className="mt-0.5 text-xs text-ink-500">{subtitle}</p>
      <div className="mt-3 flex-1 space-y-2">{children}</div>
    </div>
  );
}

function CountRow({ label, value }: { label: string; value: number }) {
  return (
    <div className="flex items-baseline justify-between gap-3 border-b border-ink-100 pb-1.5 last:border-0">
      <span className="text-xs text-ink-600">{label}</span>
      <span className="text-lg font-semibold tabular-nums text-ink-900">{value}</span>
    </div>
  );
}

function Chips({ counts, tones }: { counts: Record<string, number>; tones: Record<string, "neutral" | "info" | "success" | "warning" | "danger" | "accent"> }) {
  const entries = Object.entries(counts);
  if (entries.length === 0) {
    return <p className="text-xs text-ink-400">Not analysed yet.</p>;
  }
  return (
    <div className="flex flex-wrap gap-1.5">
      {entries.map(([key, count]) => (
        <Badge key={key} tone={tones[key] ?? "neutral"}>
          {humanise(key)} · {count}
        </Badge>
      ))}
    </div>
  );
}

/**
 * The CURRENT → TRANSITION → FUTURE strip. Every figure is a count of stored rows, which is the
 * quickest way to show that the future process is data rather than a paragraph of prose.
 */
export function ComparisonStrip({ comparison }: { comparison: Comparison }) {
  const { summary } = comparison;

  return (
    <div className="grid gap-3 lg:grid-cols-3">
      <Column
        step="Current"
        title="How it runs today"
        subtitle="Activities, roles, systems and known pain points"
        accent="text-ink-500"
      >
        <CountRow label="Activities" value={summary.currentActivityCount} />
        <CountRow label="Roles involved" value={comparison.current.roles.length} />
        <CountRow label="Systems used" value={comparison.current.systems.length} />
        <div className="pt-1">
          <p className="mb-1.5 text-xs font-medium text-ink-600">
            Problems ({summary.problemCount})
          </p>
          <Chips
            counts={summary.problemsBySeverity}
            tones={{ LOW: "neutral", MEDIUM: "warning", HIGH: "danger" }}
          />
        </div>
      </Column>

      <Column
        step="Transition"
        title="Where AI changes it"
        subtitle="Reasoned opportunities, each traced to evidence"
        accent="text-brand-600"
      >
        <CountRow label="AI opportunities" value={summary.opportunityCount} />
        <CountRow label="Sources cited" value={summary.evidenceCount} />
        <div className="pt-1">
          <p className="mb-1.5 text-xs font-medium text-ink-600">Automation potential</p>
          <Chips
            counts={summary.opportunitiesByAutomationPotential}
            tones={{ LOW: "neutral", MEDIUM: "info", HIGH: "success" }}
          />
        </div>
        <div className="pt-1">
          <p className="mb-1.5 text-xs font-medium text-ink-600">
            Interventions ({summary.interventionCount})
          </p>
          <Chips
            counts={summary.interventionsByType}
            tones={{ AUTOMATE: "accent", AUGMENT: "info", ELIMINATE: "danger", NEW: "success" }}
          />
        </div>
      </Column>

      <Column
        step="Future"
        title="How it would run"
        subtitle="Redesigned steps with an explicit human/AI split"
        accent="text-emerald-600"
      >
        <CountRow label="Future activities" value={summary.futureActivityCount} />
        <CountRow
          label="Net change in steps"
          value={summary.futureActivityCount - summary.currentActivityCount}
        />
        <div className="pt-1">
          <p className="mb-1.5 text-xs font-medium text-ink-600">Who does the work</p>
          <Chips
            counts={summary.futureActivitiesByResponsibility}
            tones={{ AI_AUTOMATED: "accent", AI_AUGMENTED: "info", HUMAN_LED: "neutral" }}
          />
        </div>
      </Column>
    </div>
  );
}
