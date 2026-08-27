"use client";

import { useId, useState, type ReactNode } from "react";

/**
 * Every chart in the application, drawn as inline SVG.
 *
 * <p>No charting library, for three reasons that all showed up in the requirements. The data here
 * is small and oddly shaped — six future steps, five recommendations, a 5×5 risk grid — so a
 * general-purpose library spends its weight on cases this application does not have. The marks need
 * behaviour a library would fight: a bar that carries a verification state, a matrix cell that
 * opens a drawer. And every colour has to come from the validated tokens in {@code globals.css}
 * rather than from a library's default palette.
 *
 * <p>Shared rules, applied by every chart below:
 *
 * <ul>
 *   <li>Colour is assigned by job. Ordered scales use one hue, light to dark. Status uses the
 *       reserved four, always with a label beside them.
 *   <li>Two or more series always get a legend, and short lists are direct-labelled as well, so
 *       identity never depends on colour alone.
 *   <li>Values are labelled selectively — at the ends and on hover — not printed on every mark.
 *   <li>Adjacent fills are separated by a 2px gap in the surface colour, so touching segments read
 *       as two marks rather than one.
 *   <li>A table view exists for anything a screen reader cannot get from the marks.
 * </ul>
 */

// ---------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------

export function ChartFrame({
  title,
  subtitle,
  legend,
  children,
  footnote,
  className = "",
}: {
  title?: string;
  subtitle?: string;
  legend?: ReactNode;
  children: ReactNode;
  footnote?: ReactNode;
  className?: string;
}) {
  return (
    <figure className={`m-0 ${className}`}>
      {title ? (
        <figcaption className="mb-2">
          <p className="text-[0.8125rem] font-semibold text-[var(--text-primary)]">{title}</p>
          {subtitle ? <p className="mt-0.5 text-xs text-[var(--text-muted)]">{subtitle}</p> : null}
        </figcaption>
      ) : null}
      {legend ? <div className="mb-2.5 flex flex-wrap gap-x-4 gap-y-1">{legend}</div> : null}
      {children}
      {footnote ? <p className="mt-2 text-[0.6875rem] text-[var(--text-muted)]">{footnote}</p> : null}
    </figure>
  );
}

export function LegendSwatch({ colour, label, count }: { colour: string; label: string; count?: number }) {
  return (
    <span className="inline-flex items-center gap-1.5 text-[0.6875rem] text-[var(--text-secondary)]">
      <span className="size-2.5 rounded-[3px]" style={{ backgroundColor: colour }} aria-hidden="true" />
      {label}
      {count != null ? <span className="tabular text-[var(--text-muted)]">({count})</span> : null}
    </span>
  );
}

/** The ordered human → AI scale. One hue, three steps, dark end = most automated. */
export const RESPONSIBILITY_COLOURS = {
  HUMAN_LED: "var(--resp-human)",
  AI_AUGMENTED: "var(--resp-augmented)",
  AI_AUTOMATED: "var(--resp-automated)",
} as const;

export const RESPONSIBILITY_LABELS = {
  HUMAN_LED: "Human-led",
  AI_AUGMENTED: "AI-augmented",
  AI_AUTOMATED: "AI-automated",
} as const;

/** The second ordered scale on screen, so a different hue rather than a second blue. */
export const AUTOMATION_COLOURS = {
  LOW: "var(--auto-low)",
  MEDIUM: "var(--auto-medium)",
  HIGH: "var(--auto-high)",
} as const;

// ---------------------------------------------------------------------------
// Stat tile
// ---------------------------------------------------------------------------

/**
 * A single number, which is often the right chart.
 *
 * <p>{@code hint} exists so a figure can carry its own caveat. "Rs 4.2 lakh a month" beside "on
 * estimated inputs" is a different claim from the number alone, and the second half is the part
 * that keeps the first honest.
 */
export function StatTile({
  label,
  value,
  hint,
  tone = "neutral",
  spark,
}: {
  label: string;
  value: ReactNode;
  hint?: ReactNode;
  tone?: "neutral" | "good" | "warning" | "critical";
  spark?: ReactNode;
}) {
  const accent = {
    neutral: "var(--text-primary)",
    good: "var(--status-good-ink)",
    warning: "var(--status-warning-ink)",
    critical: "var(--status-critical-ink)",
  }[tone];

  return (
    <div className="panel-quiet p-3.5">
      <p className="eyebrow">{label}</p>
      <p className="tabular mt-1.5 text-2xl font-semibold leading-none" style={{ color: accent }}>
        {value}
      </p>
      {spark ? <div className="mt-2">{spark}</div> : null}
      {hint ? <p className="mt-1.5 text-[0.6875rem] leading-snug text-[var(--text-muted)]">{hint}</p> : null}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Score meter
// ---------------------------------------------------------------------------

/**
 * A 0-100 measurement as a track and a fill.
 *
 * <p>Deliberately not a gauge or a donut. Both encode one number in an angle, which is harder to
 * read and much harder to compare across a stack of six — and comparing the six is the entire point
 * of the scorecard.
 */
export function ScoreMeter({
  label,
  value,
  hint,
  max = 100,
}: {
  label: string;
  value: number;
  hint?: string;
  max?: number;
}) {
  const fraction = Math.max(0, Math.min(1, value / max));
  // One hue, stepped by magnitude: this is an ordered scale, not four categories.
  const colour =
    fraction >= 0.85
      ? "var(--seq-700)"
      : fraction >= 0.6
        ? "var(--seq-550)"
        : fraction >= 0.35
          ? "var(--seq-400)"
          : "var(--seq-250)";

  return (
    <div>
      <div className="flex items-baseline justify-between gap-2">
        <span className="text-xs font-medium text-[var(--text-secondary)]">{label}</span>
        <span className="tabular text-xs font-semibold text-[var(--text-primary)]">{Math.round(value)}</span>
      </div>
      <div
        className="mt-1.5 h-2 overflow-hidden rounded-full bg-[var(--surface-inset)]"
        role="meter"
        aria-valuenow={Math.round(value)}
        aria-valuemin={0}
        aria-valuemax={max}
        aria-label={label}
      >
        <div
          className="h-full rounded-full transition-[width] duration-500"
          style={{ width: `${fraction * 100}%`, backgroundColor: colour }}
        />
      </div>
      {hint ? <p className="mt-1 text-[0.6875rem] leading-snug text-[var(--text-muted)]">{hint}</p> : null}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Stacked responsibility bar
// ---------------------------------------------------------------------------

export interface Segment {
  key: string;
  label: string;
  value: number;
  colour: string;
}

/**
 * One horizontal bar split into ordered segments.
 *
 * <p>The headline of the whole redesign: how much of the process a person still owns. A stacked bar
 * is right here because the parts sum to a meaningful whole — every step is in exactly one
 * category — and the reader's question is about proportion rather than about each count.
 */
export function StackedBar({
  segments,
  height = 28,
  showLabels = true,
}: {
  segments: Segment[];
  height?: number;
  showLabels?: boolean;
}) {
  const total = segments.reduce((sum, segment) => sum + segment.value, 0);
  if (total === 0) {
    return (
      <div
        className="rounded-md bg-[var(--surface-inset)]"
        style={{ height }}
        role="img"
        aria-label="No steps to show"
      />
    );
  }

  return (
    <div>
      <div className="flex w-full overflow-hidden rounded-md" style={{ height }} role="img"
        aria-label={segments.map((segment) => `${segment.label}: ${segment.value}`).join(", ")}>
        {segments
          .filter((segment) => segment.value > 0)
          .map((segment, index, visible) => (
            <div
              key={segment.key}
              className="relative flex items-center justify-center transition-[flex-grow] duration-500"
              style={{
                flexGrow: segment.value,
                backgroundColor: segment.colour,
                // A 2px gap in the surface colour, so two touching segments read as two marks.
                marginRight: index < visible.length - 1 ? 2 : 0,
              }}
              title={`${segment.label}: ${segment.value}`}
            >
              {showLabels && segment.value / total > 0.12 ? (
                <span className="tabular text-[0.6875rem] font-semibold text-white drop-shadow-[0_1px_1px_rgba(0,0,0,0.35)]">
                  {segment.value}
                </span>
              ) : null}
            </div>
          ))}
      </div>
      <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1">
        {segments.map((segment) => (
          <LegendSwatch key={segment.key} colour={segment.colour} label={segment.label} count={segment.value} />
        ))}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Horizontal bars
// ---------------------------------------------------------------------------

export interface BarDatum {
  key: string;
  label: string;
  value: number;
  colour?: string;
  note?: string;
}

/**
 * Ranked horizontal bars.
 *
 * <p>Horizontal rather than vertical because the categories are sentences — an intervention's name,
 * a source's domain — and a vertical bar chart with rotated labels is a chart nobody reads.
 */
export function BarList({
  data,
  format = (value) => value.toLocaleString("en-IN"),
  colour = "var(--seq-400)",
  emptyMessage = "Nothing to show yet.",
}: {
  data: BarDatum[];
  format?: (value: number) => string;
  colour?: string;
  emptyMessage?: string;
}) {
  if (data.length === 0) {
    return <p className="py-6 text-center text-xs text-[var(--text-muted)]">{emptyMessage}</p>;
  }
  const max = Math.max(...data.map((datum) => datum.value), 1);

  return (
    <ul className="space-y-2.5">
      {data.map((datum) => (
        <li key={datum.key}>
          <div className="flex items-baseline justify-between gap-3">
            <span className="truncate text-xs text-[var(--text-secondary)]" title={datum.label}>
              {datum.label}
            </span>
            <span className="tabular shrink-0 text-xs font-medium text-[var(--text-primary)]">
              {format(datum.value)}
            </span>
          </div>
          <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-[var(--surface-inset)]">
            <div
              className="h-full rounded-full transition-[width] duration-500"
              style={{
                width: `${Math.max(2, (datum.value / max) * 100)}%`,
                backgroundColor: datum.colour ?? colour,
              }}
            />
          </div>
          {datum.note ? (
            <p className="mt-0.5 text-[0.6875rem] text-[var(--text-muted)]">{datum.note}</p>
          ) : null}
        </li>
      ))}
    </ul>
  );
}

// ---------------------------------------------------------------------------
// Impact / effort matrix
// ---------------------------------------------------------------------------

export interface MatrixPoint {
  id: string;
  label: string;
  /** 0-5, higher is more valuable. */
  impact: number;
  /** 0-5, higher is more work. */
  effort: number;
  tone?: string;
  detail?: string;
}

/**
 * Recommendations plotted by what they are worth against what they cost.
 *
 * <p>A scatter is the right form: the question is which items sit in the top-left, and that is a
 * two-dimensional question no bar chart answers. Points are labelled directly rather than through a
 * legend — with five or six items, a legend would be a lookup table for something the chart can
 * simply say.
 */
export function ImpactEffortMatrix({
  points,
  onSelect,
}: {
  points: MatrixPoint[];
  onSelect?: (id: string) => void;
}) {
  const [hovered, setHovered] = useState<string | null>(null);
  const width = 460;
  const height = 300;
  const padding = { top: 16, right: 16, bottom: 34, left: 44 };
  const plotWidth = width - padding.left - padding.right;
  const plotHeight = height - padding.top - padding.bottom;
  const gridId = useId();

  const x = (effort: number) => padding.left + (effort / 5) * plotWidth;
  const y = (impact: number) => padding.top + plotHeight - (impact / 5) * plotHeight;

  if (points.length === 0) {
    return <p className="py-8 text-center text-xs text-[var(--text-muted)]">Nothing to plot yet.</p>;
  }

  return (
    <div className="overflow-x-auto">
      <svg
        viewBox={`0 0 ${width} ${height}`}
        className="w-full min-w-[340px]"
        role="img"
        aria-label="Recommendations plotted by business impact against implementation effort"
      >
        <defs>
          <clipPath id={gridId}>
            <rect x={padding.left} y={padding.top} width={plotWidth} height={plotHeight} />
          </clipPath>
        </defs>

        {[0, 1, 2, 3, 4, 5].map((tick) => (
          <g key={`grid-${tick}`}>
            <line
              x1={x(tick)}
              y1={padding.top}
              x2={x(tick)}
              y2={padding.top + plotHeight}
              stroke="var(--chart-grid)"
              strokeWidth={1}
            />
            <line
              x1={padding.left}
              y1={y(tick)}
              x2={padding.left + plotWidth}
              y2={y(tick)}
              stroke="var(--chart-grid)"
              strokeWidth={1}
            />
          </g>
        ))}

        {/* The quadrant worth acting on first, named rather than left to be inferred. */}
        <rect
          x={padding.left}
          y={padding.top}
          width={plotWidth / 2}
          height={plotHeight / 2}
          fill="var(--seq-400)"
          opacity={0.06}
          clipPath={`url(#${gridId})`}
        />
        <text
          x={padding.left + 8}
          y={padding.top + 16}
          className="text-[9px]"
          fill="var(--text-muted)"
          fontSize={9}
        >
          Do first: high value, low effort
        </text>

        <line
          x1={padding.left}
          y1={padding.top + plotHeight}
          x2={padding.left + plotWidth}
          y2={padding.top + plotHeight}
          stroke="var(--chart-axis)"
          strokeWidth={1}
        />
        <line
          x1={padding.left}
          y1={padding.top}
          x2={padding.left}
          y2={padding.top + plotHeight}
          stroke="var(--chart-axis)"
          strokeWidth={1}
        />

        <text
          x={padding.left + plotWidth / 2}
          y={height - 6}
          textAnchor="middle"
          fontSize={10}
          fill="var(--text-muted)"
        >
          Implementation effort →
        </text>
        <text
          x={-(padding.top + plotHeight / 2)}
          y={12}
          transform="rotate(-90)"
          textAnchor="middle"
          fontSize={10}
          fill="var(--text-muted)"
        >
          Business impact →
        </text>

        {points.map((point, index) => {
          const isHovered = hovered === point.id;
          return (
            <g
              key={point.id}
              onMouseEnter={() => setHovered(point.id)}
              onMouseLeave={() => setHovered(null)}
              onClick={() => onSelect?.(point.id)}
              className={onSelect ? "cursor-pointer" : undefined}
            >
              {/* A hit target larger than the mark, so a 9px dot is still clickable. */}
              <circle cx={x(point.effort)} cy={y(point.impact)} r={16} fill="transparent" />
              <circle
                cx={x(point.effort)}
                cy={y(point.impact)}
                r={isHovered ? 8 : 6}
                fill={point.tone ?? "var(--cat-1)"}
                stroke="var(--chart-surface)"
                strokeWidth={2}
                className="transition-all"
              />
              <text
                x={x(point.effort) + 11}
                y={y(point.impact) + 3.5}
                fontSize={10}
                fill={isHovered ? "var(--text-primary)" : "var(--text-secondary)"}
                fontWeight={isHovered ? 600 : 400}
              >
                {index + 1}
              </text>
            </g>
          );
        })}
      </svg>

      <ol className="mt-2 space-y-1">
        {points.map((point, index) => (
          <li key={point.id} className="flex gap-2 text-[0.6875rem] text-[var(--text-secondary)]">
            <span className="tabular w-3.5 shrink-0 font-semibold text-[var(--text-muted)]">{index + 1}</span>
            <span className="min-w-0 flex-1">
              {point.label}
              {point.detail ? <span className="text-[var(--text-muted)]"> — {point.detail}</span> : null}
            </span>
          </li>
        ))}
      </ol>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Risk heat grid
// ---------------------------------------------------------------------------

export interface RiskCell {
  id: string;
  title: string;
  likelihood: number;
  impact: number;
  category: string;
}

/**
 * The 5×5 likelihood-by-impact grid a risk reviewer expects to see.
 *
 * <p>Sequential, one hue: severity is a magnitude, so it gets a single ramp rather than a
 * traffic-light scheme that would collide with the reserved status colours. Counts are printed in
 * every populated cell, so the grid is readable without relying on the shade at all.
 */
export function RiskGrid({
  risks,
  onSelect,
}: {
  risks: RiskCell[];
  onSelect?: (id: string) => void;
}) {
  const cells = new Map<string, RiskCell[]>();
  for (const risk of risks) {
    const key = `${Math.max(1, Math.min(5, risk.likelihood))}-${Math.max(1, Math.min(5, risk.impact))}`;
    cells.set(key, [...(cells.get(key) ?? []), risk]);
  }

  const shadeFor = (severity: number) => {
    if (severity >= 20) return "var(--seq-700)";
    if (severity >= 12) return "var(--seq-550)";
    if (severity >= 6) return "var(--seq-400)";
    if (severity >= 3) return "var(--seq-250)";
    return "var(--seq-100)";
  };

  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[300px] border-separate border-spacing-0.5">
        <caption className="sr-only">Risks by likelihood and impact</caption>
        <thead>
          <tr>
            <th scope="col" className="w-16" />
            {[1, 2, 3, 4, 5].map((impact) => (
              <th key={impact} scope="col" className="pb-1 text-center text-[0.6875rem] font-medium text-[var(--text-muted)]">
                {impact}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {[5, 4, 3, 2, 1].map((likelihood) => (
            <tr key={likelihood}>
              <th
                scope="row"
                className="pr-2 text-right text-[0.6875rem] font-medium text-[var(--text-muted)]"
              >
                {likelihood}
              </th>
              {[1, 2, 3, 4, 5].map((impact) => {
                const key = `${likelihood}-${impact}`;
                const inCell = cells.get(key) ?? [];
                const severity = likelihood * impact;
                return (
                  <td key={key} className="p-0">
                    <button
                      type="button"
                      disabled={inCell.length === 0}
                      onClick={() => inCell[0] && onSelect?.(inCell[0].id)}
                      title={
                        inCell.length
                          ? inCell.map((risk) => `${risk.title} (${risk.category})`).join("\n")
                          : `No risks at likelihood ${likelihood}, impact ${impact}`
                      }
                      className="flex aspect-square w-full items-center justify-center rounded transition-transform enabled:hover:scale-[1.06] disabled:cursor-default"
                      style={{
                        backgroundColor: inCell.length ? shadeFor(severity) : "var(--surface-inset)",
                      }}
                    >
                      {inCell.length ? (
                        <span
                          className="tabular text-xs font-semibold"
                          style={{ color: severity >= 12 ? "#ffffff" : "var(--text-primary)" }}
                        >
                          {inCell.length}
                        </span>
                      ) : null}
                    </button>
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
      <div className="mt-1.5 flex justify-between text-[0.6875rem] text-[var(--text-muted)]">
        <span>Likelihood ↑ · Impact →</span>
        <span>Cell shows how many risks sit there</span>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Roadmap
// ---------------------------------------------------------------------------

export interface RoadmapBar {
  id: string;
  title: string;
  wave: number;
  durationWeeks: number | null;
  effort: string;
  impact: string;
}

/**
 * The delivery plan as a wave-by-wave timeline.
 *
 * <p>Bars start where their wave starts rather than at a real date, because the plan has no start
 * date and inventing one would be a fiction the interface then has to keep up. What it does show
 * honestly is sequence and relative length.
 */
export function RoadmapTimeline({ items }: { items: RoadmapBar[] }) {
  if (items.length === 0) {
    return <p className="py-6 text-center text-xs text-[var(--text-muted)]">No plan was produced.</p>;
  }

  const waves = [...new Set(items.map((item) => item.wave))].sort((a, b) => a - b);
  const waveStart = new Map<number, number>();
  let cursor = 0;
  for (const wave of waves) {
    waveStart.set(wave, cursor);
    const longest = Math.max(
      ...items.filter((item) => item.wave === wave).map((item) => item.durationWeeks ?? 4),
    );
    cursor += longest;
  }
  const totalWeeks = Math.max(cursor, 1);

  const waveColour = (wave: number) =>
    wave <= 1 ? "var(--seq-400)" : wave === 2 ? "var(--seq-550)" : "var(--seq-700)";

  return (
    <div className="space-y-4">
      {waves.map((wave) => (
        <div key={wave}>
          <p className="eyebrow mb-1.5">
            Wave {wave}
            {wave === 1 ? " — startable now" : wave === 2 ? " — after wave 1 lands" : " — needs the earlier waves"}
          </p>
          <ul className="space-y-1.5">
            {items
              .filter((item) => item.wave === wave)
              .map((item) => {
                const weeks = item.durationWeeks ?? 4;
                const left = ((waveStart.get(wave) ?? 0) / totalWeeks) * 100;
                const width = Math.max(4, (weeks / totalWeeks) * 100);
                return (
                  <li key={item.id} className="grid grid-cols-[minmax(0,14rem)_1fr] items-center gap-3">
                    <span className="truncate text-xs text-[var(--text-secondary)]" title={item.title}>
                      {item.title}
                    </span>
                    <div className="relative h-5 rounded bg-[var(--surface-inset)]">
                      <div
                        className="absolute inset-y-0 flex items-center rounded px-1.5"
                        style={{
                          left: `${left}%`,
                          width: `${width}%`,
                          backgroundColor: waveColour(wave),
                        }}
                        title={`${weeks} weeks · ${item.effort.toLowerCase()} effort · ${item.impact.toLowerCase()} impact`}
                      >
                        <span className="tabular truncate text-[0.625rem] font-semibold text-white">
                          {weeks}w
                        </span>
                      </div>
                    </div>
                  </li>
                );
              })}
          </ul>
        </div>
      ))}
      <p className="text-[0.6875rem] text-[var(--text-muted)]">
        Bars show sequence and relative length in weeks, not calendar dates — the plan has no start date.
      </p>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Current vs future flow
// ---------------------------------------------------------------------------

export interface FlowStep {
  id: string;
  name: string;
  responsibility?: keyof typeof RESPONSIBILITY_COLOURS;
  replaces?: string | null;
}

/**
 * The current process beside the future one, with the links between them drawn.
 *
 * <p>The links are the point. Two lists side by side invite the reader to assume a correspondence;
 * drawing only the correspondences the data actually records shows which future steps replace
 * something and which are new — and which current steps nothing replaces.
 */
export function TransformationFlow({
  current,
  future,
}: {
  current: FlowStep[];
  future: FlowStep[];
}) {
  const [highlighted, setHighlighted] = useState<string | null>(null);

  const normalise = (value: string) => value.toLowerCase().replace(/[^a-z0-9]+/g, " ").trim();
  const currentIndex = new Map(current.map((step, index) => [normalise(step.name), index]));

  const rowHeight = 46;
  const height = Math.max(current.length, future.length) * rowHeight + 24;
  const columnWidth = 210;
  const gap = 96;
  const width = columnWidth * 2 + gap;

  return (
    <div className="overflow-x-auto">
      {/*
        Capped rather than stretched. An SVG with a small viewBox and w-full scales its text with
        the container, and on a wide screen the step names ended up larger than the page heading —
        the diagram is a diagram, not a hero.
      */}
      <svg
        viewBox={`0 0 ${width} ${height}`}
        style={{ maxWidth: `${width}px` }}
        className="w-full min-w-[520px]"
        role="img"
        aria-label="Current process steps linked to the future steps that replace them"
      >
        <text x={0} y={10} fontSize={10} fill="var(--text-muted)" className="uppercase">
          Today
        </text>
        <text x={columnWidth + gap} y={10} fontSize={10} fill="var(--text-muted)" className="uppercase">
          Redesigned
        </text>

        {/* Links first, so the boxes sit on top of them. */}
        {future.map((step, futureIndex) => {
          if (!step.replaces) return null;
          const sourceIndex = currentIndex.get(normalise(step.replaces));
          if (sourceIndex == null) return null;
          const y1 = 24 + sourceIndex * rowHeight + rowHeight / 2 - 6;
          const y2 = 24 + futureIndex * rowHeight + rowHeight / 2 - 6;
          const active = highlighted === step.id || highlighted === current[sourceIndex]?.id;
          return (
            <path
              key={`link-${step.id}`}
              d={`M ${columnWidth} ${y1} C ${columnWidth + gap / 2} ${y1}, ${columnWidth + gap / 2} ${y2}, ${columnWidth + gap} ${y2}`}
              fill="none"
              stroke={active ? "var(--seq-550)" : "var(--chart-grid)"}
              strokeWidth={active ? 2 : 1.5}
            />
          );
        })}

        {current.map((step, index) => (
          <g
            key={step.id}
            onMouseEnter={() => setHighlighted(step.id)}
            onMouseLeave={() => setHighlighted(null)}
          >
            <rect
              x={0}
              y={24 + index * rowHeight - 6}
              width={columnWidth}
              height={rowHeight - 10}
              rx={6}
              fill="var(--surface-3)"
              stroke="var(--border-subtle)"
            />
            <text x={10} y={24 + index * rowHeight + 11} fontSize={11} fill="var(--text-secondary)">
              {truncate(step.name, 30)}
            </text>
          </g>
        ))}

        {future.map((step, index) => {
          const colour = step.responsibility
            ? RESPONSIBILITY_COLOURS[step.responsibility]
            : "var(--resp-augmented)";
          return (
            <g
              key={step.id}
              onMouseEnter={() => setHighlighted(step.id)}
              onMouseLeave={() => setHighlighted(null)}
            >
              <rect
                x={columnWidth + gap}
                y={24 + index * rowHeight - 6}
                width={columnWidth}
                height={rowHeight - 10}
                rx={6}
                fill="var(--surface-3)"
                stroke="var(--border-subtle)"
              />
              <rect
                x={columnWidth + gap}
                y={24 + index * rowHeight - 6}
                width={3.5}
                height={rowHeight - 10}
                rx={2}
                fill={colour}
              />
              <text
                x={columnWidth + gap + 12}
                y={24 + index * rowHeight + 11}
                fontSize={11}
                fill="var(--text-secondary)"
              >
                {truncate(step.name, 30)}
              </text>
              {!step.replaces ? (
                <text
                  x={columnWidth + gap + columnWidth - 8}
                  y={24 + index * rowHeight + 11}
                  fontSize={9}
                  textAnchor="end"
                  fill="var(--text-muted)"
                >
                  new
                </text>
              ) : null}
            </g>
          );
        })}
      </svg>
    </div>
  );
}

function truncate(value: string, max: number) {
  return value.length <= max ? value : `${value.slice(0, max - 1)}…`;
}

// ---------------------------------------------------------------------------
// Distribution strip
// ---------------------------------------------------------------------------

/**
 * A compact count-by-category strip, used for source types and connectors.
 *
 * <p>Ordered by count rather than by category, because the reader's question is "what did this run
 * mostly find?" rather than "how many of type X".
 */
export function DistributionStrip({
  items,
  total,
}: {
  items: { key: string; label: string; value: number; colour: string }[];
  total?: number;
}) {
  const sum = total ?? items.reduce((accumulated, item) => accumulated + item.value, 0);
  if (sum === 0) {
    return <p className="text-xs text-[var(--text-muted)]">Nothing recorded.</p>;
  }
  return (
    <div>
      <div className="flex h-2 w-full overflow-hidden rounded-full">
        {items
          .filter((item) => item.value > 0)
          .map((item, index, visible) => (
            <div
              key={item.key}
              style={{
                flexGrow: item.value,
                backgroundColor: item.colour,
                marginRight: index < visible.length - 1 ? 2 : 0,
              }}
              title={`${item.label}: ${item.value}`}
            />
          ))}
      </div>
      <div className="mt-2 flex flex-wrap gap-x-3 gap-y-1">
        {items
          .filter((item) => item.value > 0)
          .map((item) => (
            <LegendSwatch key={item.key} colour={item.colour} label={item.label} count={item.value} />
          ))}
      </div>
    </div>
  );
}
