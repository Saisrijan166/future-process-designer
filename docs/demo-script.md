# Live demo script — 10 to 15 minutes

## Before you start

- [ ] Open the Vercel URL **2–3 minutes early** so the free Render backend wakes up. Cold start is
      about a minute. Confirm the dashboard lists six processes.
- [ ] Check the Gemini free-tier quota has headroom — you will make 2–3 calls during the demo.
- [ ] Have a second tab on `/evidence`, and a terminal with `psql "$DATABASE_URL"` connected.
- [ ] Have one seed process **already analysed** so section 3 doesn't start with a wait. Leave the
      rest un-analysed.
- [ ] Know your fallback: if the Gemini quota is exhausted mid-demo, show an already-analysed
      process plus its prompt/raw-response trace, and say plainly what happened.

---

## 1 · The problem (1 min)

> "This analyses how AI could change a business process — and it works on any process, not just the
> ones I loaded.
>
> The domain here is AssessWise, a fictional online-assessment company: exam authoring, question
> banks, proctoring, grading, certification, learner support. I picked it because I know that domain
> well enough to tell whether the output is actually sensible, rather than just plausible.
>
> The thing I want to be judged on is what happens when you give it a process I've never seen."

---

## 2 · Architecture and the free-tier stack (2 min)

Open [`docs/architecture-diagram.md`](architecture-diagram.md).

> "Four real layers. Next.js on Vercel. Spring Boot on Render. PostgreSQL on Neon. And in between,
> an intelligence layer that is a pipeline of eight steps, of which the model call is one.
>
> Everything is free tier or open source. Nothing here needs a licence to run."

On the fallback question — answer it before it's asked:

> "If Gemini's free tier went away tomorrow: the model call sits behind a single `AiProvider`
> interface with one implementation. Swapping to Groq's free tier or a local Ollama model is a new
> class and a config value — the pipeline, the schema and the UI don't change.
>
> I deliberately did not build a second live provider or automatic failover. The brief asks for that
> risk to be explained, not engineered around, and that time went into the pipeline instead."

---

## 3 · One process, end to end (3 min)

Open the pre-analysed seed process — **Result Evaluation & Grading** works well.

**Comparison strip** (top of the page):

> "Current, transition, future, side by side. Six activities today, five in the redesign. Every
> number is a count of database rows, not a summary of a paragraph."

**Current tab:**

> "The process as it runs today: each step with the roles, the systems and the pain points recorded
> against it. Descriptive grading takes six to nine days and drives most of the result delay."

**Transition tab:**

> "AI opportunities. Each one names the capability, the business benefit, and — this matters — the
> risk and the reasoning. Notice it isn't uniformly enthusiastic: high automation potential on
> allocation, lower on the actual marking, because that's a high-stakes judgement."

Point at the evidence chips.

> "And each opportunity carries the sources that informed it. Those are links."

**Future tab:**

> "The future process as ordered steps, each with an explicit human responsibility and AI
> responsibility, tagged automated, augmented or human-led. The last column says what changed and
> which opportunity justified it. This is the part the brief is strict about: it's rows in a table,
> not a paragraph of advice."

---

## 4 · Evidence is real (1 min)

Open the **Evidence** tab, then click a source link.

> "This is the research layer. Sixteen cited excerpts in Postgres — the EU AI Act, NIST's risk
> framework, UNESCO's guidance, NIST's own findings on demographic differences in face recognition.
> When a process is analysed, the retriever picks the most relevant four and injects them into the
> prompt."

Click through to the actual source.

> "Real URL, real document.
>
> I chose curated sources over a live search API on purpose. A rate-limited search API is a
> dependency that can fail in the middle of a demo, and its results aren't reproducible — nobody
> could check afterwards what the model was actually shown. This way it's auditable. The trade-off
> is that the corpus is small and static, and I've written that limitation down."

Worth adding:

> "And the model can only cite these. If it invents a title, the citation is thrown away and
> recorded as a warning — you'll see that in a moment."

---

## 5 · The surprise record — the part that matters (4–5 min)

**Ask the judges for a process. Any industry. Do not steer them.**

> "Give me any business process, from any industry. Something with four or five steps."

Click **+ New process** and type what they say, live. Keep the descriptions short and honest — the
point is that it works on rough input, not that you can write a perfect brief.

Then press **Create and analyse**, and talk while it runs (10–40 seconds):

> "While that runs — this is the same endpoint, the same service class, the same prompt template as
> the seed process I just showed you. There is no branch anywhere on which process this is. The
> retriever is scoring your process against the corpus right now, and it may well find nothing
> relevant, in which case it says so with a relevance score of zero rather than pretending."

When it lands, walk the result:

> "Problems it inferred from what I typed. Opportunities with risks. A redesigned process with the
> human/AI split. Interventions linking each future step back to the opportunity behind it."

Be honest about quality:

> "Look at whether these are actually specific to your process, or generic advice that would apply
> to anyone. That's the real test, and you should judge it on that."

---

## 6 · Prove it isn't hard-coded (1 min)

Two ways — the UI one is faster:

Press **Show prompt & raw response** in the "How this was produced" panel.

> "The exact prompt sent, and the exact text the model returned, stored against the run. Along with
> the model name, token counts, how long it took, and whether the JSON repair retry had to fire."

If there are validation warnings, show them rather than skipping past:

> "And here's where it corrected the model — items it dropped or fixed. Including any source the
> model tried to cite that I never gave it."

Then the database, for the sceptics:

```sql
SELECT fa.sequence_order, fa.name, fa.responsibility_type, fa.ai_responsibility
FROM future_activity fa
JOIN process p ON p.id = fa.process_id
WHERE p.name = '<the process the judge just named>'
ORDER BY fa.sequence_order;
```

> "Rows. Queryable, joinable, comparable. Not a blob of text with an AI-generated flag on it."

If you have a spare 20 seconds, press **Re-run analysis**:

> "And it's idempotent — re-running replaces the future state in one transaction. No duplicates, no
> orphans."

---

## 7 · Close (1 min)

> "What I'd add next: authentication and multi-tenant organisations; embeddings instead of keyword
> matching once the corpus grows past a few dozen sources; versioned prompts so two runs can be
> compared side by side; and a review step so a human accepts or rejects each opportunity before it
> becomes the stored future state.
>
> What I'd want you to take away is the surprise-record test. The seed data is there to make the
> demo concrete. The pipeline doesn't know it exists."

---

## Questions you should expect

**"How do I know the model didn't just memorise this?"**
Show the trace. The prompt contains the activities they just dictated; the response references them
by name. And offer to run it again on a process they invent on the spot.

**"What happens when the model returns garbage?"**
One repair retry with the specific complaint fed back. If that also fails, a `422` naming what was
wrong, the process left untouched, and a `FAILED` run recorded with the reason. There's a test for
each of those.

**"Isn't the keyword retrieval a bit basic?"**
Yes, deliberately. At sixteen snippets, keyword matching with TF-IDF weighting is as accurate as
embeddings, runs in microseconds, needs no vector database, and can be explained in one sentence —
and the scores are stored so you can check them. It stops being the right choice at a few hundred
snippets, and at that point it's one class to replace.

**"What does this cost to run?"**
Nothing. Free tiers throughout. The cost of a single analysis on Gemini Flash pricing would be a
fraction of a rupee if it were paid.

**"Could this handle our real processes?"**
As it stands: one tenant, no auth, no access control — so not with real customer data. That's the
first thing Phase 2 addresses. The pipeline and data model themselves are industry-agnostic, which
is what the live test just demonstrated.

**"Why Gemini?"**
A genuinely usable free tier, and native structured-output support so the response conforms to a
schema server-side. The validation and repair logic still run regardless — a provider that quietly
ignored the schema must not be able to corrupt the database.
