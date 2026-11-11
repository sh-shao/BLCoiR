# BLCoiR: Information Retrieval-based Fault Localization for Concurrent Programs

Replication package for the ASE 2023 paper
[*Information Retrieval-Based Fault Localization for Concurrent Programs*](https://doi.org/10.1109/ASE56229.2023.00122)
by Shuai Shao and Tingting Yu ([PDF](ase23-main-262.pdf)).

BLCoiR localizes the source files responsible for **concurrency bugs** from the bug report text. It
builds a knowledge graph of concurrency concepts from each report, ranks the concepts with PageRank,
and uses the top-ranked ones as a Lucene query.

```
bug report ──► CoreNLP: POS, lemma, dependency parse, coreference
           ──► knowledge graph: 9 concept types (Table III) + 9 concept relations (Table IV)
           ──► PageRank (undirected, d = 0.85) ──► top L = 32 terms = query
           ──► Lucene search over the project's source files ──► ranked file list
```
---

## 1. Requirements

| | |
|---|---|
| Java | JDK 11 or newer (tested with JDK 21) |
| Maven | 3.6+ |
| Python | 3.8+, standard library only (for the helper scripts) |
| Disk / RAM | ~1 GB for Maven dependencies (CoreNLP models are ~460 MB); 6 GB heap |
| Network | Only for the first `mvn package` (downloads dependencies from Maven Central) |

## 2. Build

```bash
mvn package
```

This compiles `src/main/java`, runs the unit tests, and copies the runtime jars to `target/lib/`.
All commands below go through the wrapper script `./blcoir.sh`. Run it from the repository root.

## 3. Quick check: reproduce the paper's main numbers (≈ 1 minute)

Evaluate the queries produced by the original BLCoiR code (`Query/**/BLCoiR.txt`) against the
Lucene indexes:

```bash
./blcoir.sh eval --queries Query --file BLCoiR.txt --out results/archived-BLCoiR
```

Expected (`results/archived-BLCoiR/summary.md`), compared with Table VII:

| Dataset_PE (549 reports) | Top1 | Top5 | Top10 | MRR@10 | MAP@10 |
|---|---|---|---|---|---|
| Paper, Table VII | 315 (57.27%) | 435 (79.09%) | 461 (83.82%) | 0.6610 | 0.4997 |
| This repository | 318 (57.92%) | 440 (80.15%) | 467 (85.06%) | 0.6717 | 0.5006 |

| Dataset_NL (142 reports) | Top1 | Top5 | Top10 | MRR@10 | MAP@10 |
|---|---|---|---|---|---|
| Paper, Table VII | 45 (31.69%) | 68 (47.89%) | 77 (54.23%) | 0.3923 | 0.2597 |
| This repository | 46 (32.39%) | 71 (50.00%) | 81 (57.04%) | 0.4037 | 0.2625 |

## 4. Reproducing each research question

### RQ1, Table VII: BLCoiR vs. the bug report baseline

```bash
./blcoir.sh eval --queries Query --file BLCoiR.txt      # BLCoiR (original queries), see section 3
./blcoir.sh eval --baseline brtext                       # baseline: the whole report text as query
```

The baseline reproduces the paper's BR row. For Dataset_PE it gives 179/277/322 (32.60%, 50.46%,
58.65%), against 179/275/321 in the paper.

### RQ1, Table VIII: IR models (rVSM, Indri, Lucene)

The rVSM and Indri ranks of the original runs are stored in `Model-Evaluation/`:

```bash
python3 -I scripts/score_rank_files.py .
```

The last block of output is Table VIII. rVSM gives Top1 42.20% and MRR 0.4964 (paper: 42.44% and
0.4964). Indri gives Top1 26.45% and MRR 0.3585 (paper: 26.11% and 0.3503). The Lucene row is the
command in section 3.

### RQ2, Table IX: BLCoiR's query vs. baseline queries

The baseline queries of the original study are stored next to the BLCoiR queries in
`Query/<dataset>/<project>/`:

| Paper's name | Query file | Command |
|---|---|---|
| BRText | `baseline_nl.txt` | `./blcoir.sh eval --queries Query --file baseline_nl.txt` |
| Entity | `baseline_entity.txt` | `./blcoir.sh eval --queries Query --file baseline_entity.txt` |
| DenGraph | `baseline_graph.txt` | `./blcoir.sh eval --queries Query --file baseline_graph.txt` |
| GPT-3.5 | `gpt-3.5.txt` | `./blcoir.sh eval --queries Query --file gpt-3.5.txt` |
| BLIZZARD query | `blizzard.txt` | `./blcoir.sh eval --queries Query --file blizzard.txt` |
| BLCoiR | `BLCoiR.txt` | section 3 |

For Dataset_NL the Top-K counts match Table IX exactly for BRText (29/51/60), Entity (28/50/63) and
DenGraph (27/55/66). The PE rows are within a few reports.

### RQ2, Figure 5: query length L

This needs the re-implementation (section 5), because the stored queries have a fixed length:

```bash
for L in 8 16 24 32 40; do
  ./blcoir.sh run --length $L --queries Query-Generated-L$L --out results/length-$L
done
```

### RQ3, Table X: comparison with state-of-the-art IRFL techniques

The rank lists of BugLocator, BLUiR, BRTracer, AmaLgam, BLIA and BLIZZARD are stored in
`IRBL-Evaluation/`:

```bash
python3 -I scripts/score_rank_files.py .
```

The first blocks of output are Table X. Top1, Top5, Top10 and MRR match the paper **exactly** for
every tool on Dataset_NL, NLS, PE and PES. MAP is within about 0.01. The BLCoiR rows come from
`./blcoir.sh eval --queries Query` (look at the rows "NL without stack trace", "NLS", "PE without
stack trace" and "PES" in `summary.md`).

BLCoiR+ (BLCoiR combined with BLUiR's structured retrieval and the BRTracer/BLIA stack-trace boost)
is not part of the re-implementation.

## 5. Running BLCoiR from scratch

Generate queries for all 692 reports with the re-implementation, then evaluate them. This takes
about 2 minutes, most of it CoreNLP:

```bash
./blcoir.sh run
```

The queries are written to `Query-Generated/<dataset>/<project>/BLCoiR.txt` and the evaluation to
`results/BLCoiR-generated/`. Each project folder also gets `BLCoiR.nodes.tsv`, which lists every
knowledge-graph node with its concept types and PageRank score.

Expected results:

| | Top1 | Top5 | Top10 | MRR@10 | MAP@10 |
|---|---|---|---|---|---|
| Re-implementation, PE (549) | 46.27% | 70.13% | 76.32% | 0.5639 | 0.4184 |
| Re-implementation, NL (142) | 23.24% | 42.25% | 50.70% | 0.3172 | 0.2014 |
| BRText baseline, PE | 32.60% | 50.46% | 58.65% | 0.4036 | 0.2754 |
| Original BLCoiR queries, PE | 57.92% | 80.15% | 85.06% | 0.6717 | 0.5006 |

To inspect one report (its graph, node types, PageRank scores, query and fixed files):

```bash
./blcoir.sh explain --project GitHub/vertx --bug 1247
```

### Command reference

```
./blcoir.sh query   [--project P] [--out DIR] [--length 32] [--no-coref] [--paper-selection] [--source brtext|br]
./blcoir.sh eval    --queries DIR [--file BLCoiR.txt] [--project P] [--out results/NAME] [--split tune|test]
./blcoir.sh eval    --baseline brtext [--project P]
./blcoir.sh run     [same options as query]           # query + eval
./blcoir.sh explain --project P --bug ID
./blcoir.sh df      --project P --terms "a b c"        # document frequency of terms in an index
```

- `--project` accepts a project (`GitHub/vertx`), a dataset (`GitHub`) or a name (`vertx`). Lists
  are comma-separated.
- `--paper-selection` uses plain top-L PageRank terms. The default additionally leaves Other Verbs
  out of the query and boosts code entities (see [Known differences](#known-differences)).
- `--split tune|test` evaluates only even or only odd bug ids.

Every evaluation writes two files:
- `summary.md`, with Top1/5/10, MRR and MAP, each with and without the K=10 cut-off, by report
  category, dataset and project.
- `ranks.tsv`, with the rank of every fixed file for every report.

## 6. Repository layout

| Path | Contents |
|---|---|
| `ase23-main-262.pdf` | The paper |
| `Dataset/<dataset>/<project>/` | `BR/` full bug reports, `ST/` stack traces, `changeset.xml` ground truth (fixed files per bug) |
| `BRText/<dataset>/<project>/<id>.txt` | Report text without stack traces; first line is the summary. This is BLCoiR's input |
| `Lucene/Lucene_Index/<dataset>/<project>/` | Lucene 9.3 indexes of the source files (field `contents`) |
| `Lucene/Lucene-Index2File-Mapping/<dataset>/<project>.txt` | Index document number → source file path |
| `Query/<dataset>/<project>/*.txt` | Queries from the original study (`<bug id> TAB <query>`) |
| `IRBL-Evaluation/` | Rank lists of the six compared IRFL tools (`<bug id> TAB <rank>`) |
| `Model-Evaluation/` | Rank lists of BLCoiR queries under rVSM and Indri |
| `Query-Evaluation/` | Rank lists of query variants from the original study |
| `src/main/java/uc/eecs/blcoir/` | **Re-implementation** (built by `mvn package`) |
| `src/main/resources/categories.tsv` | PE / NL / PES / NLS category of each report |
| `scripts/` | `score_rank_files.py` (Tables VIII and X), `select_experiment.py` (query-selection experiments), `extract_categories.py` |
| `src/java/`, `pom.legacy.xml`, `lib/` | Experimental code of the original study, kept for reference. Not compiled |

Datasets: BLIZZARD (6 projects, 163 reports), LtR (4, 50), Bench4BL (26, 278), GitHub (8, 201).
That is 44 projects and 692 concurrency bug reports. Categories: **PE** = the report text contains
program entities, **NL** = natural language only. **PES / NLS** are the reports in those groups that
also contain a stack trace. Dataset_PE in Tables VII and IX is PE + PES (549); Dataset_NL is
NL + NLS (142; the paper states 143, but its Table VII percentages imply 142).

## 7. How the code maps to the paper

| Paper | Code (`src/main/java/uc/eecs/blcoir/`) |
|---|---|
| §III-B bug report parsing (Alg. 1 l.4–9) | `nlp/BugReportParser`: CoreNLP 4.5.7, dependency parse, statistical coreference |
| Table III, concept types | `kg/ConceptType`, `kg/ConceptLexicon` |
| Removing non-concept nodes (Alg. 1 l.12–21) | `kg/KnowledgeGraphBuilder.prunedDependencyEdges` |
| Table IV, concept relations CR1–CR9 | `kg/KnowledgeGraphBuilder.build` |
| §III-D PageRank term weighting, top L (Alg. 1 l.25) | `rank/PageRank`, `query/QueryFormulator` |
| §III-E Lucene retrieval (Alg. 1 l.26) | `ir/LuceneRetriever` |
| §IV-A4 Top@K, MRR, MAP | `eval/Metrics`, `eval/Evaluator` |

Implementation decisions and the experiments behind them are in
[README-REPRODUCTION.md](README-REPRODUCTION.md).

## Known differences

- **Regenerated queries score lower than the original ones** (PE MRR@10 0.564 vs 0.672). The gap
  comes from details the paper does not specify: the concurrency word lists, how far relation edges
  reach, and the original code's hand-tuned stop list (`src/java/uc/eecs/nlp/StopWords.java`). The
  default query selection was tuned on even bug ids only and checked on odd ids. See
  README-REPRODUCTION.md.
- **MRR and MAP.** The paper's tables use a cut-off at K = 10 (Fig. 4): MRR@10 reproduces the stored
  rank lists exactly. MAP lands within about 0.01, because the exact averaging could not be
  recovered. Both cut-off and uncut values are reported.
- **"All" rows.** The paper's Dataset_All rows are not a plain combination of the NL and PE rows
  (for example 360 = 44.48% implies about 809 reports). Compare the NL and PE rows instead.
- **Report count.** Two reports (Bench4BL HIVE-9199 and GitHub grpc-1549) are missing from the
  original category lists. They count in "All" (693 here vs. 692) but not in NL or PE.
- **BLCoiR+** is not re-implemented.

## Citation

```latex
@inproceedings{BLCoiR,
  author       = {Shuai Shao and
                  Tingting Yu},
  title        = {Information Retrieval-Based Fault Localization for Concurrent Programs},
  booktitle    = {38th {IEEE/ACM} International Conference on Automated Software Engineering,
                  {ASE} 2023, Luxembourg, September 11-15, 2023},
  pages        = {1467--1479},
  publisher    = {{IEEE}},
  year         = {2023},
  url          = {https://doi.org/10.1109/ASE56229.2023.00122},
  doi          = {10.1109/ASE56229.2023.00122},
}
```
