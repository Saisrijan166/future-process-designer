"use client";

import Link from "next/link";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { RunDetails } from "@/components/run-details";
import { CurrentTab, EvidenceTab, FutureTab, TransitionTab } from "@/components/tabs-content";
import { TransformationSummary } from "@/components/transformation-summary";
import { Badge, Button, ErrorPanel, Loading, Spinner } from "@/components/ui";
import { ApiError, api } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { useApiResource } from "@/lib/use-api-resource";

type TabId = "current" | "transition" | "future" | "evidence";

const TABS: { id: TabId; label: string; hint: string }[] = [
  { id: "current", label: "Today", hint: "How the process runs right now" },
  { id: "transition", label: "AI ideas", hint: "Where AI could help — and what could go wrong" },
  { id: "future", label: "Redesigned", hint: "The future process, split between people and AI" },
  { id: "evidence", label: "Evidence", hint: "The research used to ground the analysis" },
];

/** What the pipeline is doing, in the order it does it. Timings are indicative. */
const PIPELINE_STAGES = [
  "Reading your process",
  "Finding relevant research",
  "Asking the AI",
  "Checking its answer",
  "Saving the result",
];

export default function ProcessDetailPage() {
  const params = useParams<{ id: string }>();
  const searchParams = useSearchParams();
  const router = useRouter();
  const processId = params.id;

  const requestedTab = searchParams.get("tab");
  const [tab, setTab] = useState<TabId>(
    TABS.some((entry) => entry.id === requestedTab) ? (requestedTab as TabId) : "current",
  );

  /** Keeps the URL in step with the visible tab, so the view can be linked to and reloaded. */
  const selectTab = useCallback(
    (next: TabId) => {
      setTab(next);
      const query = next === "current" ? "" : `?tab=${next}`;
      window.history.replaceState(null, "", `/processes/${processId}${query}`);
    },
    [processId],
  );
  const [analysing, setAnalysing] = useState(false);
  const [analysisError, setAnalysisError] = useState<ApiError | null>(null);
  const [flash, setFlash] = useState<string | null>(null);
  const [deleting, setDeleting] = useState(false);

  const load = useCallback(() => api.getComparison(processId), [processId]);
  const {
    data: comparison,
    error: loadError,
    loading,
    reload,
    replace: replaceComparison,
  } = useApiResource(load);

  const runAnalysis = useCallback(async () => {
    setAnalysing(true);
    setAnalysisError(null);
    setFlash(null);
    try {
      const result = await api.analyze(processId);
      replaceComparison(await api.getComparison(processId));
      selectTab("transition");
      setFlash(
        `Found ${result.opportunitiesGenerated} AI ${
          result.opportunitiesGenerated === 1 ? "idea" : "ideas"
        } and designed a ${result.futureActivitiesGenerated}-step future process.`,
      );
    } catch (caught) {
      setAnalysisError(caught as ApiError);
      try {
        replaceComparison(await api.getComparison(processId));
      } catch {
        // Keep whatever is on screen; the analysis error is the useful message.
      }
    } finally {
      setAnalysing(false);
    }
  }, [processId, replaceComparison, selectTab]);

  const autoStarted = useRef(false);
  const shouldAutoAnalyse = searchParams.get("analyze") === "1";
  useEffect(() => {
    if (autoStarted.current || !shouldAutoAnalyse || !comparison) return;
    autoStarted.current = true;
    const timer = setTimeout(() => {
      router.replace(`/processes/${processId}`, { scroll: false });
      void runAnalysis();
    }, 0);
    return () => clearTimeout(timer);
  }, [shouldAutoAnalyse, comparison, processId, router, runAnalysis]);

  async function handleDelete() {
    if (!window.confirm("Delete this process and everything generated from it?")) return;
    setDeleting(true);
    try {
      await api.deleteProcess(processId);
      router.push("/");
    } catch (caught) {
      setAnalysisError(caught as ApiError);
      setDeleting(false);
    }
  }

  if (loadError) {
    return (
      <ErrorPanel title="Could not load this process" message={loadError.message} onRetry={reload} />
    );
  }

  if (loading || !comparison) {
    return <Loading label="Loading process…" />;
  }

  const { process, summary, latestRun } = comparison;
  const analysed = process.status === "ANALYZED";

  const countFor = (id: TabId) =>
    id === "current"
      ? summary.currentActivityCount
      : id === "transition"
        ? summary.opportunityCount
        : id === "future"
          ? summary.futureActivityCount
          : summary.evidenceCount;

  return (
    <div className="space-y-6">
      <Link href="/" className="inline-flex items-center gap-1 text-sm text-brand-700 hover:underline">
        <span aria-hidden="true">←</span> All processes
      </Link>

      <header className="rounded-2xl border border-ink-200 bg-white p-6">
        <div className="flex flex-wrap items-start justify-between gap-6">
          <div className="max-w-3xl">
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-2xl font-semibold tracking-tight text-ink-900">{process.name}</h1>
              {analysed ? (
                <Badge tone="success">Analysed</Badge>
              ) : (
                <Badge tone="neutral">Not analysed yet</Badge>
              )}
              {process.shared ? (
                <Badge
                  tone="neutral"
                  title="A shared sample: everyone can read and analyse it, nobody can edit or delete it"
                >
                  Shared sample
                </Badge>
              ) : (
                <Badge tone="accent">Yours</Badge>
              )}
            </div>
            <p className="mt-1 text-sm text-ink-500">{process.industry}</p>
            <p className="mt-3 text-sm leading-relaxed text-ink-700">{process.description}</p>
            {process.lastAnalyzedAt ? (
              <p className="mt-3 text-xs text-ink-500">
                Last analysed {formatDateTime(process.lastAnalyzedAt)}
              </p>
            ) : null}
          </div>

          <div className="flex flex-col items-stretch gap-2">
            <Button onClick={() => void runAnalysis()} disabled={analysing} className="min-w-52">
              {analysing ? <Spinner /> : null}
              {analysing ? "Analysing…" : analysed ? "Run the analysis again" : "Analyse this process"}
            </Button>
            <p className="max-w-52 text-xs leading-relaxed text-ink-400">
              {analysed
                ? "Replaces the current result. Answers vary slightly each run."
                : "Generates the AI ideas and the redesigned process. 5–30 seconds."}
            </p>
            {process.shared ? (
              <p className="mt-1 text-xs leading-relaxed text-ink-400">
                This is a shared sample, so it cannot be edited or deleted. Analysing it updates it
                for everyone.
              </p>
            ) : (
              <button
                type="button"
                onClick={() => void handleDelete()}
                disabled={deleting || analysing}
                className="mt-1 text-left text-xs font-medium text-ink-500 transition-colors hover:text-rose-700 disabled:opacity-50"
              >
                {deleting ? "Deleting…" : "Delete this process"}
              </button>
            )}
          </div>
        </div>
      </header>

      {analysing ? <AnalysisProgress /> : null}

      {flash ? (
        <div
          role="status"
          className="animate-rise flex flex-wrap items-center justify-between gap-3 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-status-good-ink"
        >
          <span className="flex items-center gap-2 font-medium">
            <svg className="size-4" viewBox="0 0 16 16" fill="none" aria-hidden="true">
              <circle cx="8" cy="8" r="6.5" stroke="currentColor" strokeWidth="1.5" />
              <path d="M5.2 8.2l2 2 3.6-4" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
            {flash}
          </span>
          <button
            type="button"
            onClick={() => setFlash(null)}
            className="text-xs font-medium hover:underline"
          >
            Dismiss
          </button>
        </div>
      ) : null}

      {analysisError ? (
        <ErrorPanel
          title="The analysis did not complete"
          message={analysisError.message}
          detail={analysisError.reason}
          onRetry={() => void runAnalysis()}
        />
      ) : null}

      {!analysed && !analysing ? (
        <div className="rounded-xl border border-ink-300 border-dashed bg-white p-5">
          <p className="text-sm font-semibold text-ink-900">This process has not been analysed yet.</p>
          <p className="mt-1 max-w-3xl text-sm leading-relaxed text-ink-600">
            Only the <strong>Today</strong> tab has anything in it so far. Press{" "}
            <strong>Analyse this process</strong> above and the system will generate the AI ideas and
            the redesigned process — live, not looked up.
          </p>
        </div>
      ) : null}

      <TransformationSummary comparison={comparison} />

      {latestRun ? <RunDetails run={latestRun} processId={processId} /> : null}

      <div>
        <div
          role="tablist"
          aria-label="Process views"
          className="flex flex-wrap gap-1 border-b border-ink-200"
        >
          {TABS.map((entry) => {
            const selected = tab === entry.id;
            const count = countFor(entry.id);
            return (
              <button
                key={entry.id}
                role="tab"
                type="button"
                aria-selected={selected}
                aria-controls={`panel-${entry.id}`}
                id={`tab-${entry.id}`}
                onClick={() => selectTab(entry.id)}
                className={`-mb-px flex items-center gap-2 border-b-2 px-4 py-3 text-sm font-medium transition-colors ${
                  selected
                    ? "border-ink-900 text-ink-900"
                    : "border-transparent text-ink-500 hover:border-ink-300 hover:text-ink-800"
                }`}
              >
                {entry.label}
                <span
                  className={`tabular rounded-full px-1.5 py-0.5 text-[11px] ${
                    selected ? "bg-ink-900 text-white" : "bg-ink-100 text-ink-600"
                  }`}
                >
                  {count}
                </span>
              </button>
            );
          })}
        </div>

        <p className="pt-3 text-xs text-ink-500">
          {TABS.find((entry) => entry.id === tab)?.hint}
        </p>

        <div
          role="tabpanel"
          id={`panel-${tab}`}
          aria-labelledby={`tab-${tab}`}
          className="animate-rise pt-5"
          key={tab}
        >
          {tab === "current" ? (
            <CurrentTab
              activities={comparison.current.activities}
              problems={comparison.current.problems}
            />
          ) : null}
          {tab === "transition" ? (
            <TransitionTab opportunities={comparison.transition.opportunities} />
          ) : null}
          {tab === "future" ? (
            <FutureTab
              futureActivities={comparison.future.activities}
              interventions={comparison.future.interventions}
            />
          ) : null}
          {tab === "evidence" ? <EvidenceTab evidence={comparison.transition.evidence} /> : null}
        </div>
      </div>
    </div>
  );
}

/**
 * A staged indicator rather than a bare spinner.
 *
 * The stages are the pipeline's real steps, but the highlight advances on a timer —
 * the API returns one response at the end, so there is no server-sent progress to
 * follow. It is presented as "what is happening", never as a measured percentage.
 */
function AnalysisProgress() {
  const [stage, setStage] = useState(0);

  useEffect(() => {
    const timer = setInterval(() => {
      setStage((current) => Math.min(current + 1, PIPELINE_STAGES.length - 1));
    }, 3500);
    return () => clearInterval(timer);
  }, []);

  return (
    <div
      role="status"
      aria-live="polite"
      className="animate-rise overflow-hidden rounded-xl border border-ink-300 bg-white"
    >
      <div className="relative h-1 bg-ink-100">
        <div className="absolute inset-y-0 -left-1/3 w-1/3 animate-sweep bg-ink-900" />
      </div>
      <div className="p-5">
        <p className="flex items-center gap-2 text-sm font-semibold text-ink-900">
          <Spinner className="size-4" />
          Analysing this process…
        </p>
        <ol className="mt-3 grid gap-2 sm:grid-cols-5">
          {PIPELINE_STAGES.map((label, index) => {
            const done = index < stage;
            const active = index === stage;
            return (
              <li
                key={label}
                className={`flex items-center gap-2 rounded-lg px-2.5 py-2 text-xs transition-colors ${
                  active ? "bg-ink-900 text-white" : done ? "bg-ink-100 text-ink-600" : "text-ink-400"
                }`}
              >
                <span className="shrink-0">
                  {done ? (
                    <svg className="size-3.5" viewBox="0 0 16 16" fill="none" aria-hidden="true">
                      <path d="M3.5 8.5l3 3 6-7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                    </svg>
                  ) : (
                    <span className="block size-1.5 rounded-full bg-current" />
                  )}
                </span>
                {label}
              </li>
            );
          })}
        </ol>
        <p className="mt-3 text-xs text-ink-500">
          If the first AI service is busy or out of quota, the backup takes over automatically.
        </p>
      </div>
    </div>
  );
}
