"use client";

import { useState } from "react";
import { Badge, Button, Card, Spinner } from "@/components/ui";
import { ApiError, api } from "@/lib/api";
import { formatDateTime, formatDuration, formatNumber } from "@/lib/format";
import type { AnalysisRunSummary, AnalysisRunTrace } from "@/lib/types";

/**
 * Shows how the stored analysis was produced, and can pull the exact prompt and raw model response
 * on demand. This is the answer to "how do we know this isn't hard-coded?" — it is a link, not an
 * assertion.
 */
export function RunDetails({
  run,
  processId,
}: {
  run: AnalysisRunSummary;
  processId: string;
}) {
  const [trace, setTrace] = useState<AnalysisRunTrace | null>(null);
  const [loadingTrace, setLoadingTrace] = useState(false);
  const [traceError, setTraceError] = useState<string | null>(null);

  async function loadTrace() {
    if (trace) {
      setTrace(null);
      return;
    }
    setLoadingTrace(true);
    setTraceError(null);
    try {
      setTrace(await api.getLatestTrace(processId));
    } catch (caught) {
      setTraceError((caught as ApiError).message);
    } finally {
      setLoadingTrace(false);
    }
  }

  const statusTone = run.status === "SUCCEEDED" ? "success" : run.status === "FAILED" ? "danger" : "info";

  return (
    <Card className="p-4">
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
        <h3 className="text-sm font-semibold text-ink-900">How this was produced</h3>
        <Badge tone={statusTone}>{run.status}</Badge>
        {run.repairAttempted ? (
          <Badge tone="warning" title="The first response was not usable, so the pipeline retried once with a repair prompt">
            Repair retry used
          </Badge>
        ) : null}
        <Button variant="secondary" className="ml-auto" onClick={() => void loadTrace()}>
          {loadingTrace ? <Spinner /> : null}
          {trace ? "Hide prompt & raw response" : "Show prompt & raw response"}
        </Button>
      </div>

      <dl className="mt-3 grid grid-cols-2 gap-x-6 gap-y-2 text-xs sm:grid-cols-3 lg:grid-cols-6">
        <div>
          <dt className="text-ink-500">Provider</dt>
          <dd className="font-medium text-ink-800">{run.provider}</dd>
        </div>
        <div>
          <dt className="text-ink-500">Model</dt>
          <dd className="font-medium text-ink-800">{run.model}</dd>
        </div>
        <div>
          <dt className="text-ink-500">Run at</dt>
          <dd className="font-medium text-ink-800">{formatDateTime(run.startedAt)}</dd>
        </div>
        <div>
          <dt className="text-ink-500">Duration</dt>
          <dd className="font-medium text-ink-800">{formatDuration(run.durationMs)}</dd>
        </div>
        <div>
          <dt className="text-ink-500">Prompt tokens</dt>
          <dd className="font-medium text-ink-800">{formatNumber(run.promptTokens)}</dd>
        </div>
        <div>
          <dt className="text-ink-500">Output tokens</dt>
          <dd className="font-medium text-ink-800">{formatNumber(run.outputTokens)}</dd>
        </div>
      </dl>

      {run.errorMessage ? (
        <p className="mt-3 rounded-md bg-rose-50 p-2 text-xs text-rose-800">{run.errorMessage}</p>
      ) : null}

      {run.validationWarnings.length > 0 ? (
        <details className="mt-3">
          <summary className="cursor-pointer text-xs font-medium text-amber-800">
            {run.validationWarnings.length} validation warning
            {run.validationWarnings.length === 1 ? "" : "s"} — items the pipeline corrected or
            discarded
          </summary>
          <ul className="mt-2 space-y-1 rounded-md bg-amber-50 p-2">
            {run.validationWarnings.map((warning, index) => (
              <li key={index} className="text-xs text-amber-900">
                • {warning}
              </li>
            ))}
          </ul>
        </details>
      ) : null}

      {traceError ? <p className="mt-3 text-xs text-rose-700">{traceError}</p> : null}

      {trace ? (
        <div className="mt-4 grid gap-4 lg:grid-cols-2">
          <div>
            <p className="mb-1 text-xs font-semibold text-ink-700">
              Prompt sent to {trace.run.model}
            </p>
            <pre className="max-h-96 overflow-auto rounded-lg bg-ink-900 p-3 text-[11px] leading-relaxed whitespace-pre-wrap text-ink-100">
              {trace.promptText ?? "Not recorded."}
            </pre>
          </div>
          <div>
            <p className="mb-1 text-xs font-semibold text-ink-700">Raw response received</p>
            <pre className="max-h-96 overflow-auto rounded-lg bg-ink-900 p-3 text-[11px] leading-relaxed whitespace-pre-wrap text-emerald-100">
              {trace.rawResponse ?? "Not recorded."}
            </pre>
          </div>
        </div>
      ) : null}
    </Card>
  );
}
