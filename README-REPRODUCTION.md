# BLCoiR: reproduced implementation

This is a clean re-implementation of **BLCoiR** (Shao & Yu, *Information Retrieval-Based Fault
Localization for Concurrent Programs*, ASE 2023, `ase23-main-262.pdf`). It was rebuilt from the paper
because the original implementation was lost. The `src/java` tree in this repository holds only
experimental code: `uc.eecs.BLCoiR` is an empty class, the old pom did not compile it, and its paths
point to `/Users/shuai/Java-projects/...`.

The new code is in `src/main/java/uc/eecs/blcoir`. The old `src/java` is left untouched and is not
compiled. The original pom is kept as `pom.legacy.xml`.

## Build and run

Requirements: JDK 11+ and Maven. The first build downloads about 500 MB of CoreNLP models.

```bash
mvn package
```

Generate BLCoiR queries for all 692 reports and evaluate them (about 10–20 min, mostly CoreNLP):

```bash
./blcoir.sh run
```

Other commands:

```bash
./blcoir.sh query   [--project P] [--out Query-Generated] [--length 32] [--no-coref]
./blcoir.sh eval    --queries Query --file BLCoiR.txt        # evaluate any stored query file
./blcoir.sh eval    --baseline brtext                         # whole report text as the query
./blcoir.sh explain --project GitHub/vertx --bug 1247          # show KG, node types, PageRank scores
```

`P` can be a project (`GitHub/vertx`), a whole dataset (`GitHub`) or a project name (`vertx`).
Lists are comma-separated. Each evaluation writes `results/<name>/summary.md` (tables by quality,
dataset and project) and `ranks.tsv` (per-report ranks and the query used).

`eval --queries Query --file X.txt` works with every stored query file under `Query/`:
`BLCoiR.txt` (your original queries), `blizzard.txt`, `baseline_entity.txt`,
`baseline_graph.txt`, `gpt-3.5.txt`, and the rest.

## How the code maps to the paper

| Paper | Code |
|---|---|
| §III-A corpus and index | Uses the indexes in `Lucene/Lucene_Index` (written by Lucene 9.3.0) and `Lucene-Index2File-Mapping` |
| §III-B key entity extraction, dependency parsing, coreference (Alg. 1 l.4–9) | `nlp/BugReportParser`: CoreNLP tokenize, ssplit, pos, lemma, depparse, statistical coref |
| Table III, the 9 concept types | `kg/ConceptType`, `kg/ConceptLexicon` |
| Removing non-concept nodes and redirecting their edges (Alg. 1 l.12–21) | `KnowledgeGraphBuilder.prunedDependencyEdges` |
| Table IV, concept relations CR1–CR9 (Alg. 1 l.22–24) | `KnowledgeGraphBuilder.build` |
| §III-D, PageRank on the undirected graph with d = 0.85, top L = 32 terms (Alg. 1 l.25) | `rank/PageRank`, `query/QueryFormulator` |
| §III-E, Lucene retrieval (Alg. 1 l.26) | `ir/LuceneRetriever` |
| §IV-A4, Top@K, MRR and MAP | `eval/Metrics`, `eval/Evaluator` |
| PE / NL / PES / NLS splits | `src/main/resources/categories.tsv`, extracted from the old `ClassificationID.java` by `scripts/extract_categories.py` |

### Implementation decisions the paper does not pin down

- **Graph nodes** are lower-cased lemmas. Identifiers are split at `.`, so `HttpClientImpl.close`
  becomes the two nodes `httpclientimpl` and `close`. The same term in different sentences is one
  node.
- **Concept typing** uses word lists (in `ConceptLexicon`) seeded from Table III and the old code's
  `ConCorpus`. COE and EXC are recognised by identifier shape (camelCase, PascalCase, `a.b`, `foo()`,
  `*Exception`) whatever the POS tag. Adjectives that name a concurrency mechanism, such as
  `concurrent`, `atomic` and `thread-safe`, are also typed CME. This is a small extension beyond the
  nouns-and-verbs rule in the paper.
- **CR1–CR6** link concept pairs that appear in the same sentence. **CR7** and **CR8** use the `obj`
  and `nsubj` dependencies. **CR9** links every concept that takes part in a CR1, CR3 or CR5
  relation to every other such concept.
- **Coreference** only replaces pronominal mentions (for example `its` → `XsltBuilder`) with their
  representative mention.
- **MAP** divides by the number of relevant files retrieved, as BugLocator and BLIZZARD do.
- Stop words (`src/main/resources/stopwords.txt`) are removed only when the top-L terms are picked.
  They are not removed from the graph.
- **Package segments** of qualified names are dropped, so `org.redisson.RedissonTopic.removeAllListeners`
  becomes `redissontopic` and `removealllisteners`. Package names occur in nearly every file of a
  project. Set `-Dblcoir.dropPackages=false` to keep them.
- **Query selection refinements** (default, switch off with `--paper-selection`): Other Verbs (OTV)
  stay in the graph but are not used as query terms, and COE/EXC terms get ×2 PageRank weight when the
  top L are picked. See the tuning section below.

## Validation

Running the new evaluator on your original stored queries (`Query/**/BLCoiR.txt`) reproduces the
paper. Compare with Table VII:

| | Top1 | Top5 | Top10 | MRR | MAP |
|---|---|---|---|---|---|
| Paper, Dataset_PE | 57.27% | 79.09% | 83.82% | 0.6610 | 0.4997 |
| Original queries, this evaluator, PE (549) | 57.92% | 80.15% | 85.06% | 0.6736 | 0.5163 |
| Paper, Dataset_NL | 31.69% | 47.89% | 54.23% | 0.3923 | 0.2597 |
| Original queries, this evaluator, NL (142) | 32.39% | 50.00% | 57.04% | 0.4108 | 0.2778 |
| Paper, BR baseline, PE | 32.55% | 50.00% | 58.36% | 0.4001 | 0.2854 |
| BRText baseline, this evaluator, PE | 32.60% | 50.46% | 58.65% | 0.4139 | 0.2962 |

Queries regenerated from scratch by this re-implementation (`./blcoir.sh run`, which writes
`Query-Generated/` and `results/BLCoiR-generated/`):

| | Top1 | Top5 | Top10 | MRR | MAP |
|---|---|---|---|---|---|
| Re-implementation, PE (549) | 46.27% | 70.13% | 76.32% | 0.5693 | 0.4382 |
| Re-implementation, NL (142) | 23.24% | 42.25% | 50.70% | 0.3271 | 0.2247 |
| *v1: plain top-L PageRank, packages kept, PE* | 41.89% | 64.66% | 73.22% | 0.5293 | 0.3988 |
| *v1: NL* | 23.24% | 41.55% | 48.59% | 0.3195 | 0.2262 |

The v1 rows are kept in `results/BLCoiR-generated-v1-paper-selection/`.

### Closing the gap to the original queries

To avoid overfitting, the reports were split by bug id: even ids were used to tune and odd ids were
held out. `scripts/select_experiment.py` rebuilds queries from the dumped graph nodes
(`Query-Generated/**/BLCoiR.nodes.tsv`) under different selection rules and scores them on the tune
split.

All-report MRR on each split:

| | tune (347) | held-out (346) |
|---|---:|---:|
| v1 (plain top-L PageRank) | 0.4753 | 0.5000 |
| current default | 0.5173 | 0.5231 |
| your original queries | 0.5869 | 0.6517 |

What was learned:
- **Coverage is not the issue.** 95% of the original query terms are nodes in this graph.
- **What helped:** dropping package segments, not using Other Verbs as query terms, and boosting code
  entities.
- **What did not help:**
  - the title boost
  - surface word forms (the Lucene index is unstemmed)
  - the full report instead of BRText
  - relation-only nodes
  - other query lengths (L = 20–40 all score within ±0.01)
- **Your original stop list.** The original pipeline filtered queries with the hand-tuned list in
  `src/java/uc/eecs/nlp/StopWords.java`. It contains `thread`, `wait`, `synchronize`, `method`,
  `org`, `java` and project names like `tomcat`, `eclipse` and `jdt`. Applying it here scores about
  the same as the generic default (tune MRR 0.520 vs 0.517), so it is not used.
- **The remaining gap** (about 0.13 MRR on the held-out split) is not about selection weights:
  average query length is about 18 terms, below L = 32, so most queries already contain every
  candidate node. It comes from which words become nodes in the first place, that is, the concept
  lexicon and parsing details of the lost code. Those can't be recovered from its output alone.

Known gaps:
- BLCoiR+ (§IV-B3) is not re-implemented. It adds BLUiR's structured Indri retrieval and the
  BRTracer/BLIA stack-trace boost.
- Two reports (Bench4BL HIVE-9199 and GitHub grpc-1549) are missing from the old classification
  lists, so they appear as `?`. That gives 693 reports here against 692 in the paper.
- The paper's own Table VII "All" row has inconsistent counts (for example 360 = 44.48% implies
  about 809 reports), so compare the PE and NL rows.
