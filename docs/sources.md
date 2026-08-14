# Research sources

The 16 curated snippets that ground every analysis. They live in the `knowledge_snippet` table,
seeded by [`V2__seed_data.sql`](../backend/src/main/resources/db/migration/V2__seed_data.sql), and
are browsable in the running app at `/evidence`.

## How this layer works, and why it is built this way

The brief requires that outputs be traceable to underlying data, research or reasoning. The obvious
implementation is a live web-search API. This project does not do that, on purpose:

- Free search APIs are rate-limited and occasionally slow or unavailable. During a fifteen-minute
  live demo, that is a dependency that can fail in front of the judges.
- Search results are not reproducible. Two runs of the same analysis a week apart would be grounded
  in different material, and no one could check afterwards what the model was actually shown.
- The failure mode is silent: a search that returns nothing useful still produces a confident
  answer, with nothing recorded about why.

Instead, "research" is a small hand-curated corpus stored in Postgres. When a process is analysed:

1. `KnowledgeRetrievalService` scores every snippet against the process name, industry, description
   and activity text, weighting a term match in a snippet's tags above its title and its title above
   its body, and discounting terms that appear across most of the corpus.
2. The top four are injected into the prompt with their titles and URLs.
3. The model is instructed to cite supporting snippets **by exact title**, and told explicitly never
   to cite anything not shown to it.
4. `AnalysisPersistenceService` resolves each cited title against the snippets actually retrieved
   for that run. A title that does not resolve is discarded and recorded as a warning on the run —
   so the Evidence tab cannot display a source that did not inform the analysis.
5. The run record stores which snippets were retrieved, their relevance scores, and the terms that
   matched, in `analysis_run_snippet`.

The honest limitation: this corpus is small, hand-picked and static. It grounds the analysis in real
cited material rather than in nothing, but it is not a substitute for live research, and it is
weighted towards education, assessment and AI governance — so a process from an unrelated industry
will often match nothing and fall back to general AI-governance material. That fallback is visible
in the UI as a relevance score of 0.00 rather than being hidden. Replacing keyword retrieval with
embeddings over a larger corpus is the obvious next step, and it changes exactly one class.

**Snippet text is paraphrased.** Each `snippet_text` is a short summary written for this project of
what the source says, not a copied passage — both to stay clear of the sources' licensing and
because a 60-word paraphrase is more useful as prompt context than a block quote. The `source_url`
is the authority; the paraphrase is a pointer to it.

**Retrieval date.** All 16 URLs were checked and returned HTTP 200 on **14-08-2026**, which is the
`retrieved_at` value stored against each row.

## The corpus

### Law and regulation

| Title | Publisher | Source |
|---|---|---|
| EU AI Act classifies education and assessment uses as high risk | European Union | <https://eur-lex.europa.eu/eli/reg/2024/1689/oj> |
| GDPR Article 22 on automated decisions about individuals | EU General Data Protection Regulation | <https://gdpr-info.eu/art-22-gdpr/> |
| India DPDP Act framework for personal data | Ministry of Electronics and Information Technology, India | <https://www.meity.gov.in/data-protection-framework> |

### Guidance

| Title | Publisher | Source |
|---|---|---|
| UNESCO guidance on generative AI in education | UNESCO | <https://www.unesco.org/en/articles/guidance-generative-ai-education-and-research> |
| OECD AI Principles on transparency and contestability | OECD | <https://oecd.ai/en/ai-principles> |
| US Department of Education on keeping humans in the loop | US Department of Education, Office of Educational Technology | <https://www.ed.gov/sites/ed/files/documents/ai-report/ai-report.pdf> |

### Standards

| Title | Publisher | Source |
|---|---|---|
| NIST AI Risk Management Framework | National Institute of Standards and Technology | <https://nvlpubs.nist.gov/nistpubs/ai/NIST.AI.100-1.pdf> |
| 1EdTech QTI standard for portable assessment content | 1EdTech Consortium | <https://www.1edtech.org/standards/qti> |
| WCAG 2.2 accessibility requirements for digital content | World Wide Web Consortium | <https://www.w3.org/TR/WCAG22/> |

### Research

| Title | Publisher | Source |
|---|---|---|
| NIST evaluation of demographic differences in face recognition | National Institute of Standards and Technology | <https://pages.nist.gov/frvt/html/frvt11.html> |
| Stanford AI Index on adoption and cost trends | Stanford Institute for Human-Centered AI | <https://aiindex.stanford.edu/report/> |
| Survey of large language models applied to education | arXiv — *Large Language Models for Education: A Survey and Outlook* | <https://arxiv.org/abs/2403.18105> |

### Vendor material

Included because vendors publish the most concrete operational detail about assessment AI in
production — and because their stated limitations are useful, citable evidence about risk.

| Title | Publisher | Source |
|---|---|---|
| ETS research programme on automated scoring | Educational Testing Service | <https://www.ets.org/research.html> |
| Duolingo English Test research on computer-adaptive testing | Duolingo English Test | <https://englishtest.duolingo.com/research> |
| Turnitin on the limits of AI writing detection | Turnitin | <https://www.turnitin.com/solutions/ai-writing> |

### General web

| Title | Publisher | Source |
|---|---|---|
| Automated essay scoring: background and criticisms | Wikipedia | <https://en.wikipedia.org/wiki/Automated_essay_scoring> |

## Adding to the corpus

Append rows to a new Flyway migration (`V3__more_snippets.sql`) following the shape in `V2`. No code
changes are needed — retrieval reads whatever is in the table. Fill `tags` generously: tag matches
are weighted three times a body match, so tags are the main lever on what gets retrieved.

## Data disclaimer

The six AssessWise processes and the figures in their recorded pain points (review turnaround times,
proctor-to-candidate ratios, ticket volumes) are **synthetic sample data** written to be realistic
for a mid-size online assessment operation. They are not measurements of any real organisation or
client, and nothing in this repository contains customer data.
