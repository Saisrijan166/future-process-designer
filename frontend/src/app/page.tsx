"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { Badge, ButtonLink, ErrorPanel, Loading } from "@/components/ui";
import { StatTile } from "@/components/viz";
import { api } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { useApiResource } from "@/lib/use-api-resource";
import type { ProcessListQuery, ProcessStatus, ProcessSummary } from "@/lib/types";

const PAGE_SIZE = 9;

const FILTERS: { key: string; label: string; status?: ProcessStatus }[] = [
  { key: "all", label: "All" },
  { key: "analysed", label: "Analysed", status: "ANALYZED" },
  { key: "pending", label: "Not yet run", status: "CURRENT_ONLY" },
];

const SORTS: { value: NonNullable<ProcessListQuery["sort"]>; label: string }[] = [
  { value: "recent", label: "Newest first" },
  { value: "oldest", label: "Oldest first" },
  { value: "name", label: "Name A–Z" },
  { value: "analysed", label: "Recently analysed" },
];

export default function DashboardPage() {
  const [filter, setFilter] = useState("all");
  const [sort, setSort] = useState<NonNullable<ProcessListQuery["sort"]>>("recent");
  const [search, setSearch] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [page, setPage] = useState(0);

  // Debounced so typing does not fire a request per keystroke.
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search), 300);
    return () => clearTimeout(timer);
  }, [search]);

  // Changing what is being asked for returns to the first page — otherwise a filter applied while
  // on page 3 lands the user on an empty page. Done in the handlers rather than an effect, so the
  // reset happens with the change instead of as a second render pass.
  function changeFilter(key: string) {
    setFilter(key);
    setPage(0);
  }

  function changeSort(next: NonNullable<ProcessListQuery["sort"]>) {
    setSort(next);
    setPage(0);
  }

  function changeSearch(next: string) {
    setSearch(next);
    setPage(0);
  }

  const status = FILTERS.find((entry) => entry.key === filter)?.status;

  const load = useCallback(
    () => api.listProcesses({ page, size: PAGE_SIZE, status, q: debouncedSearch, sort }),
    [page, status, debouncedSearch, sort],
  );
  const { data: result, error, loading, reload } = useApiResource(load);

  const stats = result?.stats;

  return (
    <div className="space-y-8">
      <Hero />

      {error ? (
        <ErrorPanel title="Could not load processes" message={error.message} onRetry={reload} />
      ) : null}

      {stats ? (
        <dl className="grid grid-cols-2 gap-3 lg:grid-cols-4">
          <StatTile label="Processes" value={stats.processes} />
          <StatTile
            label="Analysed"
            value={stats.analysed}
            hint={`${stats.processes - stats.analysed} still to run`}
          />
          <StatTile label="AI ideas found" value={stats.opportunities} />
          <StatTile label="Future steps designed" value={stats.futureActivities} />
        </dl>
      ) : null}

      <div className="space-y-4">
        <div className="flex flex-wrap items-center gap-3">
          <div className="flex gap-1 rounded-lg border border-ink-200 bg-white p-1">
            {FILTERS.map((entry) => (
              <button
                key={entry.key}
                type="button"
                onClick={() => changeFilter(entry.key)}
                aria-pressed={filter === entry.key}
                className={`rounded-md px-3 py-1.5 text-xs font-medium transition-colors ${
                  filter === entry.key
                    ? "bg-ink-900 text-white"
                    : "text-ink-600 hover:bg-ink-100 hover:text-ink-900"
                }`}
              >
                {entry.label}
              </button>
            ))}
          </div>

          <div className="relative min-w-56 flex-1 sm:max-w-xs">
            <svg
              className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-ink-400"
              viewBox="0 0 16 16"
              fill="none"
              aria-hidden="true"
            >
              <circle cx="7" cy="7" r="4.5" stroke="currentColor" strokeWidth="1.5" />
              <path d="M10.5 10.5L14 14" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
            </svg>
            <input
              type="search"
              value={search}
              onChange={(event) => changeSearch(event.target.value)}
              placeholder="Search name, industry or description"
              aria-label="Search processes"
              className="w-full rounded-lg border border-ink-300 bg-white py-2 pr-3 pl-9 text-sm text-ink-900 transition-colors placeholder:text-ink-400 focus:border-brand-500 focus:outline-none"
            />
          </div>

          <label className="flex items-center gap-2 text-xs text-ink-500">
            Sort
            <select
              value={sort}
              onChange={(event) => changeSort(event.target.value as NonNullable<ProcessListQuery["sort"]>)}
              className="rounded-lg border border-ink-300 bg-white px-2.5 py-2 text-xs font-medium text-ink-700 focus:border-brand-500 focus:outline-none"
            >
              {SORTS.map((entry) => (
                <option key={entry.value} value={entry.value}>
                  {entry.label}
                </option>
              ))}
            </select>
          </label>

          <ButtonLink href="/processes/new" variant="secondary" className="ml-auto">
            + New process
          </ButtonLink>
        </div>

        {loading && !result ? <Loading label="Loading processes…" /> : null}

        {result && result.items.length === 0 ? (
          <div className="rounded-xl border border-dashed border-ink-300 bg-white px-6 py-12 text-center">
            <p className="text-sm font-medium text-ink-800">Nothing matches that</p>
            <p className="mt-1 text-sm text-ink-500">
              {debouncedSearch
                ? `No process matches “${debouncedSearch}”.`
                : "No process is in this state yet."}
            </p>
          </div>
        ) : null}

        {result && result.items.length > 0 ? (
          <>
            <ul className="animate-rise grid gap-4 md:grid-cols-2 xl:grid-cols-3">
              {result.items.map((process) => (
                <ProcessCard key={process.id} process={process} />
              ))}
            </ul>

            <Pager
              page={result.page}
              totalPages={result.totalPages}
              totalItems={result.totalItems}
              size={result.size}
              hasPrevious={result.hasPrevious}
              hasNext={result.hasNext}
              busy={loading}
              onChange={setPage}
            />
          </>
        ) : null}
      </div>
    </div>
  );
}

/**
 * Paging controls.
 *
 * Page numbers are shown rather than only prev/next, because "where am I in the list" is the
 * question a pager exists to answer. Beyond seven pages the middle collapses to an ellipsis so the
 * control keeps a fixed width.
 */
function Pager({
  page,
  totalPages,
  totalItems,
  size,
  hasPrevious,
  hasNext,
  busy,
  onChange,
}: {
  page: number;
  totalPages: number;
  totalItems: number;
  size: number;
  hasPrevious: boolean;
  hasNext: boolean;
  busy: boolean;
  onChange: (page: number) => void;
}) {
  if (totalPages <= 1) {
    return (
      <p className="text-xs text-ink-500">
        Showing all {totalItems} process{totalItems === 1 ? "" : "es"}.
      </p>
    );
  }

  const firstRow = page * size + 1;
  const lastRow = Math.min((page + 1) * size, totalItems);

  const pages: (number | "gap")[] = [];
  for (let index = 0; index < totalPages; index += 1) {
    const nearEdge = index === 0 || index === totalPages - 1;
    const nearCurrent = Math.abs(index - page) <= 1;
    if (nearEdge || nearCurrent) {
      pages.push(index);
    } else if (pages.at(-1) !== "gap") {
      pages.push("gap");
    }
  }

  const buttonClass =
    "grid size-8 place-items-center rounded-lg border text-xs font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-40";

  return (
    <nav
      className="flex flex-wrap items-center justify-between gap-3 border-t border-ink-200 pt-4"
      aria-label="Process list pages"
    >
      <p className="tabular text-xs text-ink-500">
        Showing {firstRow}–{lastRow} of {totalItems}
      </p>

      <div className="flex items-center gap-1">
        <button
          type="button"
          onClick={() => onChange(page - 1)}
          disabled={!hasPrevious || busy}
          aria-label="Previous page"
          className={`${buttonClass} border-ink-300 bg-white text-ink-700 hover:bg-ink-50`}
        >
          ‹
        </button>

        {pages.map((entry, index) =>
          entry === "gap" ? (
            <span key={`gap-${index}`} className="px-1 text-xs text-ink-400" aria-hidden="true">
              …
            </span>
          ) : (
            <button
              key={entry}
              type="button"
              onClick={() => onChange(entry)}
              disabled={busy}
              aria-label={`Page ${entry + 1}`}
              aria-current={entry === page ? "page" : undefined}
              className={`${buttonClass} ${
                entry === page
                  ? "border-ink-900 bg-ink-900 text-white"
                  : "border-ink-300 bg-white text-ink-700 hover:bg-ink-50"
              }`}
            >
              {entry + 1}
            </button>
          ),
        )}

        <button
          type="button"
          onClick={() => onChange(page + 1)}
          disabled={!hasNext || busy}
          aria-label="Next page"
          className={`${buttonClass} border-ink-300 bg-white text-ink-700 hover:bg-ink-50`}
        >
          ›
        </button>
      </div>
    </nav>
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
