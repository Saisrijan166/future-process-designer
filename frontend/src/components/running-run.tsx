"use client";

import { useEffect, useState } from "react";
import { Badge, Panel, Spinner } from "@/components/ui";
import { api } from "@/lib/api";
import type { ActiveRun } from "@/lib/types";

/** Slow enough to be free, fast enough that a finished run is noticed within a stage. */
const POLL_INTERVAL_MS = 3000;

/**
 * What is happening on a run this page did not start.
 *
 * <p>An analysis takes about four minutes and lives on the server, not in the tab that asked for
 * it. Reload the page, open a second tab, or come back later and the live console is gone — and
 * before this, so was any sign that anything was happening: the page looked idle and the button
 * refused to work, with nothing to say why.
 *
 * <p>The pipeline commits each stage as it starts and finishes, so progress can simply be read.
 * This polls for it rather than streaming: a viewer who arrives mid-run cannot attach to the
 * original stream, and a three-second poll of a small payload is a fair price for being able to
 * follow a run from anywhere.
 */
export function RunningRunPanel({
  processId,
  initial,
  onFinished,
}: {
  processId: string;
  initial: ActiveRun;
  /** Called once the run is over, so the page can reload the analysis it produced. */
  onFinished: () => void;
}) {
  const [run, setRun] = useState<ActiveRun>(initial);
  const [lostTrack, setLostTrack] = useState(false);

  useEffect(() => {
    let cancelled = false;

    const poll = async () => {
      try {
        const next = await api.getActiveRun(processId);
        if (cancelled) return;
        if (next) {
          setRun(next);
        } else {
          // Gone from the active list: it either finished or failed. Either way the stored
          // analysis is what matters now, so hand back to the page.
          onFinished();
        }
      } catch {
        // A poll that fails is not news worth interrupting anyone over — the run is on the
        // server and unaffected. Say so quietly and keep trying.
        if (!cancelled) setLostTrack(true);
      }
    };

    const timer = window.setInterval(poll, POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [processId, onFinished]);

  const done = Math.min(run.stagesCompleted, run.stagesTotal);
  const fraction = run.stagesTotal > 0 ? done / run.stagesTotal : 0;

  return (
    <Panel className="p-4">
      <div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-2">
        <div className="min-w-0">
          <p className="flex items-center gap-2 text-sm font-semibold text-[var(--text-primary)]">
            <Spinner className="size-4 text-[var(--text-link)]" />
            Analysing this process now
          </p>
          <p className="mt-1 text-xs text-[var(--text-secondary)]">
            {run.currentStageTitle
              ? `Stage ${Math.min(done + 1, run.stagesTotal)} of ${run.stagesTotal} — ${run.currentStageTitle}`
              : `${done} of ${run.stagesTotal} stages done`}
            {" · "}
            {formatElapsed(run.elapsedMs)} so far
          </p>
        </div>
        <Badge tone="brand">Running</Badge>
      </div>

      {/* A count of stages, not a guess at a percentage: the stages take wildly different times. */}
      <div
        className="mt-3 h-1.5 w-full overflow-hidden rounded-full bg-[var(--surface-inset)]"
        role="progressbar"
        aria-valuemin={0}
        aria-valuemax={run.stagesTotal}
        aria-valuenow={done}
        aria-label="Stages completed"
      >
        <div
          className="h-full rounded-full bg-[var(--text-link)] transition-[width] duration-500"
          style={{ width: `${Math.round(fraction * 100)}%` }}
        />
      </div>

      {run.stages.length > 0 ? (
        <ol className="mt-3 space-y-1">
          {run.stages.map((stage) => (
            <li key={stage.stageId} className="flex items-baseline gap-2 text-xs">
              <span className="w-4 shrink-0 text-center" aria-hidden="true">
                {stage.status === "RUNNING" ? "·" : stage.status === "FAILED" ? "×" : "✓"}
              </span>
              <span
                className={
                  stage.status === "RUNNING"
                    ? "font-medium text-[var(--text-primary)]"
                    : "text-[var(--text-secondary)]"
                }
              >
                {stage.title}
              </span>
              {stage.summary ? (
                <span className="min-w-0 truncate text-[var(--text-muted)]">{stage.summary}</span>
              ) : null}
              {stage.durationMs != null ? (
                <span className="tabular ml-auto shrink-0 text-[var(--text-muted)]">
                  {Math.round(stage.durationMs / 100) / 10}s
                </span>
              ) : null}
            </li>
          ))}
        </ol>
      ) : null}

      <p className="mt-3 border-t border-[var(--border-subtle)] pt-2.5 text-[0.6875rem] text-[var(--text-muted)]">
        {lostTrack
          ? "Cannot reach the backend for progress at the moment — the run itself is unaffected, and this will catch up."
          : "This run belongs to the server, not to this tab: closing the page will not stop it, and the result appears here when it lands."}
      </p>
    </Panel>
  );
}

/** "48s", "3m 20s" — the granularity of a poll, for a run measured in minutes. */
function formatElapsed(ms: number): string {
  const seconds = Math.max(0, Math.round(ms / 1000));
  if (seconds < 60) return `${seconds}s`;
  return `${Math.floor(seconds / 60)}m ${String(seconds % 60).padStart(2, "0")}s`;
}
