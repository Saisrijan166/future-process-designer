"use client";

import Link from "next/link";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AnalysisConsole } from "@/components/analysis-console";
import {
  CurrentPanel,
  DiagnosisPanel,
  EvidencePanel,
  FuturePanel,
  ImpactPanel,
  OpportunitiesPanel,
  OverviewPanel,
  RisksPanel,
  RoadmapPanel,
  TracePanel,
} from "@/components/process-views";
import {
  Badge,
  Button,
  ButtonLink,
  ErrorPanel,
  Panel,
  Skeleton,
  Tabs,
  useToast,
  type TabDefinition,
} from "@/components/ui";
import { ApiError, api } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { useApiResource } from "@/lib/use-api-resource";
import type { AnalysisResult, AnalysisStage, ProcessDetail, ResearchRun } from "@/lib/types";

/**
 * One process, as a workspace.
 *
 * <p>Ten tabs rather than one long page, in the order the questions get asked: what is this, what
 * is wrong with it, what did we find out, what should we do, what would it look like, what is it
 * worth, what could go wrong, in what order, and how do you know. The last of those — the evidence
 * and the trace — is reachable from anywhere via the citation chips, because checking a claim
 * should not mean losing your place in the argument.
 */
export default function ProcessPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const search = useSearchParams();
  const toast = useToast();
  const processId = params.id;

  const [tab, setTab] = useState("overview");
  // The new-process form redirects here with ?analyze=1, so creating a process and analysing it is
  // one action rather than two — the second of which a first-time visitor would not know to take.
  const [analysing, setAnalysing] = useState(search.get("analyze") === "1");
  // `null` means "not fetched yet" for both, which is what the loading state is derived from —
  // there is no separate loading flag to keep in step with the data.
  const [research, setResearch] = useState<ResearchRun | null>(null);
  const [researchSettled, setResearchSettled] = useState(false);
  const [stages, setStages] = useState<AnalysisStage[] | null>(null);
  const requested = useRef({ research: false, stages: false });

  const load = useCallback(() => api.getProcess(processId), [processId]);
  const { data: detail, error, loading, reload, replace } = useApiResource(load);

  // The evidence and trace tabs pull their own data, and only when opened. Both are large — every
  // quote, every prompt — and most visits never open them. The in-flight guard is a ref rather than
  // state so the effect starts a request without also queueing a render.
  useEffect(() => {
    if (tab !== "evidence" || !detail || requested.current.research) return;
    requested.current.research = true;
    api
      .getResearch(processId)
      .then(setResearch)
      .catch(() => setResearch(null))
      .finally(() => setResearchSettled(true));
  }, [tab, detail, processId]);

  useEffect(() => {
    if (tab !== "trace" || !detail?.latestRun || requested.current.stages) return;
    requested.current.stages = true;
    const runId = detail.latestRun.id;
    api
      .getRunStages(processId, runId)
      .then(setStages)
      .catch(() => setStages([]));
  }, [tab, detail, processId]);

  const researchLoading = tab === "evidence" && !!detail && !researchSettled;
  const stagesLoading = tab === "trace" && !!detail?.latestRun && stages === null;

  const onAnalysisComplete = useCallback(
    (result: AnalysisResult) => {
      replace(result.detail);
      // Both are now stale: the run that produced them has been replaced.
      requested.current = { research: false, stages: false };
      setResearch(null);
      setResearchSettled(false);
      setStages(null);
      toast.push({
        tone: result.warnings.length > 0 ? "warning" : "good",
        title: "Analysis complete",
        message: `${result.opportunitiesGenerated} recommendations, ${result.citationsStored} citations, ${result.risksGenerated} risks${
          result.warnings.length > 0 ? ` · ${result.warnings.length} warnings` : ""
        }`,
      });
    },
    [replace, toast],
  );

  const tabs: TabDefinition[] = useMemo(() => {
    if (!detail) return [];
    return [
      { id: "overview", label: "Overview" },
      { id: "current", label: "Today", count: detail.activities.length },
      { id: "diagnosis", label: "Diagnosis", count: detail.problems.length },
      { id: "opportunities", label: "Recommendations", count: detail.opportunities.length },
      { id: "future", label: "Future process", count: detail.futureActivities.length },
      { id: "impact", label: "Impact", count: detail.impacts.length },
      { id: "risks", label: "Risks", count: detail.risks.length },
      { id: "roadmap", label: "Roadmap", count: detail.roadmap.length },
      { id: "evidence", label: "Evidence", count: detail.research?.claimCount ?? null },
      { id: "trace", label: "Run trace", count: detail.latestRun?.stageCount || null },
    ];
  }, [detail]);

  if (error) {
    return (
      <div className="mx-auto max-w-3xl py-8">
        <ErrorPanel
          title={error.status === 404 ? "Process not found" : "Could not load this process"}
          message={error.message}
          onRetry={reload}
        />
        <div className="mt-4">
          <ButtonLink href="/">Back to processes</ButtonLink>
        </div>
      </div>
    );
  }

  if (loading || !detail) {
    return (
      <div className="mx-auto max-w-[84rem] space-y-4">
        <Skeleton className="h-8 w-1/3" />
        <Skeleton className="h-4 w-2/3" />
        <div className="grid gap-3 sm:grid-cols-4">
          {Array.from({ length: 4 }).map((_, index) => (
            <Skeleton key={index} className="h-20 w-full" />
          ))}
        </div>
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  const analysed = detail.process.status === "ANALYZED";

  return (
    <div className="mx-auto max-w-[84rem] space-y-5">
      <header className="space-y-3">
        <nav className="no-print flex items-center gap-1.5 text-xs text-[var(--text-muted)]">
          <Link href="/" className="hover:text-[var(--text-primary)]">
            Processes
          </Link>
          <span>/</span>
          <span className="truncate text-[var(--text-secondary)]">{detail.process.name}</span>
        </nav>

        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-xl font-semibold text-[var(--text-primary)] sm:text-2xl">
                {detail.process.name}
              </h1>
              <Badge tone={analysed ? "good" : "neutral"}>{analysed ? "Analysed" : "Not yet analysed"}</Badge>
              {detail.process.shared ? (
                <Badge tone="info" title="A shared sample: everyone can read and analyse it, nobody can edit it.">
                  shared sample
                </Badge>
              ) : null}
              {detail.scorecard ? (
                <Badge
                  tone={detail.scorecard.overallScore >= 70 ? "good" : detail.scorecard.overallScore >= 55 ? "info" : "warning"}
                  title="Measured from the run's own output — coverage, grounding, corroboration, reviewer agreement, specificity and traceability."
                >
                  score {detail.scorecard.overallScore}/100
                </Badge>
              ) : null}
            </div>
            <p className="mt-1 text-xs text-[var(--text-muted)]">
              {detail.process.industry}
              {detail.process.lastAnalyzedAt
                ? ` · last analysed ${formatDateTime(detail.process.lastAnalyzedAt)}`
                : ""}
            </p>
            <p className="mt-2 max-w-3xl text-[0.8125rem] leading-relaxed text-[var(--text-secondary)]">
              {detail.process.description}
            </p>
          </div>

          <div className="no-print flex shrink-0 flex-wrap gap-2">
            <Button variant="ghost" size="sm" onClick={() => window.print()}>
              Print
            </Button>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => downloadJson(detail)}
              title="Every row this analysis produced, as JSON."
            >
              Export
            </Button>
            {!detail.process.shared ? (
              <Button
                variant="danger"
                size="sm"
                onClick={async () => {
                  if (!window.confirm(`Delete "${detail.process.name}" and everything generated for it?`)) {
                    return;
                  }
                  try {
                    await api.deleteProcess(processId);
                    router.push("/");
                  } catch (caught) {
                    toast.push({
                      tone: "critical",
                      title: "Could not delete",
                      message: caught instanceof ApiError ? caught.message : "Something went wrong.",
                    });
                  }
                }}
              >
                Delete
              </Button>
            ) : null}
            <Button variant="primary" size="sm" disabled={analysing} onClick={() => setAnalysing(true)}>
              {analysed ? "Re-run analysis" : "Run analysis"}
            </Button>
          </div>
        </div>
      </header>

      {analysing ? (
        <AnalysisConsole
          processId={processId}
          processName={detail.process.name}
          onComplete={onAnalysisComplete}
          onClose={() => setAnalysing(false)}
        />
      ) : null}

      {!analysed && !analysing ? (
        <Panel className="border-dashed p-4">
          <p className="text-sm text-[var(--text-secondary)]">
            Nothing has been generated for this process yet. Running the analysis searches the live web
            for this domain, proposes interventions that cite what they rest on, has a second model
            review them, and designs the future state. It takes a few minutes and you can watch every
            stage as it happens.
          </p>
        </Panel>
      ) : null}

      <Tabs tabs={tabs} active={tab} onChange={setTab} />

      <div className="pb-8">
        {tab === "overview" ? <OverviewPanel detail={detail} onOpenTab={setTab} /> : null}
        {tab === "current" ? <CurrentPanel detail={detail} /> : null}
        {tab === "diagnosis" ? <DiagnosisPanel detail={detail} /> : null}
        {tab === "opportunities" ? <OpportunitiesPanel detail={detail} /> : null}
        {tab === "future" ? <FuturePanel detail={detail} /> : null}
        {tab === "impact" ? <ImpactPanel detail={detail} /> : null}
        {tab === "risks" ? <RisksPanel detail={detail} /> : null}
        {tab === "roadmap" ? <RoadmapPanel detail={detail} /> : null}
        {tab === "evidence" ? (
          <EvidencePanel research={research} loading={researchLoading} detail={detail} />
        ) : null}
        {tab === "trace" ? <TracePanel stages={stages ?? []} detail={detail} loading={stagesLoading} /> : null}
      </div>
    </div>
  );
}

/**
 * Downloads the whole analysis as JSON.
 *
 * <p>Everything on screen is structured data, so exporting it is a serialisation rather than a
 * report generator — which is the point the assignment is making about not storing the future
 * process as prose.
 */
function downloadJson(detail: ProcessDetail) {
  const blob = new Blob([JSON.stringify(detail, null, 2)], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `${detail.process.name.toLowerCase().replace(/[^a-z0-9]+/g, "-")}-analysis.json`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
