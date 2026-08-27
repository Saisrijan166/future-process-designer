"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import {
  Button,
  ErrorPanel,
  FormField,
  INPUT_CLASSES,
  Modal,
  PAGE_READING,
  Panel,
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
  const [pickerOpen, setPickerOpen] = useState(false);

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
    <div className={`${PAGE_READING} space-y-6`}>
      <div>
        <Link href="/" className="inline-flex items-center gap-1 text-sm text-[var(--text-link)] hover:underline">
          <span aria-hidden="true">←</span> All processes
        </Link>
        <h1 className="mt-2 text-2xl font-semibold tracking-tight text-[var(--text-primary)]">
          Describe a process as it runs today
        </h1>
        <p className="mt-2 max-w-2xl text-sm leading-relaxed text-[var(--text-secondary)]">
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

      <ExampleBar
        loadedExample={loadedExample}
        onBrowse={() => setPickerOpen(true)}
        onClear={clearForm}
      />

      <ExampleDialog
        open={pickerOpen}
        loadedExample={loadedExample}
        onClose={() => setPickerOpen(false)}
        onPick={(example) => {
          setPickerOpen(false);
          loadExample(example);
        }}
      />

      <form onSubmit={handleSubmit} className="space-y-6">
        <Panel className="p-5">
          <SectionHeading title="What is the process?" />
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField label="Name" htmlFor="name" required error={fieldErrors.name}>
              <input
                id="name"
                value={name}
                onChange={(event) => setName(event.target.value)}
                maxLength={200}
                placeholder="Vendor Invoice Approval"
                className={INPUT_CLASSES}
                required
              />
            </FormField>
            <FormField
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
            </FormField>
          </div>
          <div className="mt-4">
            <FormField
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
            </FormField>
          </div>
        </Panel>

        <Panel className="p-5">
          <SectionHeading
            title="The steps, in order"
            hint="What actually happens today — including the manual, tedious parts. Roles and systems are optional, but naming them makes the analysis noticeably sharper."
          />

          <ol className="space-y-4">
            {activities.map((activity, index) => (
              <li key={activity.key} className="rise-in rounded-xl border border-[var(--border-subtle)] bg-[var(--surface-1)] p-4">
                <div className="mb-3 flex items-center justify-between">
                  <span className="inline-flex items-center gap-2 text-xs font-semibold tracking-wide text-[var(--text-muted)] uppercase">
                    <span className="grid size-5 place-items-center rounded-full bg-[var(--surface-inset)] text-[11px] text-[var(--text-secondary)]">
                      {index + 1}
                    </span>
                    Step {index + 1}
                  </span>
                  <button
                    type="button"
                    onClick={() => removeActivity(activity.key)}
                    disabled={activities.length <= 1}
                    className="text-xs font-medium text-[var(--text-muted)] hover:text-rose-700 disabled:cursor-not-allowed disabled:opacity-40"
                  >
                    Remove
                  </button>
                </div>

                <div className="space-y-3">
                  <FormField label="What happens" htmlFor={`${activity.key}-name`} required>
                    <input
                      id={`${activity.key}-name`}
                      value={activity.name}
                      onChange={(event) => updateActivity(activity.key, { name: event.target.value })}
                      maxLength={200}
                      placeholder="Match the invoice to the purchase order"
                      className={INPUT_CLASSES}
                    />
                  </FormField>
                  <FormField label="Detail" htmlFor={`${activity.key}-description`}>
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
                  </FormField>
                  <div className="grid gap-3 sm:grid-cols-2">
                    <FormField
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
                    </FormField>
                    <FormField
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
                    </FormField>
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
        </Panel>

        <div className="flex flex-wrap items-center justify-end gap-3">
          <p className="mr-auto text-xs text-[var(--text-muted)]">
            {filledActivities.length} step{filledActivities.length === 1 ? "" : "s"} will be saved,
            then analysed immediately.
          </p>
          <Link href="/" className="text-sm font-medium text-[var(--text-secondary)] hover:text-[var(--text-primary)]">
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
 * A single compact row, rather than the six cards it replaces.
 *
 * Examples are useful for someone meeting the form cold and pure noise for someone who already
 * knows what they want to type — so they live behind a button and give back the vertical space.
 */
function ExampleBar({
  loadedExample,
  onBrowse,
  onClear,
}: {
  loadedExample: string | null;
  onBrowse: () => void;
  onClear: () => void;
}) {
  const loaded = PROCESS_EXAMPLES.find((example) => example.id === loadedExample);

  return (
    <Panel className="flex flex-wrap items-center justify-between gap-x-4 gap-y-3 px-4 py-3">
      {loaded ? (
        <p className="flex flex-wrap items-center gap-2 text-sm text-[var(--text-secondary)]">
          <span className="inline-flex items-center gap-1.5 rounded-full bg-[var(--surface-inverse)] px-2.5 py-1 text-xs font-medium text-[var(--text-inverse)]">
            <svg className="size-3.5" viewBox="0 0 16 16" fill="none" aria-hidden="true">
              <path
                d="M3.5 8.5l3 3 6-7"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
            {loaded.name}
          </span>
          loaded into the form — edit anything before submitting.
        </p>
      ) : (
        <p className="text-sm text-[var(--text-secondary)]">
          Not sure what to write?{" "}
          <span className="text-[var(--text-muted)]">
            Load one of {PROCESS_EXAMPLES.length} example processes from different industries.
          </span>
        </p>
      )}

      <div className="ml-auto flex items-center gap-2">
        {loaded ? (
          <Button type="button" variant="ghost" onClick={onClear}>
            Clear the form
          </Button>
        ) : null}
        <Button type="button" variant="secondary" onClick={onBrowse}>
          {loaded ? "Choose another" : "Browse examples"}
        </Button>
      </div>
    </Panel>
  );
}

/**
 * The example list, in a dialog.
 *
 * The spread across industries is the point: "Student admissions screening" matches the research
 * library and cites sources, while "Warehouse order picking" matches nothing and says so. Both
 * outcomes are worth demonstrating, so the set stays deliberately varied.
 */
function ExampleDialog({
  open,
  loadedExample,
  onClose,
  onPick,
}: {
  open: boolean;
  loadedExample: string | null;
  onClose: () => void;
  onPick: (example: ProcessExample) => void;
}) {
  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Start from an example"
      width="52rem"
    >
      <p className="mb-3 text-xs leading-relaxed text-[var(--text-secondary)]">
        Each one fills the form with a real-looking process from a different industry. They are
        inputs, not saved answers — the analysis still runs from scratch, researches that domain
        live, and you can edit anything before submitting.
      </p>
      <ul className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
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
                    ? "border-[var(--surface-inverse)] bg-[var(--surface-inverse)] text-[var(--text-inverse)]"
                    : "border-[var(--border-subtle)] bg-[var(--surface-2)] hover:border-[var(--border-strong)] hover:bg-[var(--surface-1)]"
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
                <span className={`mt-0.5 text-xs ${active ? "text-[var(--text-inverse-muted)]" : "text-[var(--text-muted)]"}`}>
                  {example.industry}
                </span>
                <span
                  className={`mt-1.5 text-xs leading-relaxed ${active ? "text-[var(--text-inverse-muted)]" : "text-[var(--text-secondary)]"}`}
                >
                  {example.teaser}
                </span>
                <span
                  className={`mt-2 text-[11px] font-medium ${active ? "text-[var(--text-inverse-muted)]" : "text-[var(--text-muted)]"}`}
                >
                  {example.activities.length} steps
                </span>
              </button>
            </li>
          );
        })}
      </ul>
    </Modal>
  );
}
