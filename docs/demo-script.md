# Live demo script — 12 to 15 minutes

The shape of this demo: **it researches a domain it has never seen, live, in front of the room, and
it can prove every quote it uses.** Everything else is supporting material for that.

One thing to internalise before you start: **a fresh analysis takes about four minutes.** That is
arithmetic, not slowness — roughly 30,000 tokens against a free-tier ceiling of 8,000 per minute.
The demo is built around that wait rather than apologising for it, because the live console is the
single most convincing screen in the product. You will be talking over it, not watching it.

## Before you start

- [ ] Sign in early — `demo@assesswise.test` / `demo12345`, or your own account.
- [ ] Open the Vercel URL **3 minutes early** so the free Render instance wakes up. Cold start is
      about a minute. Confirm the dashboard lists six processes.
- [ ] Open **Engine** and check the budget: the daily request counts should have headroom, and no
      provider should be showing as unconfigured. This is your go/no-go check.
- [ ] Have **two** seed processes already analysed — one to walk through, one in reserve.
- [ ] Second tab on **Evidence**. Terminal with `psql "$DATABASE_URL"` connected.
- [ ] Know your fallback: if the quota is gone or the network is hostile, show an already-analysed
      process, its Evidence tab and its Run trace, and say plainly what happened. The stored
      evidence is just as checkable after the fact as it is live — that is the whole point of storing
      it.

---

## 1 · What this is (1 min)

> "This takes a business process and designs what it looks like when AI is doing part of the work.
>
> Two things make it more than a prompt with a form in front of it. It researches the domain live —
> across eleven public sources, none of which needs an API key — before it recommends anything. And
> every quote it cites has been located in the page it came from by string matching, not taken on the
> model's word.
>
> The domain loaded here is AssessWise, a fictional online-assessment company. I picked it because I
> know it well enough to tell whether the output is sensible rather than merely plausible. But what I
> want to be judged on is what happens when you give it a process I have never seen — and we will do
> that."

---

## 2 · The pipeline, not the prompt (2 min)

Open **How it works** in the app, or [`docs/architecture-diagram.md`](architecture-diagram.md).

> "Ten stages. Two of them use no model at all — the first one reads the process and names the gaps
> in it, the last one scores the run. The eight in between each have one prompt small enough to read,
> a stored response, and a model chosen for that particular job.
>
> Two of those stages are worth calling out now.
>
> The critique stage runs on a **different model family** from the one that produced the
> recommendations. A model reviewing its own work agrees with itself; a Qwen model reviewing gpt-oss
> is capable of disagreeing, and it does.
>
> And the quantification stage is not allowed to do arithmetic. It supplies four inputs — volume,
> minutes per item, the share AI could take, the cost of an hour — because those need judgement.
> Java multiplies them. Every rupee figure you will see can be recomputed from its stored inputs,
> which is not true of a number a language model wrote out."

On the free-tier question, answer it before it is asked:

> "Groq is primary, Gemini second, and any OpenAI-compatible host — including a local Ollama — is one
> config value away, because every model call goes through one interface.
>
> That is not architecture for its own sake. Gemini's free tier ran out at twenty requests a day
> mid-build, and Groq decommissioned the model this project had pinned. Both were survivable because
> the router falls back through a chain."

---

## 3 · One process, end to end (3 min)

Open the pre-analysed process — **Result Evaluation & Grading** works well. Move briskly; this is
context for section 5, not the main event.

**Overview:** the scorecard and the comparison strip.

> "Six measured components, and note that it is allowed to score badly — this run is a B. A quality
> measure that always reports success is measuring nothing. Grounding is the ratio of
> recommendations backed by a verified quote; corroboration is how often two independent domains say
> the same thing, and it is frequently low, because with six documents it genuinely is."

**Today** → **Diagnosis:**

> "The process as it runs today, then the problems with their root causes. Descriptive grading takes
> six to nine days and drives most of the result delay."

**Recommendations:**

> "Each one names the capability, the benefit, the risk, and the reasoning — and carries numbered
> citations. Those numbers resolve to specific quotes. Notice it is not uniformly enthusiastic: high
> automation potential on allocation, much lower on the marking itself, because that is a high-stakes
> judgement.
>
> The review line under each one is the critique stage's verdict, from the other model."

**Future process** → **Impact** → **Roadmap:**

> "The future state as ordered steps, each with an explicit human responsibility, an AI
> responsibility, and a failure mode — what happens when the AI part is wrong, which is the question
> that actually decides whether any of this is deployable.
>
> Impact: hours and rupees a month, with the inputs shown beside the outputs. Roadmap: waves, with
> what each one depends on."

---

## 4 · Evidence — the part I would push on if I were you (2 min)

Open the **Evidence** tab.

> "This is every source the run found, and every claim taken from it.
>
> Each claim carries the quote. And each quote has been **located in the stored page text** — we
> keep the text we read, so the check is `indexOf`, not the model's assurance. Verified means found.
> Unverified means we could not find it, and those claims are kept, labelled, and excluded from every
> score. They are not silently deleted, because you should be able to see when it happened.
>
> That check is the cheapest and least clever component in the codebase. It is also the one
> everything else rests on, because a model asked for a verbatim quote will occasionally hand you a
> convincing paraphrase — and a paraphrase presented as a quotation is the worst thing this system
> could emit."

Click a source, then click through to the real page. Find the quote on it.

> "Real URL, real document, real sentence.
>
> The credibility score has its arithmetic stored — source type, recency, whether the page was
> actually readable — so you can argue with it rather than just accept it."

Worth adding:

> "And the model can only cite what it was shown. A citation number it was never given is dropped
> and recorded as fabricated. That appears in the run's warnings, not in a log nobody reads."

---

## 5 · The surprise record (4–5 min) — this is the demo

**Ask the room for a process. Any industry. Do not steer them.**

> "Give me any business process, from any industry. Four or five steps."

Click **New process**, type what they say, keep it rough and honest. Then press
**Create and analyse** — and stay on the live console.

> "Watch this rather than me.
>
> Those are the searches it planned, in that domain's own vocabulary — I did not write them and
> neither did the seed data. Those are the connectors answering: Bing, Google News, Wikipedia,
> OpenAlex, Crossref, arXiv, Europe PMC, Hacker News, Stack Exchange. Those are pages being fetched
> — and you will see one or two refused, because `robots.txt` is honoured and consulting sites often
> refuse an unknown client. A refused source is kept and labelled, not hidden.
>
> And that line there is a quote being checked against the page it came from, one at a time."

You have three or four minutes of talking. Spend it on:

> "It will pause during this. The free tier gives 8,000 tokens a minute, shared across models — I
> found that out the hard way, when a call to one model was refused by name for another model's rate
> limit. So the run tells you it is waiting and how long for, instead of showing a spinner. Four
> minutes of spinner is indistinguishable from a hang.
>
> The optimisations underneath: every response is cached in Postgres, so re-running an unchanged
> process is near-instant and survives a restart. Each stage is routed to the cheapest model that
> can do it. The two highest-volume stages alternate between providers, because a second *provider*
> is the only real throughput multiplier — a second model on the same account is not.
>
> Meanwhile: same endpoint, same pipeline, same prompts as the process I showed you. There is no
> branch anywhere on which process this is."

When it lands, walk the result — and be honest about it:

> "Judge whether these recommendations are specific to your process or generic advice that would
> apply to anyone. That is the real test. And check the Evidence tab: are the sources actually about
> your industry, or did it settle for something adjacent?"

---

## 6 · Prove it is not hard-coded (1 min)

Open the **Run trace** tab.

> "Ten stages. For each one: the exact prompt sent, the exact text returned, which model answered,
> what it cost in tokens, how long it waited for budget, and whether it came from cache.
>
> A stage marked DEGRADED produced usable output but not all of it, and its notes say what was lost.
> Eight of the ten are allowed to degrade; two are required. A run that loses the roadmap is still a
> useful run."

Then the database, for the sceptics:

```sql
SELECT c.citation_index, c.quote_verified, s.domain, left(c.quote, 60)
FROM   evidence_claim c
JOIN   research_source s ON s.id = c.research_source_id
JOIN   research_run r    ON r.id = c.research_run_id
JOIN   process p         ON p.id = r.process_id
WHERE  p.name = '<the process the judge just named>'
ORDER  BY c.citation_index;
```

> "Rows. The quote, the domain it came from, and whether it verified. Queryable, joinable, and
> checkable by someone who does not trust me."

---

## 7 · Close (1 min)

> "The limitations I would raise before you do. A fresh run takes four minutes, and that floor is
> the free tier, not the code. Corroboration is often zero, because two independent domains saying
> the same thing in comparable words is genuinely rare at six documents — embeddings would help and
> cost a model call per claim. And PDFs are fetched and recognised but not read, which matters
> because much of the best sector research is published as PDF. That is the biggest single gap in
> evidence quality and it is the first thing I would fix.
>
> What I want you to take away is the surprise-record test. The seed data makes the demo concrete.
> The pipeline does not know it exists — and neither does the research layer, which is why it had to
> go and look."

---

## Questions you should expect

**"How do I know the quotes are real?"**
Show the Evidence tab, click through to the page, find the sentence. Then explain the mechanism: we
store the page text we read, and the quote is located in it by string matching. If you want the
adversarial version, `QuoteVerifierTest` includes a case named *"refuses a fabricated quote that
sounds like the source"*.

**"Why does it take four minutes?"**
About 30,000 tokens against a shared ceiling of 8,000 a minute. Lowering `RESEARCH_MAX_DOCUMENTS`
trades evidence for speed; a second provider key trades nothing. The Engine page shows the budget
live. A cached re-run is near-instant.

**"What if all eleven sources are blocked?"**
It says so, the grounding score falls, and it falls back to a curated corpus of sixteen verified
excerpts so the analysis is still grounded in something. It does not pretend to have found evidence
it did not find.

**"Isn't `robots.txt` optional?"**
Legally, largely. We honour it anyway — this is a tool that would be pointed at real organisations'
domains, and a research bot that ignores publishers' stated preferences is not one I would deploy.

**"What happens when the model returns garbage?"**
One repair retry with the specific complaint fed back. Individually broken items are dropped and
recorded as warnings rather than discarding a good run. A required stage that fails twice ends the
run with a `422` naming the stage; an optional one is recorded as DEGRADED and the pipeline carries
on. There is a test for each.

**"Why is the critique on a different model?"**
Because a model reviewing its own output agrees with itself. It is the only stage where disagreement
is the point.

**"What does this cost to run?"**
Nothing. Free tiers throughout — Groq, Google AI Studio, Neon, Render, Vercel — and eleven keyless
research connectors. The measured cost of one analysis at commercial pricing would be a few rupees.

**"Could this handle our real processes?"**
The pipeline and data model are industry-agnostic, which the live test just demonstrated. Each
account's processes are private: another signed-in user cannot list, read, analyse or delete them,
and asking by id returns 404 rather than 403 so ids cannot be probed. What is missing for real
customer data is team workspaces, password reset and a who-changed-what audit.

**"Why Groq rather than Gemini?"**
Far more requests a day on the free tier, several genuinely capable models under one key — which is
what makes per-task routing and the different-family critique possible — and an agentic model that
can search and read pages itself. Gemini stays as the second provider, and the high-volume stages
alternate into it for throughput.
