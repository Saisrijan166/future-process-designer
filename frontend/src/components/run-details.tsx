"use client";

import { useState } from "react";
import { Badge, Button, Card, Spinner } from "@/components/ui";
import { ApiError, api } from "@/lib/api";
import { formatDateTime, formatDuration, formatNumber } from "@/lib/format";
import type { AnalysisRunSummary, AnalysisRunTrace } from "@/lib/types";

/** Friendly names for the providers, so the panel doesn't read like a config file. */
const PROVIDER_LABELS: Record<string, string> = {
  gemini: "Google Gemini",
  groq: "Groq",
  stub: "Test stub",
};

function providerLabel(provider: string): string {
  return PROVIDER_LABELS[provider] ?? provider;
}

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
  const usedFallback = run.providerNotes.length > 0 && run.status === "SUCCEEDED";

  return (
    <Card className="p-4">
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
        <h3 className="text-sm font-semibold text-ink-900">How this was produced</h3>
        <Badge tone={statusTone}>{run.status === "SUCCEEDED" ? "Succeeded" : run.status === "FAILED" ? "Failed" : "Running"}</Badge>
        {usedFallback ? (
          <Badge tone="warning" title="The first AI service could not answer, so the next one in the chain did">
            Backup AI used
          </Badge>
        ) : null}
        {run.repairAttempted ? (
          <Badge
            tone="warning"
            title="The first response was not usable, so the pipeline asked the model again with the specific errors"
          >
            Repair retry used
          </Badge>
        ) : null}
        <Button variant="secondary" className="ml-auto" onClick={() => void loadTrace()}>
          {loadingTrace ? <Spinner /> : null}
          {trace ? "Hide prompt & raw response" : "Show prompt & raw response"}
        </Button>
      </div>

      <p className="mt-2 text-xs text-ink-500">
        Everything below is stored in the database for this run. Nothing on this page was written in
        advance.
      </p>

      {usedFallback ? (
        <div className="mt-3 rounded-lg border border-amber-200 bg-amber-50 p-3">
          <p className="text-xs font-semibold text-amber-900">
            Answered by the backup AI ({providerLabel(run.provider)})
          </p>
          <ul className="mt-1 space-y-0.5">
            {run.providerNotes.map((note, index) => (
              <li key={index} className="text-xs text-amber-800">
                • {note}
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      <dl className="mt-3 grid grid-cols-2 gap-x-6 gap-y-2 text-xs sm:grid-cols-3 lg:grid-cols-6">
        <div>
          <dt className="text-ink-500">AI service</dt>
          <dd className="font-medium text-ink-800">{providerLabel(run.provider)}</dd>
        </div>
        <div>
          <dt className="text-ink-500">Model</dt>
          <dd className="font-medium break-words text-ink-800">{run.model}</dd>
        </div>
        <div>
          <dt className="text-ink-500">Run at</dt>
          <dd className="font-medium text-ink-800">{formatDateTime(run.startedAt)}</dd>
        </div>
        <div>
          <dt className="text-ink-500">Took</dt>
          <dd className="font-medium text-ink-800">{formatDuration(run.durationMs)}</dd>
        </div>
        <div>
          <dt className="text-ink-500">Words in</dt>
          <dd className="font-medium text-ink-800" title="Prompt tokens">
            {formatNumber(run.promptTokens)}
          </dd>
        </div>
        <div>
          <dt className="text-ink-500">Words out</dt>
          <dd className="font-medium text-ink-800" title="Output tokens">
            {formatNumber(run.outputTokens)}
          </dd>
        </div>
      </dl>

      {run.errorMessage ? (
        <p className="mt-3 rounded-md bg-rose-50 p-2 text-xs text-rose-800">{run.errorMessage}</p>
      ) : null}

      {run.validationWarnings.length > 0 ? (
        <details className="mt-3">
          <summary className="cursor-pointer text-xs font-medium text-amber-800">
            {run.validationWarnings.length} thing
            {run.validationWarnings.length === 1 ? "" : "s"} the system corrected or discarded
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
              What was sent to {providerLabel(trace.run.provider)}
            </p>
            <pre className="max-h-96 overflow-auto rounded-lg bg-ink-900 p-3 text-[11px] leading-relaxed whitespace-pre-wrap text-ink-100">
              {trace.promptText ?? "Not recorded."}
            </pre>
          </div>
          <div>
            <p className="mb-1 text-xs font-semibold text-ink-700">What came back, unedited</p>
            <pre className="max-h-96 overflow-auto rounded-lg bg-ink-900 p-3 text-[11px] leading-relaxed whitespace-pre-wrap text-emerald-100">
              {trace.rawResponse ?? "Not recorded."}
            </pre>
          </div>
        </div>
      ) : null}
    </Card>
  );
}
