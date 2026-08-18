# Documentation

All technical documentation for the **AI Future Process Designer** lives in this folder. Start
wherever matches what you need.

| Document | What it covers |
|---|---|
| **[Architecture](architecture-diagram.md)** | The four layers, the eight-step analysis pipeline as a sequence diagram, the deployment topology, and why each boundary is where it is |
| **[Data model](data-model.md)** | The ER diagram, every table and column, the current/transition/future split, and the SQL that walks a future step back to the evidence that produced it |
| **[Technical documentation](technical-documentation.md)** | The full reference — module map, API reference, pipeline internals, security model, every configuration variable, testing, operations and troubleshooting |
| [Research sources](sources.md) | The 16 curated excerpts that ground every analysis, each with a verified URL, and the corpus's honest limitations |
| [Demo script](demo-script.md) | A 10–15 minute walkthrough of the whole system, including the surprise-record test |
| [AI tools disclosure](ai-tools-disclosure.md) | How AI assistance was used to build this, what it generated, and what it got wrong |

## Diagrams as images

Every diagram is exported to full-resolution PNG in [`diagrams/`](diagrams/), with its Mermaid
source checked in beside it so the image is regenerated from the same text that renders in the
Markdown rather than redrawn by hand.

| Diagram | Image | Source |
|---|---|---|
| System architecture | [PNG](diagrams/system-architecture.png) | [`.mmd`](diagrams/system-architecture.mmd) |
| Analysis pipeline (sequence) | [PNG](diagrams/analysis-pipeline-sequence.png) | [`.mmd`](diagrams/analysis-pipeline-sequence.mmd) |
| Deployment topology | [PNG](diagrams/deployment-topology.png) | [`.mmd`](diagrams/deployment-topology.mmd) |
| Entity relationship diagram | [PNG](diagrams/entity-relationship-diagram.png) | [`.mmd`](diagrams/entity-relationship-diagram.mmd) |

To regenerate them after editing a diagram:

```bash
npx -y @mermaid-js/mermaid-cli -i docs/diagrams/<name>.mmd -o docs/diagrams/<name>.png -b white -s 3
```

## Outside this folder

| Document | What it covers |
|---|---|
| [README.md](../README.md) | Project overview, local setup, quick start, submission index |
| [DEPLOYMENT.md](../DEPLOYMENT.md) | Step-by-step deployment to Neon, Render and Vercel with the exact variables |
| [LIBRARIES.md](../LIBRARIES.md) | Every dependency, its licence, and why it is there |
| [data-seed.sql](../data-seed.sql) | What the sample dataset contains and how to load it by hand |
