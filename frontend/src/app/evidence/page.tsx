"use client";

import { useCallback, useMemo, useState } from "react";
import { SnippetCard } from "@/components/snippet-card";
import { Badge, ErrorPanel, Loading } from "@/components/ui";
import { api } from "@/lib/api";
import { sourceTypeLabel, sourceTypeTone } from "@/lib/format";
import { useApiResource } from "@/lib/use-api-resource";
import type { SourceType } from "@/lib/types";

const SOURCE_TYPES: SourceType[] = ["LAW", "GUIDANCE", "STANDARD", "RESEARCH", "VENDOR", "GENERAL_WEB"];

export default function EvidencePage() {
  const load = useCallback(() => api.listKnowledgeSnippets(), []);
  const { data: snippets, error, loading, reload } = useApiResource(load);
  const [filter, setFilter] = useState<SourceType | "ALL">("ALL");

  const visible = useMemo(
    () => (snippets ?? []).filter((snippet) => filter === "ALL" || snippet.sourceType === filter),
    [snippets, filter],
  );

  const counts = useMemo(() => {
    const result: Partial<Record<SourceType, number>> = {};
    for (const snippet of snippets ?? []) {
      result[snippet.sourceType] = (result[snippet.sourceType] ?? 0) + 1;
    }
    return result;
  }, [snippets]);

  return (
    <div className="space-y-6">
      <div className="max-w-3xl">
        <h1 className="text-2xl font-semibold tracking-tight text-ink-900">The research library</h1>
        <p className="mt-2 text-sm leading-relaxed text-ink-600">
          These are the sources the AI is allowed to cite. Rather than searching the web live —
          which would be a rate-limited dependency that could fail mid-demo, and whose results
          nobody could check afterwards — the system keeps a small hand-curated set of cited
          excerpts. When you analyse a process, it scores all of them, picks the most relevant, and
          shows only those to the AI.
        </p>
        <p className="mt-2 text-sm leading-relaxed text-ink-600">
          Every source is a real, publicly reachable document — click through and check. The
          summary text is written for this project rather than copied from the source. If the AI
          cites anything that is not on this page, the citation is discarded.
        </p>
      </div>

      {error ? (
        <ErrorPanel title="Could not load the corpus" message={error.message} onRetry={reload} />
      ) : null}

      {loading ? <Loading label="Loading sources…" /> : null}

      {snippets ? (
        <>
          <div className="flex flex-wrap items-center gap-2">
            <button type="button" onClick={() => setFilter("ALL")}>
              <Badge tone={filter === "ALL" ? "accent" : "neutral"}>All · {snippets.length}</Badge>
            </button>
            {SOURCE_TYPES.filter((type) => counts[type]).map((type) => (
              <button key={type} type="button" onClick={() => setFilter(type)}>
                <Badge tone={filter === type ? sourceTypeTone[type] : "neutral"}>
                  {sourceTypeLabel[type]} · {counts[type]}
                </Badge>
              </button>
            ))}
          </div>

          <ul className="animate-rise grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {visible.map((snippet) => (
              <SnippetCard key={snippet.id} snippet={snippet} />
            ))}
          </ul>
        </>
      ) : null}
    </div>
  );
}
