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
        <h1 className="text-2xl font-semibold tracking-tight text-ink-900">Evidence corpus</h1>
        <p className="mt-2 text-sm leading-relaxed text-ink-600">
          The research layer. Rather than calling a live web-search API — which would be a
          rate-limited dependency in the middle of a demo — this project uses a small hand-curated
          set of cited excerpts. When a process is analysed, the retriever picks the most relevant
          of these by keyword match and injects them into the prompt, and each AI opportunity
          records which of them supported it.
        </p>
        <p className="mt-2 text-sm leading-relaxed text-ink-600">
          Every source below is a real, publicly reachable document. The excerpt text is a
          paraphrase written for this project, not a copied passage.
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

          <ul className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {visible.map((snippet) => (
              <SnippetCard key={snippet.id} snippet={snippet} />
            ))}
          </ul>
        </>
      ) : null}
    </div>
  );
}
