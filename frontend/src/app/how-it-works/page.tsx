"use client";

import { Badge, ButtonLink, Panel, SectionHeading } from "@/components/ui";

/**
 * What the pipeline actually does, stage by stage.
 *
 * <p>Written for someone deciding whether to believe the output. So it describes the mechanism
 * rather than the benefits, names the specific things that are checked mechanically, and states the
 * limits in the same voice as the capabilities — a page that only listed strengths would undermine
 * the thing it is arguing for.
 */

const STAGES = [
  {
    id: "1",
    title: "Read the current process",
    model: "no model",
    body: "Counts the activities, roles and systems, and names the gaps: steps with no description, activities with no recorded system, a process with no reported problems. An analysis built on a three-line description is a different thing from one built on documented activities, and the run should say which it is.",
  },
  {
    id: "2",
    title: "Diagnose the problems",
    model: "GPT-OSS 120B",
    body: "Separates symptom from cause. Asked for problems alone, a model returns the list every process shares — slow, manual, error-prone. Asked separately for the root cause, it has to say something structural, and where the material does not support one it is required to say so rather than fill the field.",
  },
  {
    id: "3",
    title: "Research the domain live",
    model: "eleven connectors",
    body: "Plans searches in the domain's own vocabulary, runs them across Bing web and news, Google News, OpenAlex, Crossref, arXiv, Europe PMC, Wikipedia, Hacker News, Stack Exchange and an agentic search, then fetches the best results and reads them. Deliberately after the diagnosis: knowing the real problem produces far better queries than knowing only the process name.",
  },
  {
    id: "4",
    title: "Extract and verify claims",
    model: "GPT-OSS 20B / Gemini",
    body: "Each page becomes atomic claims, every one carrying the words from the source that support it. Every quote is then located in the stored page text by string matching. Not found means the claim is kept and marked unverified, and can no longer raise anything's grounding score. No model is asked whether it was telling the truth.",
  },
  {
    id: "5",
    title: "Propose grounded interventions",
    model: "GPT-OSS 120B",
    body: "Recommendations that cite the evidence by number. Every citation is checked against the numbers the model was actually shown; an invented one is dropped and recorded as a fabricated citation. Each proposal must also name a specific capability, the data it needs to exist, and who checks what and when.",
  },
  {
    id: "6",
    title: "Review them adversarially",
    model: "Qwen3 27B",
    body: "A second model, from a different family, marks the first one's homework — judging whether the cited evidence actually supports the specific assertion, whether the thing could be built with the data this process has, and what happens when the model is confidently wrong. A model reviewing its own output agrees with itself; a different one does not.",
  },
  {
    id: "7",
    title: "Design the future process",
    model: "GPT-OSS 120B",
    body: "A complete ordered process, not a list of improvements, with an explicit human and AI split at every step and a required answer to what happens when the AI part is wrong or unavailable. Steps that stay entirely human are included, because a redesign that omits them is not a process.",
  },
  {
    id: "8",
    title: "Quantify the impact",
    model: "Gemini / GPT-OSS",
    body: "The model supplies four inputs — volume, handling time, the share of that time genuinely removed, and the hourly cost — and the arithmetic happens in ordinary code. Asked for a saving directly, a model returns an unfalsifiable number; asked for the inputs, it produces something a reader can argue with.",
  },
  {
    id: "9",
    title: "Assess risks and obligations",
    model: "Qwen3 27B",
    body: "The register a reviewer would expect: what could go wrong for a person, who owns each control, and what the law actually requires. An obligation is only recorded where the research established one — a risk claiming a legal requirement while citing nothing has that claim stripped, because a fabricated obligation in a compliance register is worse than a missing one.",
  },
  {
    id: "10",
    title: "Sequence and score",
    model: "no model for the score",
    body: "Delivery waves with their dependencies and the enabling work they need, then a measured score for the run: coverage, grounding, corroboration, reviewer agreement, specificity and traceability, each a ratio over stored rows. It is allowed to come out low, and it frequently should.",
  },
];

const GUARANTEES = [
  {
    title: "What is actually guaranteed",
    tone: "good" as const,
    points: [
      "A claim marked verified has its exact words in the page it names, checked by string matching against text this application stored.",
      "Corroboration counts only across different publishers, so a source agreeing with itself never raises a score.",
      "Every number in a run trace is a count of stored rows, not an assertion.",
      "The whole pipeline runs identically on a process created thirty seconds ago in an industry nobody anticipated. There is no branch anywhere on which process is being analysed.",
    ],
  },
  {
    title: "What is not",
    tone: "warning" as const,
    points: [
      "That the sources are right. Sources disagree, and when they do both are shown rather than one being chosen.",
      "That every connector answered. Several will not on any given day, and the run reports itself as partial rather than pretending otherwise.",
      "That the impact figures are measured. Unless a person supplied them they are a model's estimates, labelled as such, shown with the assumptions they rest on.",
      "That the reviewing model is right either. It is a second opinion, not an oracle — which is why its objections are shown rather than applied.",
    ],
  },
];

export default function HowItWorksPage() {
  return (
    <div className="mx-auto max-w-4xl space-y-7">
      <header>
        <Badge tone="brand">Ten stages · one model call each · every prompt stored</Badge>
        <h1 className="mt-3 text-2xl font-semibold sm:text-3xl">How an analysis is produced</h1>
        <p className="mt-2 max-w-3xl text-sm leading-relaxed text-[var(--text-secondary)]">
          The brief this was built against forbids &ldquo;one giant prompt pretending to be the whole
          system&rdquo;. What follows is the structure that replaced it, and the checks that make its
          output something you can verify rather than something you have to trust. Every prompt and
          every response below is stored per stage and readable on any analysed process&rsquo;s Run
          trace tab.
        </p>
      </header>

      <ol className="space-y-2.5">
        {STAGES.map((stage) => (
          <li key={stage.id}>
            <Panel className="p-4">
              <div className="flex items-start gap-3">
                <span className="tabular mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-lg bg-[var(--surface-3)] text-xs font-semibold text-[var(--text-secondary)]">
                  {stage.id}
                </span>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <h2 className="text-sm font-semibold text-[var(--text-primary)]">{stage.title}</h2>
                    <Badge tone="neutral">{stage.model}</Badge>
                  </div>
                  <p className="mt-1.5 text-[0.8125rem] leading-relaxed text-[var(--text-secondary)]">
                    {stage.body}
                  </p>
                </div>
              </div>
            </Panel>
          </li>
        ))}
      </ol>

      <div className="grid gap-4 md:grid-cols-2">
        {GUARANTEES.map((section) => (
          <Panel key={section.title} className="p-4">
            <SectionHeading title={section.title} />
            <ul className="space-y-2">
              {section.points.map((point) => (
                <li key={point} className="flex gap-2">
                  <span
                    className="mt-1.5 size-1.5 shrink-0 rounded-full"
                    style={{
                      backgroundColor:
                        section.tone === "good" ? "var(--status-good)" : "var(--status-warning)",
                    }}
                    aria-hidden="true"
                  />
                  <span className="text-xs leading-relaxed text-[var(--text-secondary)]">{point}</span>
                </li>
              ))}
            </ul>
          </Panel>
        ))}
      </div>

      <Panel className="p-5">
        <SectionHeading
          title="Running it on a free tier"
          hint="The constraint that shaped most of the engineering."
        />
        <div className="space-y-3 text-[0.8125rem] leading-relaxed text-[var(--text-secondary)]">
          <p>
            Everything here runs on free allowances. The binding one is Groq&rsquo;s: roughly eight
            thousand tokens a minute, enforced across the whole organisation rather than per model —
            measured directly, by watching a call to one model refused with another model&rsquo;s name
            in the error. A ten-stage pipeline spends about thirty thousand tokens, so a fresh analysis
            is paced by that ceiling rather than by how fast the models think.
          </p>
          <p>
            Three things make it workable. A token governor synchronises itself from each
            provider&rsquo;s own rate-limit headers and makes stages wait rather than fail. Every
            response is cached in Postgres against a hash of the exact request, so re-running an
            unchanged analysis costs nothing and returns immediately. And the high-volume work is
            spread across providers, because a second provider is a second quota — the only thing that
            genuinely multiplies throughput when the ceiling is organisation-wide.
          </p>
          <p>
            The visible cost of all this is time: a first analysis of a new process takes several
            minutes, most of it waiting. That is why the run streams its progress rather than showing a
            spinner — the waiting is real and hiding it would be the wrong kind of polish.
          </p>
        </div>
        <div className="mt-4 flex flex-wrap gap-2">
          <ButtonLink href="/system">See the current budgets</ButtonLink>
          <ButtonLink href="/processes/new" variant="primary">
            Try it on your own process
          </ButtonLink>
        </div>
      </Panel>
    </div>
  );
}
