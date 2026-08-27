"use client";

import { useCallback, useEffect, useState } from "react";
import { StatTile } from "@/components/charts";
import { Badge, ErrorPanel, Panel, SectionHeading, Skeleton, StatusDot } from "@/components/ui";
import { api } from "@/lib/api";
import { useApiResource } from "@/lib/use-api-resource";

/**
 * What the engine can do right now.
 *
 * <p>Unusual to expose, and worth it here. On a free tier the answer changes through the day: a
 * model's per-minute allowance runs down, a connector starts refusing requests, a stage waits. An
 * application that cannot tell you why it is slow gets assumed to be broken, and this page is the
 * difference between an unexplained pause and a visible queue.
 *
 * <p>It is also the honest version of the "what if a free tier disappears?" answer the brief asks
 * for. Rather than a paragraph promising the design is portable, this shows the provider list, the
 * per-task routing, and which connectors are currently answering.
 */
export default function SystemPage() {
  const load = useCallback(() => api.getAiStatus(), []);
  const { data: status, error, loading, reload } = useApiResource(load);
  const [tick, setTick] = useState(0);

  // Budgets refill continuously, so a stale page is misleading in a way a stale list is not.
  useEffect(() => {
    const timer = window.setInterval(() => setTick((value) => value + 1), 20_000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    if (tick > 0) reload();
  }, [tick, reload]);

  if (error) {
    return <ErrorPanel title="Could not read the engine status" message={error.message} onRetry={reload} />;
  }

  if (loading && !status) {
    return (
      <div className="mx-auto max-w-5xl space-y-4">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-32 w-full" />
        <Skeleton className="h-48 w-full" />
      </div>
    );
  }

  if (!status) return null;

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <header>
        <h1 className="text-xl font-semibold sm:text-2xl">Engine</h1>
        <p className="mt-1 max-w-3xl text-sm leading-relaxed text-[var(--text-secondary)]">
          Which models each stage will use, how much free-tier budget is left, and which research
          connectors are live. This refreshes every twenty seconds because the numbers genuinely move.
        </p>
      </header>

      <div className="grid gap-3 sm:grid-cols-4">
        <StatTile
          label="Pipeline"
          value={status.pipeline === "staged" ? "10 stages" : "single call"}
          hint={status.pipeline === "staged" ? "with live research" : "one prompt, low quota cost"}
        />
        <StatTile
          label="Providers with a key"
          value={status.providers.filter((provider) => provider.configured).length}
          tone={status.configured ? "good" : "critical"}
          hint={status.configured ? undefined : "analysis will fail until one is set"}
        />
        <StatTile
          label="Research connectors"
          value={status.researchConnectors.filter((connector) => connector.enabled).length}
          tone={status.researchEnabled ? "neutral" : "warning"}
          hint={status.researchEnabled ? "all free, none needing a key" : "live research is switched off"}
        />
        <StatTile
          label="Response cache"
          value={status.cacheEnabled ? "On" : "Off"}
          hint={status.cacheEnabled ? "an unchanged re-run costs no quota" : undefined}
        />
      </div>

      <Panel className="p-4">
        <SectionHeading
          title="Free-tier budget, per model"
          hint="Synchronised from each provider's own rate-limit headers. The shared row is the organisation-wide ceiling, which on Groq is the one that actually binds."
        />
        {status.budgets.length === 0 ? (
          <p className="py-4 text-xs text-[var(--text-muted)]">
            Nothing has been called yet, so no budget has been observed. Run an analysis and this fills in.
          </p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[36rem] text-left text-xs">
              <thead>
                <tr className="border-b border-[var(--border-subtle)]">
                  <th className="eyebrow pb-2">Model</th>
                  <th className="eyebrow pb-2 text-right">Tokens left this minute</th>
                  <th className="eyebrow pb-2 text-right">Requests left today</th>
                  <th className="eyebrow pb-2 text-right">Calls</th>
                  <th className="eyebrow pb-2 text-right">Time spent waiting</th>
                </tr>
              </thead>
              <tbody>
                {status.budgets.map((budget) => {
                  const tokenFraction = budget.tokensPerMinute
                    ? budget.remainingTokens / budget.tokensPerMinute
                    : 1;
                  const shared = budget.key.endsWith(":*shared*");
                  return (
                    <tr key={budget.key} className="border-b border-[var(--border-subtle)] last:border-0">
                      <td className="py-2">
                        <span className="mono text-[var(--text-primary)]">
                          {shared ? `${budget.key.split(":")[0]} (shared ceiling)` : budget.key}
                        </span>
                        {budget.cooling ? (
                          <Badge tone="warning" className="ml-2">
                            cooling down
                          </Badge>
                        ) : null}
                      </td>
                      <td className="tabular py-2 text-right">
                        <span
                          style={{
                            color:
                              tokenFraction < 0.2
                                ? "var(--status-warning-ink)"
                                : "var(--text-secondary)",
                          }}
                        >
                          {Math.round(budget.remainingTokens).toLocaleString("en-IN")}
                        </span>
                        <span className="text-[var(--text-muted)]">
                          {" "}
                          / {budget.tokensPerMinute.toLocaleString("en-IN")}
                        </span>
                      </td>
                      <td className="tabular py-2 text-right text-[var(--text-secondary)]">
                        {Math.round(budget.remainingRequests).toLocaleString("en-IN")}
                      </td>
                      <td className="tabular py-2 text-right text-[var(--text-secondary)]">
                        {budget.admitted}
                        {budget.rejected > 0 ? (
                          <span className="text-[var(--status-warning-ink)]"> · {budget.rejected} refused</span>
                        ) : null}
                      </td>
                      <td className="tabular py-2 text-right text-[var(--text-secondary)]">
                        {budget.throttledMillis > 0
                          ? `${(budget.throttledMillis / 1000).toFixed(1)}s`
                          : "—"}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </Panel>

      <div className="grid gap-4 lg:grid-cols-2">
        <Panel className="p-4">
          <SectionHeading
            title="Which model does which job"
            hint="In order of preference. A stage falls to the next candidate when the first has no budget."
          />
          <ul className="space-y-1.5">
            {Object.entries(status.routing).map(([task, candidates]) => (
              <li key={task} className="flex flex-wrap items-baseline gap-x-2 gap-y-1">
                <span className="w-40 shrink-0 text-xs text-[var(--text-secondary)]">
                  {task.replace(/-/g, " ")}
                </span>
                <span className="mono min-w-0 flex-1 text-[var(--text-muted)]">
                  {candidates.length > 0 ? candidates.join("  →  ") : "nothing configured"}
                </span>
              </li>
            ))}
          </ul>
        </Panel>

        <div className="space-y-4">
          <Panel className="p-4">
            <SectionHeading title="Providers" />
            <ul className="space-y-1.5">
              {status.providers.map((provider) => (
                <li key={provider.name} className="flex items-center justify-between gap-2">
                  <StatusDot
                    tone={provider.configured ? "good" : "neutral"}
                    label={provider.name}
                  />
                  <span className="mono text-[var(--text-muted)]">
                    {provider.configured ? provider.defaultModel || "—" : "no key configured"}
                  </span>
                </li>
              ))}
            </ul>
          </Panel>

          <Panel className="p-4">
            <SectionHeading
              title="Research connectors"
              hint="Every one free and keyless. Two more (Tavily, Brave) stay dormant unless a key is supplied."
            />
            <ul className="grid grid-cols-2 gap-x-3 gap-y-1.5">
              {status.researchConnectors.map((connector) => (
                <li key={connector.id}>
                  <StatusDot
                    tone={connector.enabled ? "good" : "neutral"}
                    label={connector.displayName}
                  />
                </li>
              ))}
            </ul>
          </Panel>
        </div>
      </div>

      <Panel quiet className="p-4">
        <p className="text-xs leading-relaxed text-[var(--text-secondary)]">
          <strong className="text-[var(--text-primary)]">If a free tier disappears.</strong> Every model
          call goes through one interface with a routing table, so moving a stage to another provider is
          a configuration change: Cerebras, OpenRouter and a local Ollama already speak the same dialect
          and need only a base URL and a key. The research layer is eleven independent connectors, so
          losing one degrades a run rather than ending it. What none of that survives is losing every
          provider at once — and that is why the run trace records which model actually answered.
        </p>
      </Panel>
    </div>
  );
}
