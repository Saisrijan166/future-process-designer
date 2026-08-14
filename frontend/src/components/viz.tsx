import type { ReactNode } from "react";
import type { AutomationPotential, ResponsibilityType } from "@/lib/types";

/* ---------------------------------------------------------------- stat tiles */

/**
 * A single headline number. Deliberately not a one-bar chart — when the data is one
 * value, the number *is* the visualisation.
 */
export function StatTile({
  label,
  value,
  hint,
  emphasis = false,
}: {
  label: string;
  value: ReactNode;
  hint?: string;
  emphasis?: boolean;
}) {
  return (
    <div
      className={`rounded-xl border px-4 py-3 ${
        emphasis ? "border-ink-300 bg-white shadow-xs" : "border-ink-200 bg-white"
      }`}
    >
      <dt className="text-xs font-medium tracking-wide text-ink-500 uppercase">{label}</dt>
      <dd className="mt-1 text-2xl font-semibold text-ink-900">{value}</dd>
      {hint ? <p className="mt-0.5 text-xs text-ink-500">{hint}</p> : null}
    </div>
  );
}

/** The one number a view leads with. Exactly one per screen. */
export function HeroFigure({
  before,
  after,
  unit,
}: {
  before: number;
  after: number;
  unit: string;
}) {
  const delta = after - before;
  const deltaLabel = delta === 0 ? "no change" : `${delta > 0 ? "+" : ""}${delta}`;

  return (
    <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
      <span className="text-5xl leading-none font-semibold text-ink-900">{before}</span>
      <svg
        className="size-5 shrink-0 self-center text-ink-400"
        viewBox="0 0 20 20"
        fill="none"
        aria-hidden="true"
      >
        <path d="M3 10h13m0 0-4.5-4.5M16 10l-4.5 4.5" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      <span className="text-5xl leading-none font-semibold text-ink-900">{after}</span>
      <span className="text-sm text-ink-600">{unit}</span>
      {delta !== 0 ? (
        <span className="rounded-full bg-ink-100 px-2 py-0.5 text-xs font-medium text-ink-600">
          {deltaLabel}
        </span>
      ) : null}
    </div>
  );
}

/* ------------------------------------------------------------- responsibility */

export const RESPONSIBILITY_ORDER: ResponsibilityType[] = [
  "HUMAN_LED",
  "AI_AUGMENTED",
  "AI_AUTOMATED",
];

export const RESPONSIBILITY_LABEL: Record<ResponsibilityType, string> = {
  HUMAN_LED: "Person leads",
  AI_AUGMENTED: "AI assists, person decides",
  AI_AUTOMATED: "AI runs it",
};

export const RESPONSIBILITY_SHORT: Record<ResponsibilityType, string> = {
  HUMAN_LED: "Person leads",
  AI_AUGMENTED: "AI assists",
  AI_AUTOMATED: "AI runs it",
};

/** Ordinal ramp: light = human, dark = AI. One hue, validated light→dark. */
const RESPONSIBILITY_FILL: Record<ResponsibilityType, string> = {
  HUMAN_LED: "var(--color-viz-human)",
  AI_AUGMENTED: "var(--color-viz-augmented)",
  AI_AUTOMATED: "var(--color-viz-automated)",
};

/** Text/border treatment for the same three states, used on chips beside a label. */
export const RESPONSIBILITY_CHIP: Record<ResponsibilityType, string> = {
  HUMAN_LED: "bg-viz-human-wash text-ink-700 ring-viz-human/50",
  AI_AUGMENTED: "bg-viz-automated-wash text-viz-augmented ring-viz-augmented/30",
  AI_AUTOMATED: "bg-viz-automated text-white ring-viz-automated",
};

export function ResponsibilityDot({ type }: { type: ResponsibilityType }) {
  return (
    <span
      aria-hidden="true"
      className="inline-block size-2.5 shrink-0 rounded-full ring-2 ring-white"
      style={{ background: RESPONSIBILITY_FILL[type] }}
    />
  );
}

/**
 * Part-to-whole across the three responsibility states.
 *
 * Stacked bar rather than a pie: the categories are ordered (human → AI), there are
 * only three, and the reader's job is "how much of this process would AI take on?".
 * Segments carry a 2px surface gap, the data ends are rounded, and every visible
 * segment is direct-labelled — so identity never rests on colour alone.
 */
export function ResponsibilitySplitBar({
  counts,
  total,
  compact = false,
}: {
  counts: Record<string, number>;
  total: number;
  compact?: boolean;
}) {
  const present = RESPONSIBILITY_ORDER.filter((type) => (counts[type] ?? 0) > 0);

  if (total === 0 || present.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-ink-300 px-3 py-2 text-xs text-ink-500">
        Not analysed yet — no split to show.
      </div>
    );
  }

  return (
    <figure className="space-y-2">
      <div
        className={`flex w-full gap-[2px] overflow-hidden ${compact ? "h-2.5" : "h-4"}`}
        role="img"
        aria-label={present
          .map((type) => `${RESPONSIBILITY_LABEL[type]}: ${counts[type]} of ${total} steps`)
          .join(". ")}
      >
        {present.map((type, index) => {
          const count = counts[type] ?? 0;
          const isFirst = index === 0;
          const isLast = index === present.length - 1;
          return (
            <div
              key={type}
              title={`${RESPONSIBILITY_LABEL[type]} — ${count} of ${total} steps`}
              className="h-full transition-[flex-grow] duration-500"
              style={{
                flexGrow: count,
                flexBasis: 0,
                background: RESPONSIBILITY_FILL[type],
                borderTopLeftRadius: isFirst ? 4 : 0,
                borderBottomLeftRadius: isFirst ? 4 : 0,
                borderTopRightRadius: isLast ? 4 : 0,
                borderBottomRightRadius: isLast ? 4 : 0,
              }}
            />
          );
        })}
      </div>

      {/* Every state is listed, including the ones with no steps — "0 run by AI alone" is as
          much of an answer as "3 assisted", and hiding it would overstate the change. */}
      <figcaption className="flex flex-wrap gap-x-4 gap-y-1">
        {RESPONSIBILITY_ORDER.map((type) => {
          const count = counts[type] ?? 0;
          return (
            <span
              key={type}
              className={`inline-flex items-center gap-1.5 text-xs ${
                count > 0 ? "text-ink-600" : "text-ink-400"
              }`}
            >
              {count > 0 ? (
                <ResponsibilityDot type={type} />
              ) : (
                <span aria-hidden="true" className="inline-block size-2.5 rounded-full bg-ink-200" />
              )}
              {RESPONSIBILITY_SHORT[type]}
              <span className={`tabular font-semibold ${count > 0 ? "text-ink-900" : "text-ink-400"}`}>
                {count}
              </span>
            </span>
          );
        })}
      </figcaption>
    </figure>
  );
}

/* -------------------------------------------------------- automation potential */

const AUTOMATION_STEPS: AutomationPotential[] = ["LOW", "MEDIUM", "HIGH"];

const AUTOMATION_FILL: Record<AutomationPotential, string> = {
  LOW: "var(--color-viz-auto-low)",
  MEDIUM: "var(--color-viz-auto-medium)",
  HIGH: "var(--color-viz-auto-high)",
};

export const AUTOMATION_LABEL: Record<AutomationPotential, string> = {
  LOW: "Low",
  MEDIUM: "Medium",
  HIGH: "High",
};

/**
 * A three-segment meter for automation potential. A ratio against a fixed limit is a
 * meter, not a chart; the unfilled track is a lighter step of the same hue so the
 * state reads across the whole bar rather than only in the filled part.
 */
export function AutomationMeter({ level }: { level: AutomationPotential }) {
  const filled = AUTOMATION_STEPS.indexOf(level) + 1;

  return (
    <div className="inline-flex items-center gap-2" title={`Automation potential: ${AUTOMATION_LABEL[level]}`}>
      <span className="flex gap-[2px]" role="img" aria-label={`Automation potential ${AUTOMATION_LABEL[level]} of high`}>
        {AUTOMATION_STEPS.map((step, index) => (
          <span
            key={step}
            className="h-1.5 w-5 rounded-full first:rounded-l-[4px] last:rounded-r-[4px]"
            style={{
              background: index < filled ? AUTOMATION_FILL[level] : "var(--color-viz-auto-track)",
            }}
          />
        ))}
      </span>
      {/* The label wears an ink token, not the mark's colour: the light end of the ramp is
          ~2:1 against white, which is fine for a 6px bar and unreadable as text. */}
      <span className="text-xs font-medium whitespace-nowrap text-ink-700">
        {AUTOMATION_LABEL[level]} automation potential
      </span>
    </div>
  );
}

/* ---------------------------------------------------------------- score meter */

/**
 * Relevance of a retrieved source, as a proportion of the best score in the set.
 * Zero is a real and meaningful value here — it means the retriever found no keyword
 * match and included the source only as general background — so it is labelled
 * rather than drawn as an empty bar.
 */
export function RelevanceMeter({ score, max }: { score: number; max: number }) {
  if (score <= 0) {
    return (
      <span className="text-[11px] text-ink-500">
        No keyword match — included as general background
      </span>
    );
  }
  const ratio = max > 0 ? Math.max(0.08, score / max) : 0;

  return (
    <span className="inline-flex items-center gap-2" title={`Relevance score ${score.toFixed(2)}`}>
      <span className="relative h-1.5 w-16 overflow-hidden rounded-full bg-viz-human-wash">
        <span
          className="absolute inset-y-0 left-0 rounded-full"
          style={{ width: `${ratio * 100}%`, background: "var(--color-viz-augmented)" }}
        />
      </span>
      <span className="tabular text-[11px] font-medium text-ink-600">{score.toFixed(2)}</span>
    </span>
  );
}

/* -------------------------------------------------------------------- counter */

/** A labelled count with a rule beneath it — used in the three transformation columns. */
export function CountRow({
  label,
  value,
  tone = "ink",
}: {
  label: string;
  value: number;
  tone?: "ink" | "muted";
}) {
  return (
    <div className="flex items-baseline justify-between gap-3 border-b border-ink-100 py-1.5 last:border-0">
      <span className={`text-xs ${tone === "muted" ? "text-ink-500" : "text-ink-600"}`}>{label}</span>
      <span className="tabular text-base font-semibold text-ink-900">{value}</span>
    </div>
  );
}
