"use client";

import Link from "next/link";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { ComparisonStrip } from "@/components/comparison-strip";
import { RunDetails } from "@/components/run-details";
import { CurrentTab, EvidenceTab, FutureTab, TransitionTab } from "@/components/tabs-content";
import { Badge, Button, ErrorPanel, Loading, Spinner } from "@/components/ui";
import { ApiError, api } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { useApiResource } from "@/lib/use-api-resource";

type TabId = "current" | "transition" | "future" | "evidence";

const TABS: { id: TabId; label: string; hint: string }[] = [
  { id: "current", label: "Current", hint: "How the process runs today" },
  { id: "transition", label: "AI ideas", hint: "Where AI could help, and the risks" },
  { id: "future", label: "Future", hint: "The redesigned process, step by step" },
  { id: "evidence", label: "Evidence", hint: "The research used to ground the analysis" },
];

export default function ProcessDetailPage() {
  const params = useParams<{ id: string }>();
  const searchParams = useSearchParams();
  const router = useRouter();
  const processId = params.id;

  const [tab, setTab] = useState<TabId>("current");
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
      setTab("transition");
      setFlash(
        `Generated ${result.opportunitiesGenerated} AI opportunit${
          result.opportunitiesGenerated === 1 ? "y" : "ies"
        }, ${result.futureActivitiesGenerated} future activities and ${
          result.interventionsGenerated
        } interventions.`,
      );
    } catch (caught) {
      setAnalysisError(caught as ApiError);
      // Reload anyway: a failed run is still recorded, and the panel should show why it failed.
      try {
        replaceComparison(await api.getComparison(processId));
      } catch {
        // Keep whatever is already on screen; the analysis error is the useful message.
      }
    } finally {
      setAnalysing(false);
    }
  }, [processId, replaceComparison]);

  // Arriving from the create form, start the analysis without a second click — that form's button
  // promised "Create and analyse". The trigger lives in an effect but only ever fires work
  // asynchronously, so it does not cascade renders.
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

  return (
    <div className="space-y-6">
      <div>
        <Link href="/" className="text-sm text-brand-700 hover:underline">
          ← All processes
        </Link>

        <div className="mt-2 flex flex-wrap items-start justify-between gap-4">
          <div className="max-w-3xl">
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-2xl font-semibold tracking-tight text-ink-900">{process.name}</h1>
              {analysed ? (
                <Badge tone="success">Analysed</Badge>
              ) : (
                <Badge tone="neutral">Not analysed yet</Badge>
              )}
              {process.origin === "USER" ? <Badge tone="info">Created here</Badge> : null}
            </div>
            <p className="mt-1 text-sm text-ink-500">{process.industry}</p>
            <p className="mt-2 text-sm leading-relaxed text-ink-700">{process.description}</p>
            {process.lastAnalyzedAt ? (
              <p className="mt-2 text-xs text-ink-500">
                Last analysed {formatDateTime(process.lastAnalyzedAt)}
              </p>
            ) : null}
          </div>

          <div className="flex flex-col items-end gap-2">
            <Button onClick={() => void runAnalysis()} disabled={analysing} className="min-w-44">
              {analysing ? <Spinner /> : null}
              {analysing ? "Analysing…" : analysed ? "Run it again" : "Analyse this process"}
            </Button>
            <p className="max-w-48 text-right text-xs text-ink-400">
              {analysed
                ? "Re-running replaces the result. Answers vary slightly each time."
                : "Generates the AI ideas and the future process. Takes 5–30 seconds."}
            </p>
            <button
              type="button"
              onClick={() => void handleDelete()}
              disabled={deleting || analysing}
              className="text-xs font-medium text-ink-500 hover:text-rose-700 disabled:opacity-50"
            >
              {deleting ? "Deleting…" : "Delete process"}
            </button>
          </div>
        </div>
      </div>

      {analysing ? (
        <div
          role="status"
          aria-live="polite"
          className="flex items-center gap-3 rounded-xl border border-brand-200 bg-brand-50 p-4 text-sm text-brand-900"
        >
          <Spinner className="size-5 text-brand-600" />
          <div>
            <p className="font-medium">Working on it…</p>
            <p className="text-xs text-brand-800">
              Finding relevant research → asking the AI → checking its answer → saving the result.
              Usually 5–30 seconds. If the first AI service is busy, the backup takes over
              automatically.
            </p>
          </div>
        </div>
      ) : null}

      {flash ? (
        <div
          role="status"
          className="flex items-center justify-between gap-3 rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-900"
        >
          <span>{flash}</span>
          <button
            type="button"
            onClick={() => setFlash(null)}
            className="text-xs font-medium text-emerald-800 hover:underline"
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
        <div className="rounded-xl border border-brand-200 bg-brand-50 p-4">
          <p className="text-sm font-medium text-brand-900">This process has not been analysed yet.</p>
          <p className="mt-1 text-sm text-brand-900/80">
            Only the <strong>Current</strong> tab has anything in it so far. Press{" "}
            <strong>Analyse this process</strong> above to generate the AI ideas and the redesigned
            future process — they are produced live, not looked up.
          </p>
        </div>
      ) : null}

      <ComparisonStrip comparison={comparison} />

      {latestRun ? <RunDetails run={latestRun} processId={processId} /> : null}

      <div>
        <div role="tablist" aria-label="Process views" className="flex flex-wrap gap-1 border-b border-ink-200">
          {TABS.map((entry) => {
            const count =
              entry.id === "current"
                ? summary.currentActivityCount
                : entry.id === "transition"
                  ? summary.opportunityCount
                  : entry.id === "future"
                    ? summary.futureActivityCount
                    : summary.evidenceCount;
            const selected = tab === entry.id;
            return (
              <button
                key={entry.id}
                role="tab"
                type="button"
                aria-selected={selected}
                aria-controls={`panel-${entry.id}`}
                id={`tab-${entry.id}`}
                onClick={() => setTab(entry.id)}
                className={`-mb-px border-b-2 px-4 py-2.5 text-sm font-medium transition-colors ${
                  selected
                    ? "border-brand-600 text-brand-700"
                    : "border-transparent text-ink-500 hover:border-ink-300 hover:text-ink-800"
                }`}
              >
                {entry.label}
                <span className="ml-1.5 rounded-full bg-ink-100 px-1.5 py-0.5 text-[11px] tabular-nums text-ink-600">
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
          className="pt-4"
        >
          {tab === "current" ? (
            <CurrentTab activities={comparison.current.activities} problems={comparison.current.problems} />
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
