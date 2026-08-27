# Research sources

This project has **two** evidence layers. The primary one gathers sources live, per process, at the
moment you press Analyse. The second is a small curated corpus that grounds a run whose live
research found nothing. Both are described here, with their limitations.

---

## 1. Live research — the primary layer

### What happens when you press Analyse

1. **A model plans the searches.** Not the process's own words: the domain's. "Checking answer
   scripts" finds almost nothing; searched as "automated essay scoring inter-rater reliability" it
   finds three decades of measurement. Each query carries an *intent* —
   `DOMAIN_BASELINE`, `PAIN_POINT`, `AI_CAPABILITY`, `REGULATION`, `BENCHMARK`,
   `VENDOR_LANDSCAPE`, `RISK`, `CASE_STUDY` — which is stored, so a run that never asked what the
   law requires can be seen to have a gap. A deterministic template plan runs if the planning model
   is unavailable, so research still happens.
2. **Eleven connectors answer, in parallel.** Each is asked only about the intents its material
   suits: a statute search does not go to Hacker News, and a benchmark question does not go to
   Wikipedia. Every connector may return nothing without failing the run.
3. **The best results are fetched and read.** Direct request first, honouring `robots.txt`. A
   publisher that refuses server-side requests is retried through a public text reader. If that also
   fails, the source stays in the run with its search snippet, marked *blocked* — visible rather
   than disappeared, because a source that was found and could not be read is information.
4. **Each page becomes atomic claims, each carrying a verbatim quote.** Then the part that matters:
   **every quote is located in the stored page text by string matching.** Not found means the claim
   is kept, marked unverified, and excluded from every grounding score. No model is asked whether it
   was telling the truth — a model that will invent a quote will also confirm one.
5. **Sources are scored, explainably.** Source type, publication recency weighted by how fast that
   kind of source goes stale, whether the text could actually be read, publisher reputation,
   independent corroboration, contradictions. The arithmetic is stored beside the number and shown
   in the interface, because a trust score nobody can decompose is decoration.
6. **Claims are cross-checked against each other.** Agreement counts **only across different
   publishers** — two pages on one site repeating each other is one source with two URLs.
   Disagreements are recorded and both sides shown; picking a winner would mean asserting something
   neither source supports.
7. **Recommendations cite by number.** Any number the model was not shown is dropped and recorded as
   a fabricated citation. A recommendation citing nothing is kept and labelled ungrounded.

### The connectors

All eleven are free and **none requires an API key or an account**. Verified live on 27-08-2026.

| Connector | What it is for | Endpoint |
|---|---|---|
| Bing web search | The backbone: ordinary web pages, any intent | `bing.com/search?format=rss` |
| Bing News | News, from a second index | `bing.com/news/search?format=RSS` |
| Google News | What happened recently — regulatory deadlines, procurement, incidents | `news.google.com/rss/search` |
| Wikipedia | The domain's vocabulary, so the other connectors get better queries | `en.wikipedia.org/w/api.php` |
| OpenAlex | ~250M scholarly works; where measured numbers live | `api.openalex.org` |
| Crossref | The DOI registry. Overlaps OpenAlex on purpose | `api.crossref.org` |
| arXiv | Preprints, and its abstract pages are HTML so they can be quoted | `export.arxiv.org/api/query` |
| Europe PMC | Medical-education literature: rater agreement, examiner reliability | `ebi.ac.uk/europepmc/webservices/rest` |
| Hacker News | What happened when someone shipped it. Scored as practitioner evidence | `hn.algolia.com/api/v1` |
| Stack Exchange | Implementation constraints, for the feasibility judgement | `api.stackexchange.com/2.3` |
| Groq agentic search | Runs its own searches and returns the pages it read | `groq/compound` via the chat API |

**Excluded, and why it is worth saying so.** DuckDuckGo's HTML endpoint returns an anomaly page to
server-side requests; public SearX instances return a captcha; GDELT did not respond from a server at
all. They are absent rather than left in to fail silently, because a connector that always returns
nothing is indistinguishable from a topic with no coverage.

**Optional and dormant.** Tavily and Brave are wired up and switched off unless a key is supplied.
Nothing in the default configuration depends on them, and they exist so that a keyless route
breaking is a configuration change rather than a rewrite.

### What this layer does not guarantee

- **That the sources are right.** They disagree; when they do, both are shown.
- **That every connector answered.** Several will not on any given day. The run reports itself
  `PARTIAL` rather than pretending otherwise.
- **That the whole web was searched.** Five queries, six pages read in full. The rest of what was
  found is recorded, ranked, and marked as not read.
- **That a blocked publisher was read anyway.** Roughly a third of sources refuse server-side
  fetching. Those are labelled, and their claims are limited to what the search snippet supports.
- **That corroboration will be found.** With six documents, two independently saying the same thing
  is uncommon — and the scorecard reports a corroboration score of zero when it happens, rather than
  quietly weighting it away. Embedding-based clustering would find agreement that shared vocabulary
  misses; that is the honest next step.

### Reproducibility

The original objection to live search was that it is not reproducible. That is answered by keeping
the artefacts rather than by avoiding the search: every query, every source, every fetched page's
text and every quote is stored, so what the model was shown is recoverable months later even if the
page has changed. Fetched pages are cached for a week, which also means the same statute is read
once rather than once per analysis.

---

## 2. The curated corpus — the fallback

16 hand-checked excerpts in the `knowledge_snippet` table, seeded by
[`V2__seed_data.sql`](../backend/src/main/resources/db/migration/V2__seed_data.sql) and browsable at
`/evidence`. They are retrieved by keyword match — a term in a snippet's tags is worth more than one
in its title, which is worth more than one in its body, and terms appearing across most of the
corpus are discounted — and injected when live research produced nothing.

**Why it is still here.** A run with no evidence at all would be a run with nothing to reason
against. Grounding an analysis in general AI-governance material beats grounding it in nothing, and
the run records that it fell back rather than hiding it.

**Its honest limitation** is what motivated the live layer: the same sixteen excerpts grounded every
process in every industry, and they lean towards education, assessment and AI governance. A process
from an unrelated industry matched little and fell back to generic material.

**Snippet text is paraphrased.** Each `snippet_text` is a short summary written for this project of
what the source says, not a copied passage — both to stay clear of the sources' licensing and
because a 60-word paraphrase is more useful as prompt context than a block quote. The `source_url`
is the authority. **All 16 URLs returned HTTP 200 on 14-08-2026**, which is the `retrieved_at` value
stored against each row.

### The corpus

#### Law and regulation

| Title | Publisher | Source |
|---|---|---|
| EU AI Act classifies education and assessment uses as high risk | European Union | <https://eur-lex.europa.eu/eli/reg/2024/1689/oj> |
| GDPR Article 22 on automated decisions about individuals | EU General Data Protection Regulation | <https://gdpr-info.eu/art-22-gdpr/> |
| India DPDP Act framework for personal data | Ministry of Electronics and Information Technology, India | <https://www.meity.gov.in/data-protection-framework> |

#### Guidance

| Title | Publisher | Source |
|---|---|---|
| UNESCO guidance on generative AI in education | UNESCO | <https://www.unesco.org/en/articles/guidance-generative-ai-education-and-research> |
| OECD AI Principles on transparency and contestability | OECD | <https://oecd.ai/en/ai-principles> |
| US Department of Education on keeping humans in the loop | US Department of Education, Office of Educational Technology | <https://www.ed.gov/sites/ed/files/documents/ai-report/ai-report.pdf> |

#### Standards

| Title | Publisher | Source |
|---|---|---|
| NIST AI Risk Management Framework | National Institute of Standards and Technology | <https://nvlpubs.nist.gov/nistpubs/ai/NIST.AI.100-1.pdf> |
| 1EdTech QTI standard for portable assessment content | 1EdTech Consortium | <https://www.1edtech.org/standards/qti> |
| WCAG 2.2 accessibility requirements for digital content | World Wide Web Consortium | <https://www.w3.org/TR/WCAG22/> |

#### Research

| Title | Publisher | Source |
|---|---|---|
| NIST evaluation of demographic differences in face recognition | National Institute of Standards and Technology | <https://pages.nist.gov/frvt/html/frvt11.html> |
| Stanford AI Index on adoption and cost trends | Stanford Institute for Human-Centered AI | <https://aiindex.stanford.edu/report/> |
| Survey of large language models applied to education | arXiv — *Large Language Models for Education: A Survey and Outlook* | <https://arxiv.org/abs/2403.18105> |

#### Vendor material

Included because vendors publish the most concrete operational detail about assessment AI in
production — and because their stated limitations are useful, citable evidence about risk.

| Title | Publisher | Source |
|---|---|---|
| ETS research programme on automated scoring | Educational Testing Service | <https://www.ets.org/research.html> |
| Duolingo English Test research on computer-adaptive testing | Duolingo English Test | <https://englishtest.duolingo.com/research> |
| Turnitin on the limits of AI writing detection | Turnitin | <https://www.turnitin.com/solutions/ai-writing> |

#### General web

| Title | Publisher | Source |
|---|---|---|
| Automated essay scoring: background and criticisms | Wikipedia | <https://en.wikipedia.org/wiki/Automated_essay_scoring> |

### Adding to the corpus

Append rows to a new Flyway migration following the shape in `V2`. No code changes are needed —
retrieval reads whatever is in the table. Fill `tags` generously: tag matches are weighted three
times a body match, so tags are the main lever on what gets retrieved.

---

## Where to see all of this in the running application

| What | Where |
|---|---|
| Every query planned, with its intent | Process → **Evidence** tab |
| Every source found, with its credibility arithmetic | Process → **Evidence** → click a source |
| Every claim beside the quote that was checked | Process → **Evidence** → the claim list, filterable by verified state |
| Which claims a recommendation rests on | Process → **Recommendations** → the citation chips |
| The exact prompt that produced each stage | Process → **Run trace** |
| The curated fallback corpus | **Evidence** in the sidebar |
| Which connectors are answering right now | **Engine** in the sidebar |

## Data disclaimer

The six AssessWise processes and the figures in their recorded pain points (review turnaround times,
proctor-to-candidate ratios, ticket volumes) are **synthetic sample data** written to be realistic
for a mid-size online assessment operation. They are not measurements of any real organisation or
client, and nothing in this repository contains customer data.
