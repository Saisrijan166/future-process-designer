"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import {
  Button,
  Card,
  ErrorPanel,
  Field,
  INPUT_CLASSES,
  SectionHeading,
  Spinner,
} from "@/components/ui";
import { ApiError, api } from "@/lib/api";
import type { CreateProcessRequest, Role, SystemTool } from "@/lib/types";

interface ActivityDraft {
  key: string;
  name: string;
  description: string;
  roles: string;
  systems: string;
}

let nextKey = 0;
function emptyActivity(): ActivityDraft {
  nextKey += 1;
  return { key: `activity-${nextKey}`, name: "", description: "", roles: "", systems: "" };
}

function splitList(value: string): string[] {
  return value
    .split(",")
    .map((entry) => entry.trim())
    .filter(Boolean);
}

/**
 * An example the demo can load with one click. It is a starting point for the form, not a code
 * path: it fills in the same inputs a person would type, and is analysed by exactly the same
 * pipeline as everything else.
 */
const EXAMPLE = {
  name: "Employee Expense Reimbursement",
  industry: "Corporate Shared Services",
  description:
    "How an employee claims a travel expense and how finance checks, approves and pays it back.",
  activities: [
    {
      name: "Submit the expense claim",
      description: "The employee fills a form and attaches photographed receipts.",
      roles: "Employee",
      systems: "Expense Portal",
    },
    {
      name: "Check receipts against policy",
      description:
        "A finance associate reads each receipt and checks the amount, date and category against the travel policy.",
      roles: "Finance Associate",
      systems: "Expense Portal, Policy Document",
    },
    {
      name: "Route for manager approval",
      description: "The claim is emailed to the reporting manager for sign-off.",
      roles: "Finance Associate, Reporting Manager",
      systems: "Email",
    },
    {
      name: "Process payment and close the claim",
      description: "Approved claims are batched into the payroll run and the claim is marked paid.",
      roles: "Finance Associate",
      systems: "Payroll System",
    },
  ],
};

export default function NewProcessPage() {
  const router = useRouter();

  const [name, setName] = useState("");
  const [industry, setIndustry] = useState("");
  const [description, setDescription] = useState("");
  const [activities, setActivities] = useState<ActivityDraft[]>([emptyActivity(), emptyActivity()]);

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const [knownRoles, setKnownRoles] = useState<Role[]>([]);
  const [knownSystems, setKnownSystems] = useState<SystemTool[]>([]);

  useEffect(() => {
    // Suggestions only — the form works perfectly well if these never load.
    void api.listRoles().then(setKnownRoles).catch(() => undefined);
    void api.listSystems().then(setKnownSystems).catch(() => undefined);
  }, []);

  function updateActivity(key: string, patch: Partial<ActivityDraft>) {
    setActivities((current) =>
      current.map((activity) => (activity.key === key ? { ...activity, ...patch } : activity)),
    );
  }

  function removeActivity(key: string) {
    setActivities((current) =>
      current.length <= 1 ? current : current.filter((activity) => activity.key !== key),
    );
  }

  function loadExample() {
    setName(EXAMPLE.name);
    setIndustry(EXAMPLE.industry);
    setDescription(EXAMPLE.description);
    setActivities(EXAMPLE.activities.map((activity) => ({ ...emptyActivity(), ...activity })));
    setError(null);
  }

  const filledActivities = activities.filter((activity) => activity.name.trim().length > 0);
  const canSubmit =
    name.trim().length > 0 &&
    industry.trim().length > 0 &&
    description.trim().length > 0 &&
    filledActivities.length > 0 &&
    !submitting;

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (!canSubmit) return;

    setSubmitting(true);
    setError(null);

    const payload: CreateProcessRequest = {
      name: name.trim(),
      industry: industry.trim(),
      description: description.trim(),
      activities: filledActivities.map((activity) => ({
        name: activity.name.trim(),
        description: activity.description.trim(),
        roles: splitList(activity.roles),
        systems: splitList(activity.systems),
      })),
    };

    try {
      const created = await api.createProcess(payload);
      router.push(`/processes/${created.process.id}?analyze=1`);
    } catch (caught) {
      setError(caught as ApiError);
      setSubmitting(false);
    }
  }

  const fieldErrors = error?.fieldErrors ?? {};

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <div>
        <Link href="/" className="text-sm text-brand-700 hover:underline">
          ← Back to processes
        </Link>
        <h1 className="mt-2 text-2xl font-semibold tracking-tight text-ink-900">New process</h1>
        <p className="mt-2 max-w-2xl text-sm text-ink-600">
          Describe how a process runs <strong>today</strong>. Any industry works — the pipeline has
          no knowledge of the sample data. On save you will go straight to the analysis.
        </p>
      </div>

      {error ? (
        <ErrorPanel
          title="Could not create the process"
          message={error.message}
          detail={
            Object.keys(fieldErrors).length > 0
              ? Object.entries(fieldErrors)
                  .map(([field, message]) => `${field}: ${message}`)
                  .join(" · ")
              : undefined
          }
        />
      ) : null}

      <form onSubmit={handleSubmit} className="space-y-6">
        <Card className="p-5">
          <SectionHeading
            title="The process"
            action={
              <Button type="button" variant="secondary" onClick={loadExample}>
                Fill an example
              </Button>
            }
          />
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Name" htmlFor="name" required error={fieldErrors.name}>
              <input
                id="name"
                value={name}
                onChange={(event) => setName(event.target.value)}
                maxLength={200}
                placeholder="e.g. Vendor Invoice Approval"
                className={INPUT_CLASSES}
                required
              />
            </Field>
            <Field
              label="Industry"
              htmlFor="industry"
              required
              error={fieldErrors.industry}
              hint="Used to retrieve relevant research context."
            >
              <input
                id="industry"
                value={industry}
                onChange={(event) => setIndustry(event.target.value)}
                maxLength={120}
                placeholder="e.g. Manufacturing"
                className={INPUT_CLASSES}
                required
              />
            </Field>
          </div>
          <div className="mt-4">
            <Field
              label="Description"
              htmlFor="description"
              required
              error={fieldErrors.description}
              hint="Two or three sentences on what the process achieves and who is involved. The more concrete this is, the more specific the analysis."
            >
              <textarea
                id="description"
                value={description}
                onChange={(event) => setDescription(event.target.value)}
                maxLength={4000}
                rows={3}
                placeholder="How a finance team receives, checks and approves supplier invoices before payment."
                className={INPUT_CLASSES}
                required
              />
            </Field>
          </div>
        </Card>

        <Card className="p-5">
          <SectionHeading
            title="Current activities"
            description="The steps as they happen today, in order. Roles and systems are optional but make the analysis sharper."
          />

          <ol className="space-y-4">
            {activities.map((activity, index) => (
              <li key={activity.key} className="rounded-lg border border-ink-200 bg-ink-50/60 p-4">
                <div className="mb-3 flex items-center justify-between">
                  <span className="text-xs font-semibold tracking-wide text-ink-500 uppercase">
                    Step {index + 1}
                  </span>
                  <button
                    type="button"
                    onClick={() => removeActivity(activity.key)}
                    disabled={activities.length <= 1}
                    className="text-xs font-medium text-ink-500 hover:text-rose-700 disabled:cursor-not-allowed disabled:opacity-40"
                  >
                    Remove
                  </button>
                </div>

                <div className="space-y-3">
                  <Field label="What happens" htmlFor={`${activity.key}-name`} required>
                    <input
                      id={`${activity.key}-name`}
                      value={activity.name}
                      onChange={(event) => updateActivity(activity.key, { name: event.target.value })}
                      maxLength={200}
                      placeholder="e.g. Match the invoice to the purchase order"
                      className={INPUT_CLASSES}
                    />
                  </Field>
                  <Field label="Detail" htmlFor={`${activity.key}-description`}>
                    <textarea
                      id={`${activity.key}-description`}
                      value={activity.description}
                      onChange={(event) =>
                        updateActivity(activity.key, { description: event.target.value })
                      }
                      maxLength={2000}
                      rows={2}
                      placeholder="How it is done today, and what makes it slow or error-prone."
                      className={INPUT_CLASSES}
                    />
                  </Field>
                  <div className="grid gap-3 sm:grid-cols-2">
                    <Field
                      label="Roles"
                      htmlFor={`${activity.key}-roles`}
                      hint="Comma separated"
                    >
                      <input
                        id={`${activity.key}-roles`}
                        value={activity.roles}
                        onChange={(event) => updateActivity(activity.key, { roles: event.target.value })}
                        list="known-roles"
                        placeholder="Accounts Payable Clerk"
                        className={INPUT_CLASSES}
                      />
                    </Field>
                    <Field
                      label="Systems"
                      htmlFor={`${activity.key}-systems`}
                      hint="Comma separated"
                    >
                      <input
                        id={`${activity.key}-systems`}
                        value={activity.systems}
                        onChange={(event) =>
                          updateActivity(activity.key, { systems: event.target.value })
                        }
                        list="known-systems"
                        placeholder="ERP, Email"
                        className={INPUT_CLASSES}
                      />
                    </Field>
                  </div>
                </div>
              </li>
            ))}
          </ol>

          <datalist id="known-roles">
            {knownRoles.map((role) => (
              <option key={role.id} value={role.name} />
            ))}
          </datalist>
          <datalist id="known-systems">
            {knownSystems.map((system) => (
              <option key={system.id} value={system.name} />
            ))}
          </datalist>

          <Button
            type="button"
            variant="secondary"
            className="mt-4"
            onClick={() => setActivities((current) => [...current, emptyActivity()])}
            disabled={activities.length >= 30}
          >
            + Add a step
          </Button>
        </Card>

        <div className="flex flex-wrap items-center justify-end gap-3">
          <p className="mr-auto text-xs text-ink-500">
            {filledActivities.length} step{filledActivities.length === 1 ? "" : "s"} will be saved.
          </p>
          <Link href="/" className="text-sm font-medium text-ink-600 hover:text-ink-900">
            Cancel
          </Link>
          <Button type="submit" disabled={!canSubmit}>
            {submitting ? <Spinner /> : null}
            {submitting ? "Creating…" : "Create and analyse"}
          </Button>
        </div>
      </form>
    </div>
  );
}
