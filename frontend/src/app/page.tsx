"use client";

import Link from "next/link";
import { useCallback, useMemo, useState } from "react";
import { Badge, ButtonLink, ErrorPanel, Loading } from "@/components/ui";
import { StatTile } from "@/components/viz";
import { api } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { useApiResource } from "@/lib/use-api-resource";
import type { ProcessSummary } from "@/lib/types";

type Filter = "all" | "analysed" | "pending";

export default function DashboardPage() {
  const load = useCallback(() => api.listProcesses(), []);
  const { data: processes, error, loading, reload } = useApiResource(load);
  const [filter, setFilter] = useState<Filter>("all");

  const analysed = useMemo(
    () => (processes ?? []).filter((process) => process.status === "ANALYZED"),
    [processes],
  );
  const visible = useMemo(
    () =>
      (processes ?? []).filter((process) =>
        filter === "all"
          ? true
          : filter === "analysed"
            ? process.status === "ANALYZED"
            : process.status !== "ANALYZED",
      ),
    [processes, filter],
  );

  const opportunities = analysed.reduce((total, process) => total + process.opportunityCount, 0);
  const futureSteps = analysed.reduce((total, process) => total + process.futureActivityCount, 0);

  return (
    <div className="space-y-8">
      <Hero />

      {error ? (
        <ErrorPanel title="Could not load processes" message={error.message} onRetry={reload} />
      ) : null}

      {loading ? <Loading label="Loading processes…" /> : null}

      {processes ? (
        <div className="animate-rise space-y-6">
          <dl className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <StatTile label="Processes" value={processes.length} />
            <StatTile
              label="Analysed"
              value={analysed.length}
              hint={`${processes.length - analysed.length} still to run`}
            />
            <StatTile label="AI ideas found" value={opportunities} />
            <StatTile label="Future steps designed" value={futureSteps} />
          </dl>

          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex gap-1 rounded-lg border border-ink-200 bg-white p-1">
              {(
                [
                  ["all", `All ${processes.length}`],
                  ["analysed", `Analysed ${analysed.length}`],
                  ["pending", `Not yet run ${processes.length - analysed.length}`],
                ] as const
              ).map(([key, label]) => (
                <button
                  key={key}
                  type="button"
                  onClick={() => setFilter(key)}
                  aria-pressed={filter === key}
                  className={`rounded-md px-3 py-1.5 text-xs font-medium transition-colors ${
                    filter === key
                      ? "bg-ink-900 text-white"
                      : "text-ink-600 hover:bg-ink-100 hover:text-ink-900"
                  }`}
                >
                  {label}
                </button>
              ))}
            </div>
            <ButtonLink href="/processes/new" variant="secondary">
              + New process
            </ButtonLink>
          </div>

          {visible.length === 0 ? (
            <div className="rounded-xl border border-dashed border-ink-300 bg-white px-6 py-12 text-center text-sm text-ink-500">
              Nothing in this view.
            </div>
          ) : (
            <ul className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
              {visible.map((process) => (
                <ProcessCard key={process.id} process={process} />
              ))}
            </ul>
          )}
        </div>
      ) : null}
    </div>
  );
}

function Hero() {
  return (
    <section className="overflow-hidden rounded-2xl border border-ink-200 bg-white">
      <div className="grid gap-6 p-6 lg:grid-cols-[1.4fr_1fr] lg:p-8">
        <div>
          <h1 className="text-3xl leading-tight font-semibold tracking-tight text-ink-900">
            Redesign a business process around AI
          </h1>
          <p className="mt-3 max-w-2xl text-sm leading-relaxed text-ink-600">
            Describe how a process works <strong className="text-ink-900">today</strong> — the
            steps, who does them, what goes wrong. The system finds where AI could genuinely help,
            then writes out the redesigned process step by step, saying for each one what a person
            still owns and what the AI does.
          </p>
          <div className="mt-5 flex flex-wrap gap-3">
            <ButtonLink href="/processes/new">Analyse your own process</ButtonLink>
            <ButtonLink href="/how-it-works" variant="secondary">
              How it works
            </ButtonLink>
          </div>
        </div>

        <ol className="space-y-2 self-center">
          {[
            { n: 1, t: "Describe it as it is now", d: "Four or five steps. Any industry." },
            { n: 2, t: "Press Analyse", d: "5–30 seconds. Nothing is pre-written." },
            { n: 3, t: "Read the redesign", d: "With reasoning, risks and sources." },
          ].map((step) => (
            <li key={step.n} className="flex gap-3 rounded-lg bg-ink-50 p-3">
              <span className="grid size-6 shrink-0 place-items-center rounded-full bg-ink-900 text-xs font-bold text-white">
                {step.n}
              </span>
              <div>
                <p className="text-sm font-medium text-ink-900">{step.t}</p>
                <p className="text-xs text-ink-500">{step.d}</p>
              </div>
            </li>
          ))}
        </ol>
      </div>
    </section>
  );
}

function ProcessCard({ process }: { process: ProcessSummary }) {
  const analysed = process.status === "ANALYZED";
  const delta = process.futureActivityCount - process.activityCount;

  return (
    <li>
      <Link
        href={`/processes/${process.id}`}
        className="group flex h-full flex-col rounded-xl border border-ink-200 bg-white p-5 transition-all hover:border-ink-300 hover:shadow-md"
      >
        <div className="flex items-start justify-between gap-3">
          <h2 className="text-sm leading-snug font-semibold text-ink-900 group-hover:text-brand-700">
            {process.name}
          </h2>
          {analysed ? (
            <Badge tone="success">Analysed</Badge>
          ) : (
            <Badge tone="neutral">Not run yet</Badge>
          )}
        </div>

        <p className="mt-1 text-xs text-ink-500">{process.industry}</p>
        <p className="mt-2 line-clamp-2 text-xs leading-relaxed text-ink-600">
          {process.description}
        </p>

        <div className="mt-auto border-t border-ink-100 pt-4">
          {analysed ? (
            <div className="flex flex-wrap items-baseline gap-x-2 gap-y-1 text-xs text-ink-600">
              <span className="tabular text-lg leading-none font-semibold text-ink-900">
                {process.activityCount}
              </span>
              <span aria-hidden="true" className="text-ink-400">
                →
              </span>
              <span className="tabular text-lg leading-none font-semibold text-ink-900">
                {process.futureActivityCount}
              </span>
              <span>steps</span>
              {delta !== 0 ? (
                <span className="rounded-full bg-ink-100 px-1.5 py-0.5 text-[11px] font-medium">
                  {delta > 0 ? "+" : ""}
                  {delta}
                </span>
              ) : null}
              <span className="ml-auto font-medium text-ink-700">
                {process.opportunityCount} AI ideas
              </span>
            </div>
          ) : (
            <p className="text-xs text-ink-500">
              <span className="tabular font-semibold text-ink-900">{process.activityCount}</span>{" "}
              steps recorded — open it and press Analyse
            </p>
          )}
        </div>

        {process.lastAnalyzedAt ? (
          <p className="mt-2 text-[11px] text-ink-400">
            Last analysed {formatDateTime(process.lastAnalyzedAt)}
          </p>
        ) : null}
      </Link>
    </li>
  );
}
