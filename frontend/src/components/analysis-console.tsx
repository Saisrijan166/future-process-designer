"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Badge, Button, Panel, Spinner, type Tone } from "@/components/ui";
import { ApiError, streamAnalysis } from "@/lib/api";
import type { AnalysisResult, ProgressEvent } from "@/lib/types";

/**
 * The live view of an analysis while it runs.
 *
 * <p>A full run takes minutes: eleven search connectors, half a dozen pages fetched and read, ten
 * model calls, several of them queued behind a free-tier token bucket. A spinner for that long is
 * indistinguishable from a hang — and, worse, it hides the part of this application that is
 * actually interesting. The searches being planned, the sources arriving, each quote being checked
 * against the page it came from: that is the product, and it should be watchable.
 *
 * <p>Two decisions shape what is shown. Every event is rendered as it arrives rather than
 * batched, so the pace of the run is visible including the pauses. And a stage that degrades or
 * fails stays on screen with its reason, because a run that lost its roadmap and says so is more
 * trustworthy than one that quietly shows nine stages.
 */

const STAGE_ORDER = [
  "intake",
  "diagnosis",
  "research",
  "opportunities",
  "critique",
  "future-design",
  "quantification",
  "risks",
  "roadmap",
  "scorecard",
] as const;

const STAGE_TITLES: Record<(typeof STAGE_ORDER)[number], string> = {
  intake: "Read the current process",
  diagnosis: "Diagnose the problems",
  research: "Research the domain live",
  opportunities: "Find grounded AI opportunities",
  critique: "Review the proposals adversarially",
  "future-design": "Design the future process",
  quantification: "Quantify the impact",
  risks: "Assess risks and obligations",
  roadmap: "Sequence the delivery",
  scorecard: "Score this analysis",
};

type StageState = "waiting" | "running" | "done" | "degraded" | "failed";

interface StageView {
  id: string;
  title: string;
  state: StageState;
  summary?: string;
  model?: string;
  cached?: boolean;
  notes: string[];
  tokens?: number;
}

interface FeedEntry {
  id: number;
  kind: "query" | "source" | "claims" | "search" | "note";
  text: string;
  detail?: string;
  tone: Tone;
}

export function AnalysisConsole({
  processId,
  processName,
  onComplete,
  onClose,
}: {
  processId: string;
  processName: string;
  onComplete: (result: AnalysisResult) => void;
  onClose: () => void;
}) {
  const [stages, setStages] = useState<StageView[]>(() =>
    STAGE_ORDER.map((id) => ({ id, title: STAGE_TITLES[id], state: "waiting", notes: [] })),
  );
  const [feed, setFeed] = useState<FeedEntry[]>([]);
  const [error, setError] = useState<{ message: string; detail?: string } | null>(null);
  const [finished, setFinished] = useState(false);
  const [elapsed, setElapsed] = useState(0);

  const feedId = useRef(0);
  const feedRef = useRef<HTMLDivElement>(null);
  const abortRef = useRef<AbortController | null>(null);
  const startedAt = useRef(Date.now());

  const pushFeed = useCallback((entry: Omit<FeedEntry, "id">) => {
    setFeed((current) => {
      const next = [...current, { ...entry, id: feedId.current++ }];
      // The feed is a live log, not an archive: the last eighty entries are what anyone reads, and
      // an unbounded list on a slow run is a memory leak with a scrollbar.
      return next.length > 80 ? next.slice(next.length - 80) : next;
    });
  }, []);

  const applyEvent = useCallback(
    (event: ProgressEvent) => {
      const data = event.data ?? {};

      switch (event.type) {
        case "STAGE_STARTED": {
          if (!event.stageId || event.stageId === "run") return;
          setStages((current) =>
            current.map((stage) =>
              stage.id === event.stageId ? { ...stage, state: "running" } : stage,
            ),
          );
          return;
        }
        case "STAGE_FINISHED":
        case "STAGE_DEGRADED":
        case "STAGE_FAILED": {
          if (!event.stageId || event.stageId === "run") return;
          const state: StageState =
            event.type === "STAGE_FINISHED"
              ? "done"
              : event.type === "STAGE_DEGRADED"
                ? "degraded"
                : "failed";
          setStages((current) =>
            current.map((stage) =>
              stage.id === event.stageId
                ? {
                    ...stage,
                    state,
                    summary: event.message ?? stage.summary,
                    model: typeof data.model === "string" && data.model ? data.model : stage.model,
                    cached: Boolean(data.cached),
                    notes: Array.isArray(data.notes) ? (data.notes as string[]) : stage.notes,
                    tokens:
                      typeof data.outputTokens === "number"
                        ? (data.promptTokens as number ?? 0) + data.outputTokens
                        : stage.tokens,
                  }
                : stage,
            ),
          );
          if (event.type !== "STAGE_FINISHED" && event.message) {
            pushFeed({
              kind: "note",
              text: event.message,
              tone: event.type === "STAGE_FAILED" ? "critical" : "warning",
            });
          }
          return;
        }
        case "QUERY_PLANNED":
          pushFeed({
            kind: "query",
            text: event.message ?? "",
            detail: typeof data.intent === "string" ? intentLabel(data.intent) : undefined,
            tone: "brand",
          });
          return;
        case "SEARCH_RESULT":
          pushFeed({
            kind: "search",
            text: event.message ?? "",
            detail: Array.isArray(data.titles) ? (data.titles as string[]).slice(0, 2).join(" · ") : undefined,
            tone: "neutral",
          });
          return;
        case "SOURCE_FETCHED":
          pushFeed({
            kind: "source",
            text: event.message ?? "",
            detail: typeof data.domain === "string" ? data.domain : undefined,
            tone: data.status === "FETCHED" ? "good" : data.status === "BLOCKED" ? "warning" : "neutral",
          });
          return;
        case "CLAIMS_EXTRACTED":
          pushFeed({
            kind: "claims",
            text: event.message ?? "",
            detail: typeof data.model === "string" ? String(data.model) : undefined,
            tone: (data.verified as number) > 0 ? "good" : "warning",
          });
          return;
        case "NOTE":
          if (event.message) pushFeed({ kind: "note", text: event.message, tone: "neutral" });
          return;
        default:
          return;
      }
    },
    [pushFeed],
  );

  useEffect(() => {
    const controller = new AbortController();
    abortRef.current = controller;
    let cancelled = false;

    streamAnalysis(processId, applyEvent, controller.signal)
      .then((result) => {
        if (cancelled) return;
        setFinished(true);
        onComplete(result);
      })
      .catch((caught) => {
        if (cancelled || controller.signal.aborted) return;
        setFinished(true);
        if (caught instanceof ApiError) {
          setError({ message: caught.message, detail: caught.reason });
        } else {
          setError({ message: caught instanceof Error ? caught.message : "The analysis failed." });
        }
      });

    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [processId, applyEvent, onComplete]);

  useEffect(() => {
    if (finished) return;
    const timer = window.setInterval(
      () => setElapsed(Math.round((Date.now() - startedAt.current) / 1000)),
      1000,
    );
    return () => window.clearInterval(timer);
  }, [finished]);

  useEffect(() => {
    feedRef.current?.scrollTo({ top: feedRef.current.scrollHeight, behavior: "smooth" });
  }, [feed]);

  const progress = useMemo(() => {
    const settled = stages.filter((stage) => stage.state !== "waiting" && stage.state !== "running").length;
    return Math.round((settled / stages.length) * 100);
  }, [stages]);

  return (
    <Panel className="overflow-hidden">
      <header className="flex flex-wrap items-center justify-between gap-3 border-b border-[var(--border-subtle)] px-4 py-3">
        <div className="flex min-w-0 items-center gap-2.5">
          {finished ? (
            error ? (
              <Badge tone="critical">Failed</Badge>
            ) : (
              <Badge tone="good">Complete</Badge>
            )
          ) : (
            <span className="pulse-ring flex size-6 items-center justify-center rounded-full bg-[var(--brand-wash)]">
              <Spinner className="size-3 text-[var(--text-link)]" />
            </span>
          )}
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold text-[var(--text-primary)]">
              {finished ? (error ? "Analysis failed" : "Analysis complete") : "Analysing live"}
            </p>
            <p className="truncate text-xs text-[var(--text-muted)]">{processName}</p>
          </div>
        </div>
        <div className="flex items-center gap-3">
          <span className="tabular text-xs text-[var(--text-muted)]">
            {formatElapsed(elapsed)} · {progress}%
          </span>
          {!finished ? (
            <Button
              size="sm"
              variant="ghost"
              onClick={() => {
                abortRef.current?.abort();
                onClose();
              }}
              title="Stop watching. The analysis keeps running on the server and will be saved."
            >
              Stop watching
            </Button>
          ) : (
            <Button size="sm" variant="secondary" onClick={onClose}>
              Close
            </Button>
          )}
        </div>
      </header>

      <div
        className="h-1 w-full bg-[var(--surface-inset)]"
        role="progressbar"
        aria-valuenow={progress}
        aria-valuemin={0}
        aria-valuemax={100}
      >
        <div
          className="h-full bg-[var(--seq-400)] transition-[width] duration-500"
          style={{ width: `${progress}%` }}
        />
      </div>

      {error ? (
        <div className="border-b border-[var(--border-subtle)] bg-[var(--status-critical-wash)] px-4 py-3">
          <p className="text-sm text-[var(--status-critical-ink)]">{error.message}</p>
          {error.detail ? (
            <p className="mono mt-1 text-[var(--text-secondary)]">{error.detail}</p>
          ) : null}
        </div>
      ) : null}

      <div className="grid gap-0 md:grid-cols-[minmax(0,20rem)_1fr]">
        <ol className="border-b border-[var(--border-subtle)] p-3 md:border-b-0 md:border-r">
          {stages.map((stage) => (
            <StageRow key={stage.id} stage={stage} />
          ))}
        </ol>

        <div ref={feedRef} className="max-h-[26rem] overflow-y-auto p-3">
          {feed.length === 0 ? (
            <p className="px-1 py-6 text-center text-xs text-[var(--text-muted)]">
              Waiting for the first search to be planned…
            </p>
          ) : (
            <ul className="space-y-1.5">
              {feed.map((entry) => (
                <li key={entry.id} className="rise-in flex items-start gap-2">
                  <FeedIcon kind={entry.kind} tone={entry.tone} />
                  <div className="min-w-0 flex-1">
                    <p className="text-xs leading-snug text-[var(--text-secondary)]">{entry.text}</p>
                    {entry.detail ? (
                      <p className="truncate text-[0.6875rem] text-[var(--text-muted)]">{entry.detail}</p>
                    ) : null}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </Panel>
  );
}

function StageRow({ stage }: { stage: StageView }) {
  const marker = {
    waiting: <span className="size-2 rounded-full bg-[var(--border-strong)]" />,
    running: <Spinner className="size-3 text-[var(--text-link)]" />,
    done: (
      <svg viewBox="0 0 12 12" className="size-3 text-[var(--status-good)]" aria-hidden="true">
        <path
          d="M2 6.4l2.6 2.6L10 3"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
    ),
    degraded: (
      <svg viewBox="0 0 12 12" className="size-3 text-[var(--status-warning)]" aria-hidden="true">
        <path d="M6 2v4.5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
        <circle cx="6" cy="9.4" r="1" fill="currentColor" />
      </svg>
    ),
    failed: (
      <svg viewBox="0 0 12 12" className="size-3 text-[var(--status-critical)]" aria-hidden="true">
        <path d="M3 3l6 6M9 3l-6 6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
      </svg>
    ),
  }[stage.state];

  return (
    <li
      className={`flex gap-2.5 rounded-md px-2 py-1.5 ${
        stage.state === "running" ? "bg-[var(--brand-wash)]" : ""
      }`}
    >
      <span className="mt-1 flex size-3 shrink-0 items-center justify-center">{marker}</span>
      <div className="min-w-0 flex-1">
        <p
          className={`text-xs ${
            stage.state === "waiting"
              ? "text-[var(--text-muted)]"
              : "font-medium text-[var(--text-primary)]"
          }`}
        >
          {stage.title}
        </p>
        {stage.summary ? (
          <p className="mt-0.5 text-[0.6875rem] leading-snug text-[var(--text-secondary)]">
            {stage.summary}
          </p>
        ) : null}
        {stage.model || stage.cached ? (
          <p className="mono mt-0.5 text-[var(--text-muted)]">
            {stage.cached ? "from cache" : stage.model}
          </p>
        ) : null}
      </div>
    </li>
  );
}

function FeedIcon({ kind, tone }: { kind: FeedEntry["kind"]; tone: Tone }) {
  const colour = {
    neutral: "var(--text-muted)",
    brand: "var(--text-link)",
    good: "var(--status-good)",
    warning: "var(--status-warning)",
    serious: "var(--status-serious)",
    critical: "var(--status-critical)",
    info: "var(--seq-400)",
  }[tone];

  const glyph = {
    query: "M6.5 11a4.5 4.5 0 1 0 0-9 4.5 4.5 0 0 0 0 9zM10 10l3 3",
    search: "M6.5 11a4.5 4.5 0 1 0 0-9 4.5 4.5 0 0 0 0 9zM10 10l3 3",
    source: "M3 2.5h6l3 3V13H3zM9 2.5V6h3",
    claims: "M2.5 7.5l3 3 6-6.5",
    note: "M7 3v5M7 11h.01",
  }[kind];

  return (
    <svg viewBox="0 0 14 14" className="mt-0.5 size-3 shrink-0" aria-hidden="true">
      <path
        d={glyph}
        fill="none"
        stroke={colour}
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function intentLabel(intent: string) {
  return intent
    .toLowerCase()
    .split("_")
    .join(" ");
}

function formatElapsed(seconds: number) {
  if (seconds < 60) return `${seconds}s`;
  return `${Math.floor(seconds / 60)}m ${String(seconds % 60).padStart(2, "0")}s`;
}
