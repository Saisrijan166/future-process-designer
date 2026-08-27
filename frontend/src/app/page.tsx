"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { StatTile } from "@/components/charts";
import { Badge, Button, ButtonLink, EmptyState, ErrorPanel, Panel, SectionHeading, Skeleton } from "@/components/ui";
import { api } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { useApiResource } from "@/lib/use-api-resource";
import type { ProcessListQuery, ProcessStatus, ProcessSummary } from "@/lib/types";

const PAGE_SIZE = 9;

const FILTERS: { key: string; label: string; status?: ProcessStatus }[] = [
  { key: "all", label: "All" },
  { key: "analysed", label: "Analysed" },
  { key: "pending", label: "Not yet run" },
];

const FILTER_STATUS: Record<string, ProcessStatus | undefined> = {
  all: undefined,
  analysed: "ANALYZED",
  pending: "CURRENT_ONLY",
};

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

  // Debounced, so typing does not fire a request per keystroke.
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search), 300);
    return () => clearTimeout(timer);
  }, [search]);

  // Changing what is being asked for returns to the first page — a filter applied while on page 3
  // would otherwise land on an empty one. Done in the handlers rather than an effect, so the reset
  // happens with the change instead of as a second render pass.
  const changeFilter = (key: string) => {
    setFilter(key);
    setPage(0);
  };
  const changeSort = (next: NonNullable<ProcessListQuery["sort"]>) => {
    setSort(next);
    setPage(0);
  };
  const changeSearch = (next: string) => {
    setSearch(next);
    setPage(0);
  };

  const load = useCallback(
    () =>
      api.listProcesses({
        page,
        size: PAGE_SIZE,
        status: FILTER_STATUS[filter],
        q: debouncedSearch,
        sort,
      }),
    [page, filter, debouncedSearch, sort],
  );
  const { data: result, error, loading, reload } = useApiResource(load);

  const stats = result?.stats;

  return (
    <div className="mx-auto max-w-[84rem] space-y-7">
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
            hint={
              stats.processes - stats.analysed > 0
                ? `${stats.processes - stats.analysed} still to run`
                : "every process has been through the pipeline"
            }
          />
          <StatTile label="AI interventions proposed" value={stats.opportunities} />
          <StatTile label="Future steps designed" value={stats.futureActivities} />
        </dl>
      ) : null}

      <div className="space-y-4">
        <div className="flex flex-wrap items-center gap-3">
          <div className="flex gap-1 rounded-lg border border-[var(--border-subtle)] bg-[var(--surface-2)] p-1">
            {FILTERS.map((entry) => (
              <button
                key={entry.key}
                type="button"
                onClick={() => changeFilter(entry.key)}
                aria-pressed={filter === entry.key}
                className={`rounded-md px-3 py-1.5 text-xs font-medium transition-colors ${
                  filter === entry.key
                    ? "bg-[var(--surface-inverse)] text-[var(--text-inverse)]"
                    : "text-[var(--text-secondary)] hover:bg-[var(--surface-3)]"
                }`}
              >
                {entry.label}
              </button>
            ))}
          </div>

          <div className="relative min-w-56 flex-1 sm:max-w-xs">
            <svg
              className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-[var(--text-muted)]"
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
              placeholder="Search by name or industry"
              aria-label="Search processes"
              className="h-9 w-full rounded-lg border border-[var(--border-subtle)] bg-[var(--surface-2)] pl-9 pr-3 text-sm outline-none placeholder:text-[var(--text-muted)] focus:border-[var(--border-focus)]"
            />
          </div>

          <select
            value={sort}
            onChange={(event) => changeSort(event.target.value as NonNullable<ProcessListQuery["sort"]>)}
            aria-label="Sort processes"
            className="h-9 rounded-lg border border-[var(--border-subtle)] bg-[var(--surface-2)] px-2.5 text-xs text-[var(--text-secondary)] outline-none focus:border-[var(--border-focus)]"
          >
            {SORTS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>

          <ButtonLink href="/processes/new" variant="primary" size="md" className="ml-auto">
            New process
          </ButtonLink>
        </div>

        {loading && !result ? (
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            {Array.from({ length: 6 }).map((_, index) => (
              <Panel key={index} className="space-y-3 p-4">
                <Skeleton className="h-4 w-2/3" />
                <Skeleton className="h-3 w-1/3" />
                <Skeleton className="h-12 w-full" />
              </Panel>
            ))}
          </div>
        ) : result && result.items.length === 0 ? (
          <EmptyState
            title={debouncedSearch ? "Nothing matched that search" : "No processes yet"}
            message={
              debouncedSearch
                ? "Try a different word, or clear the search to see everything."
                : "Describe a process as it runs today and the pipeline will research it and design its AI-enabled future state."
            }
            action={<ButtonLink href="/processes/new" variant="primary">Describe a process</ButtonLink>}
          />
        ) : (
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            {result?.items.map((process) => (
              <ProcessCard key={process.id} process={process} />
            ))}
          </div>
        )}

        {result && result.totalPages > 1 ? (
          <div className="flex items-center justify-between gap-3 pt-1">
            <p className="text-xs text-[var(--text-muted)]">
              Page {result.page + 1} of {result.totalPages} · {result.totalItems} processes
            </p>
            <div className="flex gap-2">
              <Button size="sm" disabled={!result.hasPrevious} onClick={() => setPage((value) => value - 1)}>
                Previous
              </Button>
              <Button size="sm" disabled={!result.hasNext} onClick={() => setPage((value) => value + 1)}>
                Next
              </Button>
            </div>
          </div>
        ) : null}
      </div>
    </div>
  );
}

function Hero() {
  return (
    <section className="panel overflow-hidden">
      <div className="grid gap-6 p-6 lg:grid-cols-[1.4fr_1fr] lg:p-8">
        <div>
          <Badge tone="brand">Ten-stage pipeline · live research · verified citations</Badge>
          <h1 className="mt-3 text-2xl font-semibold leading-tight text-[var(--text-primary)] sm:text-3xl">
            Describe how a process runs today.
            <br />
            Get back a redesign you can check.
          </h1>
          <p className="mt-3 max-w-2xl text-sm leading-relaxed text-[var(--text-secondary)]">
            Every analysis searches the live web across eleven free public sources, reads the pages it
            finds, and extracts claims with a quote that is then located in the page it came from. A
            second model reviews the recommendations before you see them. The future process is stored
            as rows — activities, responsibilities, risks, costs — not as a paragraph of prose.
          </p>
          <div className="mt-5 flex flex-wrap gap-2">
            <ButtonLink href="/processes/new" variant="primary" size="lg">
              Describe a process
            </ButtonLink>
            <ButtonLink href="/how-it-works" size="lg">
              See how it works
            </ButtonLink>
          </div>
        </div>

        <ul className="grid gap-2.5 self-center">
          {[
            {
              title: "Researched, not recalled",
              body: "Bing, Google News, OpenAlex, Crossref, arXiv, Europe PMC, Wikipedia, Hacker News, Stack Exchange and an agentic search — all free, none requiring a key.",
            },
            {
              title: "Quotes checked mechanically",
              body: "Every citation carries a quote that was located in the stored page text by string matching. One that could not be found is shown as unverified rather than hidden.",
            },
            {
              title: "Marked by a second model",
              body: "A different model family reviews each recommendation and scores its evidence. Where the two disagree, you see the objection.",
            },
          ].map((item) => (
            <li key={item.title} className="panel-quiet p-3.5">
              <p className="text-[0.8125rem] font-semibold text-[var(--text-primary)]">{item.title}</p>
              <p className="mt-1 text-xs leading-relaxed text-[var(--text-secondary)]">{item.body}</p>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}

function ProcessCard({ process }: { process: ProcessSummary }) {
  const analysed = process.status === "ANALYZED";
  const summary = useMemo(
    () =>
      process.description.length > 150 ? `${process.description.slice(0, 149)}…` : process.description,
    [process.description],
  );

  return (
    <Link
      href={`/processes/${process.id}`}
      className="panel group flex flex-col p-4 transition-all hover:border-[var(--border-strong)] hover:shadow-[var(--shadow-panel)]"
    >
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <h3 className="truncate text-[0.9375rem] font-semibold text-[var(--text-primary)]">
            {process.name}
          </h3>
          <p className="mt-0.5 truncate text-xs text-[var(--text-muted)]">{process.industry}</p>
        </div>
        <Badge tone={analysed ? "good" : "neutral"}>{analysed ? "Analysed" : "Not run"}</Badge>
      </div>

      <p className="mt-2.5 line-clamp-3 flex-1 text-xs leading-relaxed text-[var(--text-secondary)]">
        {summary}
      </p>

      <dl className="mt-3.5 grid grid-cols-3 gap-2 border-t border-[var(--border-subtle)] pt-3">
        <div>
          <dt className="eyebrow">Steps</dt>
          <dd className="tabular text-sm font-semibold">{process.activityCount}</dd>
        </div>
        <div>
          <dt className="eyebrow">AI ideas</dt>
          <dd className="tabular text-sm font-semibold">{process.opportunityCount || "—"}</dd>
        </div>
        <div>
          <dt className="eyebrow">Future</dt>
          <dd className="tabular text-sm font-semibold">{process.futureActivityCount || "—"}</dd>
        </div>
      </dl>

      <p className="mt-2.5 text-[0.6875rem] text-[var(--text-muted)]">
        {process.shared ? "Shared sample · " : ""}
        {analysed && process.lastAnalyzedAt
          ? `Analysed ${formatDateTime(process.lastAnalyzedAt)}`
          : `Added ${formatDateTime(process.createdAt)}`}
      </p>
    </Link>
  );
}
