import Link from "next/link";
import { Card } from "@/components/ui";

export const metadata = {
  title: "How it works — AI Future Process Designer",
  description: "A plain-language explanation of what this tool does and how it produces its answers.",
};

function Step({
  number,
  title,
  children,
}: {
  number: number;
  title: string;
  children: React.ReactNode;
}) {
  return (
    <li className="flex gap-4">
      <span className="grid size-8 shrink-0 place-items-center rounded-full bg-brand-600 text-sm font-bold text-white">
        {number}
      </span>
      <div className="pb-6">
        <h3 className="text-sm font-semibold text-ink-900">{title}</h3>
        <div className="mt-1 space-y-2 text-sm leading-relaxed text-ink-600">{children}</div>
      </div>
    </li>
  );
}

export default function HowItWorksPage() {
  return (
    <div className="mx-auto max-w-3xl space-y-10">
      <header>
        <h1 className="text-2xl font-semibold tracking-tight text-ink-900">How this works</h1>
        <p className="mt-3 text-base leading-relaxed text-ink-700">
          Every organisation has processes that were designed before AI was an option — steps that
          exist only because someone had to read something, check something or retype something.
          This tool takes one of those processes, as it runs <strong>today</strong>, and works out
          what it would look like if AI did the parts AI is actually good at.
        </p>
        <p className="mt-3 text-sm leading-relaxed text-ink-600">
          The output is not advice in a paragraph. It is a redesigned process: numbered steps, each
          saying what a person is still accountable for and what the AI does, each linked back to
          the reason it changed.
        </p>
      </header>

      <section>
        <h2 className="mb-4 text-lg font-semibold text-ink-900">What happens when you press Analyse</h2>
        <ol className="border-l border-ink-200 pl-1">
          <Step number={1} title="It reads what you wrote down">
            <p>
              The process name, the industry, and every current step with its people and systems.
              Nothing else — it has no access to your real systems, and it does not search the web.
            </p>
          </Step>
          <Step number={2} title="It finds relevant research">
            <p>
              A small library of 16 cited sources lives in the database — the EU AI Act, guidance
              from UNESCO and the US Department of Education, standards from NIST, published vendor
              research. The system scores all of them against your process and picks the four most
              relevant.
            </p>
            <p>
              If your process has nothing to do with those sources, it says so honestly with a
              relevance score of zero rather than pretending otherwise.
            </p>
          </Step>
          <Step number={3} title="It asks an AI model, with strict instructions">
            <p>
              Your process and those four sources are assembled into a prompt that demands a
              specific answer format and forbids inventing sources. The exact text sent is saved.
            </p>
          </Step>
          <Step number={4} title="It checks the answer before believing it">
            <p>
              The response is parsed and validated. If it is unusable, the system tells the model
              precisely what was wrong and asks once more. Individual broken items are dropped and
              recorded rather than being allowed to spoil the rest.
            </p>
            <p>
              If the model cites a source it was never shown, that citation is thrown away — so the
              Evidence tab can never display something that did not inform the analysis.
            </p>
          </Step>
          <Step number={5} title="It saves the result as real data">
            <p>
              Everything becomes rows in a database, linked together: this future step exists
              because of that opportunity, which addresses that current step, supported by that
              source. You can query it, count it and compare it.
            </p>
          </Step>
        </ol>
      </section>

      <section className="grid gap-4 sm:grid-cols-3">
        <Card className="p-4">
          <h3 className="text-sm font-semibold text-ink-900">Current</h3>
          <p className="mt-1 text-xs leading-relaxed text-ink-600">
            How the process runs today: the steps, who does them, which systems they use, and what
            goes wrong.
          </p>
        </Card>
        <Card className="p-4">
          <h3 className="text-sm font-semibold text-brand-700">Transition</h3>
          <p className="mt-1 text-xs leading-relaxed text-ink-600">
            Where AI could change things — with the benefit, the risk, the reasoning and the sources
            behind each suggestion.
          </p>
        </Card>
        <Card className="p-4">
          <h3 className="text-sm font-semibold text-emerald-700">Future</h3>
          <p className="mt-1 text-xs leading-relaxed text-ink-600">
            The redesigned process, step by step, splitting the work explicitly between people and
            AI.
          </p>
        </Card>
      </section>

      <section>
        <h2 className="mb-3 text-lg font-semibold text-ink-900">Questions you might reasonably ask</h2>
        <dl className="space-y-4">
          <div>
            <dt className="text-sm font-semibold text-ink-900">
              Are the answers pre-written for the sample processes?
            </dt>
            <dd className="mt-1 text-sm leading-relaxed text-ink-600">
              No. The samples ship with no future state at all — you generate theirs the same way
              you would generate one for a process you invent. Every result page has a{" "}
              <em>Show prompt &amp; raw response</em> button that reveals exactly what was sent to
              the model and exactly what came back.
            </dd>
          </div>
          <div>
            <dt className="text-sm font-semibold text-ink-900">Does it work on processes it has never seen?</dt>
            <dd className="mt-1 text-sm leading-relaxed text-ink-600">
              That is the point of it. There is no special handling for any particular process or
              industry. Try something entirely unrelated — a hospital admission, a warehouse
              dispatch, a school admissions round — and it takes the identical code path.
            </dd>
          </div>
          <div>
            <dt className="text-sm font-semibold text-ink-900">What happens when the AI service is down?</dt>
            <dd className="mt-1 text-sm leading-relaxed text-ink-600">
              There are two, tried in order. If Google Gemini is out of free quota or unreachable,
              Groq answers instead — and the result page says plainly which one produced the
              analysis and why the first was passed over.
            </dd>
          </div>
          <div>
            <dt className="text-sm font-semibold text-ink-900">Should I trust what it produces?</dt>
            <dd className="mt-1 text-sm leading-relaxed text-ink-600">
              Treat it as a well-informed first draft, not a decision. It reasons only from what you
              typed in, it can be confidently wrong, and every suggestion is worth checking against
              what you know about the real process. The risk field on each opportunity is there
              because AI in a real process is not free of consequences.
            </dd>
          </div>
        </dl>
      </section>

      <section className="rounded-xl border border-brand-200 bg-brand-50 p-5">
        <h2 className="text-sm font-semibold text-brand-900">Try it</h2>
        <p className="mt-1 text-sm leading-relaxed text-brand-900/80">
          The fastest way to understand it is to run it on something you know well. Describe a
          process from your own work in four or five steps and see whether the result is genuinely
          specific to it.
        </p>
        <div className="mt-3 flex flex-wrap gap-3">
          <Link
            href="/processes/new"
            className="rounded-lg bg-brand-600 px-3.5 py-2 text-sm font-medium text-white hover:bg-brand-700"
          >
            Create a process
          </Link>
          <Link
            href="/"
            className="rounded-lg border border-brand-300 bg-white px-3.5 py-2 text-sm font-medium text-brand-700 hover:bg-brand-100"
          >
            Browse the samples
          </Link>
        </div>
      </section>
    </div>
  );
}
