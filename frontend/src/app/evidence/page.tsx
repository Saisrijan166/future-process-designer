"use client";

import { useCallback, useMemo, useState } from "react";
import { DistributionStrip, StatTile } from "@/components/charts";
import { SOURCE_TYPE_COLOURS, SOURCE_TYPE_LABELS } from "@/components/evidence";
import { Badge, EmptyState, ErrorPanel, Panel, SectionHeading, Skeleton } from "@/components/ui";
import { api } from "@/lib/api";
import { formatDate } from "@/lib/format";
import { useApiResource } from "@/lib/use-api-resource";
import type { KnowledgeSnippet, SourceType } from "@/lib/types";

/**
 * The curated corpus: the fallback grounding, and the honest label on it.
 *
 * <p>This was the whole evidence layer in the first version of the application, and it is worth
 * being clear about what it is now. Live research replaced it as the primary source of grounding —
 * every analysis searches the web for the specific process in front of it — but a run whose
 * connectors were all blocked still needs something to reason against, and this is that something.
 *
 * <p>Which is why the page says so at the top rather than presenting fifteen excerpts as though
 * they were the research. The real evidence for any given analysis lives on that process's own
 * Evidence tab, with the quotes that were checked against the pages they came from.
 */
export default function EvidencePage() {
  const load = useCallback(() => api.listKnowledgeSnippets(), []);
  const { data: snippets, error, loading, reload } = useApiResource(load);
  const [filter, setFilter] = useState<SourceType | "ALL">("ALL");

  const byType = useMemo(() => {
    const counts = new Map<SourceType, number>();
    for (const snippet of snippets ?? []) {
      counts.set(snippet.sourceType, (counts.get(snippet.sourceType) ?? 0) + 1);
    }
    return counts;
  }, [snippets]);

  const visible = useMemo(
    () => (snippets ?? []).filter((snippet) => filter === "ALL" || snippet.sourceType === filter),
    [snippets, filter],
  );

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <header>
        <h1 className="text-xl font-semibold sm:text-2xl">Curated corpus</h1>
        <p className="mt-1.5 max-w-3xl text-sm leading-relaxed text-[var(--text-secondary)]">
          Hand-checked excerpts with real, openable source URLs. These are the <em>fallback</em>: when
          an analysis runs, it searches the live web for that specific process, and this corpus is what
          grounds a run whose connectors were all blocked or which was run with research switched off.
          The evidence behind any particular analysis is on that process&rsquo;s own Evidence tab.
        </p>
      </header>

      {error ? (
        <ErrorPanel title="Could not load the corpus" message={error.message} onRetry={reload} />
      ) : null}

      {loading && !snippets ? (
        <div className="space-y-3">
          {Array.from({ length: 4 }).map((_, index) => (
            <Skeleton key={index} className="h-24 w-full" />
          ))}
        </div>
      ) : snippets && snippets.length > 0 ? (
        <>
          <div className="grid gap-3 sm:grid-cols-3">
            <StatTile label="Excerpts" value={snippets.length} />
            <StatTile label="Distinct publishers" value={new Set(snippets.map((snippet) => snippet.publisher ?? "—")).size} />
            <StatTile label="Source types" value={byType.size} />
          </div>

          <Panel className="p-4">
            <SectionHeading title="What kind of sources" />
            <DistributionStrip
              items={[...byType.entries()].map(([type, count]) => ({
                key: type,
                label: SOURCE_TYPE_LABELS[type],
                value: count,
                colour: SOURCE_TYPE_COLOURS[type],
              }))}
            />
          </Panel>

          <div className="flex flex-wrap gap-1.5">
            <FilterChip active={filter === "ALL"} onClick={() => setFilter("ALL")}>
              All {snippets.length}
            </FilterChip>
            {[...byType.entries()].map(([type, count]) => (
              <FilterChip key={type} active={filter === type} onClick={() => setFilter(type)}>
                {SOURCE_TYPE_LABELS[type]} {count}
              </FilterChip>
            ))}
          </div>

          <ul className="space-y-2.5">
            {visible.map((snippet) => (
              <li key={snippet.id}>
                <SnippetCard snippet={snippet} />
              </li>
            ))}
          </ul>
        </>
      ) : (
        <EmptyState
          title="The corpus is empty"
          message="No curated excerpts are stored. Live research still runs; this fallback simply has nothing in it."
        />
      )}
    </div>
  );
}

function FilterChip({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={`rounded-md border px-2 py-1 text-[0.6875rem] font-medium transition-colors ${
        active
          ? "border-transparent bg-[var(--surface-inverse)] text-[var(--text-inverse)]"
          : "border-[var(--border-subtle)] text-[var(--text-secondary)] hover:bg-[var(--surface-3)]"
      }`}
    >
      {children}
    </button>
  );
}

function SnippetCard({ snippet }: { snippet: KnowledgeSnippet }) {
  return (
    <Panel className="p-4">
      <div className="flex flex-wrap items-center gap-1.5">
        <span
          className="size-2 rounded-[2px]"
          style={{ backgroundColor: SOURCE_TYPE_COLOURS[snippet.sourceType] }}
          aria-hidden="true"
        />
        <Badge tone="neutral">{SOURCE_TYPE_LABELS[snippet.sourceType]}</Badge>
        {snippet.publisher ? (
          <span className="text-[0.6875rem] text-[var(--text-muted)]">{snippet.publisher}</span>
        ) : null}
        <span className="text-[0.6875rem] text-[var(--text-muted)]">
          · retrieved {formatDate(snippet.retrievedAt)}
        </span>
      </div>

      <h2 className="mt-2 text-sm font-semibold text-[var(--text-primary)]">{snippet.title}</h2>
      <blockquote className="quote mt-2">{snippet.snippetText}</blockquote>

      <div className="mt-2.5 flex flex-wrap items-center gap-x-3 gap-y-1">
        <a
          href={snippet.sourceUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="mono break-all text-[var(--text-link)] hover:underline"
        >
          {snippet.sourceUrl}
        </a>
      </div>

      {snippet.tags.length > 0 ? (
        <div className="mt-2 flex flex-wrap gap-1">
          {snippet.tags.map((tag) => (
            <span
              key={tag}
              className="rounded bg-[var(--surface-3)] px-1.5 py-0.5 text-[0.625rem] text-[var(--text-muted)]"
            >
              {tag}
            </span>
          ))}
        </div>
      ) : null}
    </Panel>
  );
}
