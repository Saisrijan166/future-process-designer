"use client";

import Link from "next/link";
import { useCallback } from "react";
import { Badge, ButtonLink, Card, EmptyState, ErrorPanel, Loading, Stat } from "@/components/ui";
import { api } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { useApiResource } from "@/lib/use-api-resource";

export default function DashboardPage() {
  const load = useCallback(() => api.listProcesses(), []);
  const { data: processes, error, loading, reload } = useApiResource(load);

  const analysed = processes?.filter((process) => process.status === "ANALYZED") ?? [];
  const futureActivities = analysed.reduce((total, process) => total + process.futureActivityCount, 0);
  const opportunities = analysed.reduce((total, process) => total + process.opportunityCount, 0);

  return (
    <div className="space-y-8">
      <section>
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="max-w-3xl">
            <h1 className="text-2xl font-semibold tracking-tight text-ink-900">Processes</h1>
            <p className="mt-2 text-sm text-ink-600">
              Pick a process to see how it runs today, where AI could change it, and what the
              redesigned process looks like. The same pipeline runs for the sample processes and for
              anything you create — try it with a process from your own industry.
            </p>
          </div>
          <ButtonLink href="/processes/new">+ New process</ButtonLink>
        </div>

        {processes && processes.length > 0 ? (
          <dl className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-4">
            <Stat label="Processes" value={processes.length} />
            <Stat label="Analysed" value={analysed.length} hint={`${processes.length - analysed.length} not yet run`} />
            <Stat label="AI opportunities" value={opportunities} />
            <Stat label="Future activities" value={futureActivities} />
          </dl>
        ) : null}
      </section>

      {error ? (
        <ErrorPanel title="Could not load processes" message={error.message} onRetry={reload} />
      ) : null}

      {loading ? <Loading label="Loading processes…" /> : null}

      {processes && processes.length === 0 ? (
        <EmptyState
          title="No processes yet"
          description="Create one to run the analysis pipeline against it."
          action={<ButtonLink href="/processes/new">+ New process</ButtonLink>}
        />
      ) : null}

      {processes && processes.length > 0 ? (
        <Card className="overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full min-w-3xl border-collapse text-left text-sm">
              <thead className="border-b border-ink-200 bg-ink-50 text-xs tracking-wide text-ink-600 uppercase">
                <tr>
                  <th scope="col" className="px-4 py-3 font-semibold">Process</th>
                  <th scope="col" className="px-4 py-3 font-semibold">Industry</th>
                  <th scope="col" className="px-4 py-3 text-right font-semibold">Activities</th>
                  <th scope="col" className="px-4 py-3 text-right font-semibold">Opportunities</th>
                  <th scope="col" className="px-4 py-3 text-right font-semibold">Future steps</th>
                  <th scope="col" className="px-4 py-3 font-semibold">Status</th>
                  <th scope="col" className="px-4 py-3 font-semibold">Last analysed</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-ink-100">
                {processes.map((process) => (
                  <tr key={process.id} className="hover:bg-ink-50/70">
                    <th scope="row" className="max-w-md px-4 py-3 font-normal">
                      <Link
                        href={`/processes/${process.id}`}
                        className="font-medium text-brand-700 hover:underline"
                      >
                        {process.name}
                      </Link>
                      <p className="mt-0.5 line-clamp-1 text-xs text-ink-500">{process.description}</p>
                    </th>
                    <td className="px-4 py-3 text-ink-600">{process.industry}</td>
                    <td className="px-4 py-3 text-right tabular-nums text-ink-700">{process.activityCount}</td>
                    <td className="px-4 py-3 text-right tabular-nums text-ink-700">{process.opportunityCount}</td>
                    <td className="px-4 py-3 text-right tabular-nums text-ink-700">{process.futureActivityCount}</td>
                    <td className="px-4 py-3">
                      {process.status === "ANALYZED" ? (
                        <Badge tone="success">Analysed</Badge>
                      ) : (
                        <Badge tone="neutral">Current only</Badge>
                      )}
                    </td>
                    <td className="px-4 py-3 text-xs whitespace-nowrap text-ink-500">
                      {formatDateTime(process.lastAnalyzedAt)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      ) : null}
    </div>
  );
}
