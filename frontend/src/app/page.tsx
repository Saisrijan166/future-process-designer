"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";
import { StatTile } from "@/components/charts";
import {
  Badge,
  Button,
  ButtonLink,
  EmptyState,
  ErrorPanel,
  PAGE_WIDE,
  Panel,
  Skeleton,
} from "@/components/ui";
import { api } from "@/lib/api";
import { formatDate, formatDateTime } from "@/lib/format";
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

// The labels name the field, not just the direction. "Newest first" next to a card whose only
// visible date was the analysis date made a correctly-sorted list look unsorted.
const SORTS: { value: NonNullable<ProcessListQuery["sort"]>; label: string }[] = [
  { value: "recent", label: "Recently added" },
  { value: "oldest", label: "Added — oldest" },
  { value: "name", label: "Name A–Z" },
  { value: "analysed", label: "Recently analysed" },
];

export default function DashboardPage() {
  // A table by default: eleven processes compared on the same six numbers is a table's job, and
  // the cards made you scan three columns to answer "which of these has been analysed".
  const [view, setView] = useState<"table" | "cards">("table");
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
  const anyRunning = (result?.items ?? []).some((item) => item.analysisRunning);

  // While something is being analysed, keep the listing fresh so the badge appears and clears on
  // its own. A run takes minutes, so eight seconds is frequent enough to feel live and rare enough
  // to cost nothing; when nothing is running there is no timer at all.
  useEffect(() => {
    if (!anyRunning) return;
    const timer = window.setInterval(reload, 8000);
    return () => window.clearInterval(timer);
  }, [anyRunning, reload]);

  return (
    <div className={`${PAGE_WIDE} space-y-7`}>
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

          <div className="ml-auto flex items-center gap-2">
            <ViewSwitch view={view} onChange={setView} />
            <ButtonLink href="/processes/new" variant="primary" size="md">
              New process
            </ButtonLink>
          </div>
        </div>

        {loading && !result ? (
          // Shaped like whichever view is about to arrive, so the layout does not jump.
          view === "table" ? (
            <Panel className="space-y-2 p-4">
              {Array.from({ length: 8 }).map((_, index) => (
                <Skeleton key={index} className="h-8 w-full" />
              ))}
            </Panel>
          ) : (
            <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
              {Array.from({ length: 6 }).map((_, index) => (
                <Panel key={index} className="space-y-3 p-4">
                  <Skeleton className="h-4 w-2/3" />
                  <Skeleton className="h-3 w-1/3" />
                  <Skeleton className="h-12 w-full" />
                </Panel>
              ))}
            </div>
          )
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
        ) : view === "table" ? (
          <ProcessTable
            processes={result?.items ?? []}
            sortedBy={sort}
            onSort={changeSort}
          />
        ) : (
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            {result?.items.map((process) => (
              <ProcessCard key={process.id} process={process} sortedBy={sort} />
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

/**
 * The listing as a table.
 *
 * <p>Eleven processes measured on the same six numbers is what a table is for: the eye runs down a
 * column instead of hopping between cards, and "which of these has not been analysed" is answered
 * at a glance rather than by reading. The card view stays available for anyone who prefers it.
 *
 * <p>The row is not itself a link — a table row cannot legally contain one — so the name is the
 * link and the row carries the click for the mouse. Keyboard and screen-reader users get the real
 * link; nobody gets a row that only works if you can see it.
 */
function ProcessTable({
  processes,
  sortedBy,
  onSort,
}: {
  processes: ProcessSummary[];
  sortedBy: NonNullable<ProcessListQuery["sort"]>;
  onSort: (next: NonNullable<ProcessListQuery["sort"]>) => void;
}) {
  const router = useRouter();

  return (
    <div className="panel overflow-x-auto">
      <table className="w-full min-w-[52rem] border-collapse text-left text-sm">
        <thead>
          <tr className="border-b border-[var(--border-subtle)]">
            <SortableHeader
              label="Process"
              className="pl-4"
              active={sortedBy === "name"}
              descending={false}
              onClick={() => onSort("name")}
            />
            <th scope="col" className="eyebrow px-3 py-2.5">
              Status
            </th>
            <th scope="col" className="eyebrow px-3 py-2.5 text-right" title="Activities recorded today">
              Steps
            </th>
            <th scope="col" className="eyebrow px-3 py-2.5 text-right" title="AI opportunities proposed">
              AI ideas
            </th>
            <th scope="col" className="eyebrow px-3 py-2.5 text-right" title="Steps in the redesigned process">
              Future
            </th>
            <SortableHeader
              label="Added"
              align="right"
              active={sortedBy === "recent" || sortedBy === "oldest"}
              descending={sortedBy === "recent"}
              // Clicking the column you are already sorted by reverses it, which is what a table
              // header is expected to do.
              onClick={() => onSort(sortedBy === "recent" ? "oldest" : "recent")}
            />
            <SortableHeader
              label="Last analysed"
              align="right"
              className="pr-4"
              active={sortedBy === "analysed"}
              descending
              onClick={() => onSort("analysed")}
            />
          </tr>
        </thead>
        <tbody>
          {processes.map((process) => {
            const analysed = process.status === "ANALYZED";
            return (
              <tr
                key={process.id}
                onClick={() => router.push(`/processes/${process.id}`)}
                className="cursor-pointer border-b border-[var(--border-subtle)] transition-colors last:border-0 hover:bg-[var(--surface-3)]"
              >
                <td className="max-w-[22rem] py-2.5 pl-4 pr-3">
                  <Link
                    href={`/processes/${process.id}`}
                    className="block truncate font-medium text-[var(--text-primary)] hover:text-[var(--text-link)]"
                    onClick={(event) => event.stopPropagation()}
                  >
                    {process.name}
                  </Link>
                  <span className="mt-0.5 block truncate text-[0.6875rem] text-[var(--text-muted)]">
                    {process.industry}
                    {process.shared ? " · shared sample" : ""}
                  </span>
                </td>
                <td className="px-3 py-2.5">
                  {process.analysisRunning ? (
                    <Badge tone="brand">Analysing…</Badge>
                  ) : (
                    <Badge tone={analysed ? "good" : "neutral"}>{analysed ? "Analysed" : "Not run"}</Badge>
                  )}
                </td>
                <td className="tabular px-3 py-2.5 text-right text-[var(--text-secondary)]">
                  {process.activityCount}
                </td>
                <td className="tabular px-3 py-2.5 text-right text-[var(--text-secondary)]">
                  {process.opportunityCount || "—"}
                </td>
                <td className="tabular px-3 py-2.5 text-right text-[var(--text-secondary)]">
                  {process.futureActivityCount || "—"}
                </td>
                <td className="tabular px-3 py-2.5 text-right text-[0.6875rem] whitespace-nowrap text-[var(--text-muted)]">
                  {formatDate(process.createdAt)}
                </td>
                <td className="tabular py-2.5 pr-4 pl-3 text-right text-[0.6875rem] whitespace-nowrap text-[var(--text-muted)]">
                  {process.lastAnalyzedAt ? formatDate(process.lastAnalyzedAt) : "—"}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function SortableHeader({
  label,
  active,
  descending,
  onClick,
  align = "left",
  className = "",
}: {
  label: string;
  active: boolean;
  /** Which way the sort actually runs, so the arrow and aria-sort agree with the rows. */
  descending: boolean;
  onClick: () => void;
  align?: "left" | "right";
  className?: string;
}) {
  return (
    <th
      scope="col"
      // aria-sort belongs to the header cell, not to the button inside it.
      aria-sort={active ? (descending ? "descending" : "ascending") : "none"}
      className={`px-3 py-2.5 ${align === "right" ? "text-right" : ""} ${className}`}
    >
      <button
        type="button"
        onClick={onClick}
        className={`eyebrow inline-flex items-center gap-1 hover:text-[var(--text-secondary)] ${
          active ? "text-[var(--text-primary)]" : ""
        }`}
      >
        {label}
        <span aria-hidden="true" className={active ? "" : "opacity-0"}>
          {descending ? "↓" : "↑"}
        </span>
      </button>
    </th>
  );
}

function ViewSwitch({
  view,
  onChange,
}: {
  view: "table" | "cards";
  onChange: (next: "table" | "cards") => void;
}) {
  return (
    <div
      role="group"
      aria-label="Listing layout"
      className="flex rounded-lg border border-[var(--border-subtle)] bg-[var(--surface-2)] p-0.5"
    >
      {(["table", "cards"] as const).map((option) => (
        <button
          key={option}
          type="button"
          onClick={() => onChange(option)}
          aria-pressed={view === option}
          className={`rounded-md px-2.5 py-1 text-xs font-medium capitalize transition-colors ${
            view === option
              ? "bg-[var(--surface-inverse)] text-[var(--text-inverse)]"
              : "text-[var(--text-muted)] hover:text-[var(--text-secondary)]"
          }`}
        >
          {option}
        </button>
      ))}
    </div>
  );
}

function ProcessCard({
  process,
  sortedBy,
}: {
  process: ProcessSummary;
  /** Which date the list is ordered by, so the card shows that one first. */
  sortedBy: NonNullable<ProcessListQuery["sort"]>;
}) {
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
        {/* Running wins over the stored status: it is the thing the reader can act on. */}
        {process.analysisRunning ? (
          <Badge tone="brand">Analysing…</Badge>
        ) : (
          <Badge tone={analysed ? "good" : "neutral"}>{analysed ? "Analysed" : "Not run"}</Badge>
        )}
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
        {process.analysisRunning ? "A run is in progress · " : ""}
        {process.shared ? "Shared sample · " : ""}
        {sortedBy === "analysed" && process.lastAnalyzedAt
          ? `Analysed ${formatDateTime(process.lastAnalyzedAt)}`
          : `Added ${formatDate(process.createdAt)}`}
        {sortedBy !== "analysed" && analysed && process.lastAnalyzedAt
          ? ` · analysed ${formatDate(process.lastAnalyzedAt)}`
          : ""}
      </p>
    </Link>
  );
}
