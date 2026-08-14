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
import { PROCESS_EXAMPLES, type ProcessExample } from "@/lib/examples";
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
  const [loadedExample, setLoadedExample] = useState<string | null>(null);

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

  function loadExample(example: ProcessExample) {
    setName(example.name);
    setIndustry(example.industry);
    setDescription(example.description);
    setActivities(example.activities.map((activity) => ({ ...emptyActivity(), ...activity })));
    setLoadedExample(example.id);
    setError(null);
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  function clearForm() {
    setName("");
    setIndustry("");
    setDescription("");
    setActivities([emptyActivity(), emptyActivity()]);
    setLoadedExample(null);
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
        <Link href="/" className="inline-flex items-center gap-1 text-sm text-brand-700 hover:underline">
          <span aria-hidden="true">←</span> All processes
        </Link>
        <h1 className="mt-2 text-2xl font-semibold tracking-tight text-ink-900">
          Describe a process as it runs today
        </h1>
        <p className="mt-2 max-w-2xl text-sm leading-relaxed text-ink-600">
          Any industry works — the system has no special knowledge of the samples. Write it the way
          you would explain it to a new joiner: rough is fine, specific is better. When you save, the
          analysis starts straight away.
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

      <ExamplePicker
        loadedExample={loadedExample}
        onPick={loadExample}
        onClear={clearForm}
      />

      <form onSubmit={handleSubmit} className="space-y-6">
        <Card className="p-5">
          <SectionHeading title="What is the process?" />
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Name" htmlFor="name" required error={fieldErrors.name}>
              <input
                id="name"
                value={name}
                onChange={(event) => setName(event.target.value)}
                maxLength={200}
                placeholder="Vendor Invoice Approval"
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
                placeholder="Manufacturing"
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
            title="The steps, in order"
            description="What actually happens today — including the manual, tedious parts. Roles and systems are optional, but naming them makes the analysis noticeably sharper."
          />

          <ol className="space-y-4">
            {activities.map((activity, index) => (
              <li key={activity.key} className="animate-rise rounded-xl border border-ink-200 bg-ink-50/70 p-4">
                <div className="mb-3 flex items-center justify-between">
                  <span className="inline-flex items-center gap-2 text-xs font-semibold tracking-wide text-ink-500 uppercase">
                    <span className="grid size-5 place-items-center rounded-full bg-ink-200 text-[11px] text-ink-700">
                      {index + 1}
                    </span>
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
                      placeholder="Match the invoice to the purchase order"
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
            {filledActivities.length} step{filledActivities.length === 1 ? "" : "s"} will be saved,
            then analysed immediately.
          </p>
          <Link href="/" className="text-sm font-medium text-ink-600 hover:text-ink-900">
            Cancel
          </Link>
          <Button type="submit" disabled={!canSubmit}>
            {submitting ? <Spinner /> : null}
            {submitting ? "Creating…" : "Create and analyse →"}
          </Button>
        </div>
      </form>
    </div>
  );
}

/**
 * Six starting points across unrelated industries.
 *
 * The variety is the argument: picking "Student admissions screening" and picking "Warehouse order
 * picking" both work, and the second one matches nothing in the research library — which the
 * Evidence tab then says out loud rather than papering over.
 */
function ExamplePicker({
  loadedExample,
  onPick,
  onClear,
}: {
  loadedExample: string | null;
  onPick: (example: ProcessExample) => void;
  onClear: () => void;
}) {
  return (
    <Card className="p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-base font-semibold text-ink-900">
            Not sure what to write? Start from an example
          </h2>
          <p className="mt-1 max-w-2xl text-sm leading-relaxed text-ink-600">
            Each one fills the form below with a real-looking process from a different industry.
            They are inputs, not saved answers — the analysis still runs from scratch, and you can
            edit anything before submitting.
          </p>
        </div>
        {loadedExample ? (
          <Button type="button" variant="ghost" onClick={onClear}>
            Clear the form
          </Button>
        ) : null}
      </div>

      <ul className="mt-4 grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
        {PROCESS_EXAMPLES.map((example) => {
          const active = loadedExample === example.id;
          return (
            <li key={example.id}>
              <button
                type="button"
                onClick={() => onPick(example)}
                aria-pressed={active}
                className={`flex h-full w-full flex-col rounded-xl border p-3 text-left transition-colors ${
                  active
                    ? "border-ink-900 bg-ink-900 text-white"
                    : "border-ink-200 bg-white hover:border-ink-300 hover:bg-ink-50"
                }`}
              >
                <span className="flex items-start justify-between gap-2">
                  <span className="text-sm leading-snug font-semibold">{example.name}</span>
                  {active ? (
                    <svg className="mt-0.5 size-4 shrink-0" viewBox="0 0 16 16" fill="none" aria-hidden="true">
                      <path
                        d="M3.5 8.5l3 3 6-7"
                        stroke="currentColor"
                        strokeWidth="2"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                      />
                    </svg>
                  ) : null}
                </span>
                <span className={`mt-0.5 text-xs ${active ? "text-ink-300" : "text-ink-500"}`}>
                  {example.industry}
                </span>
                <span className={`mt-1.5 text-xs leading-relaxed ${active ? "text-ink-200" : "text-ink-600"}`}>
                  {example.teaser}
                </span>
                <span
                  className={`mt-2 text-[11px] font-medium ${active ? "text-ink-300" : "text-ink-400"}`}
                >
                  {example.activities.length} steps
                </span>
              </button>
            </li>
          );
        })}
      </ul>
    </Card>
  );
}
