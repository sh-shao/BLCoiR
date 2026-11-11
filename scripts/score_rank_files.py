"""Recompute the paper's comparison tables from the stored rank files (Python 3, stdlib only).

  Table X    : IRBL-Evaluation/All/<Tool>_<NL|STN|PE|STP>.txt  (BugLocator, BLUiR, BRTracer, AmaLgam, BLIA, BLIZZARD)
  Table VIII : Model-Evaluation/<dataset>/<rVSM|Indri>.txt     (BLCoiR queries with other IR models)

Each file has one line per ground-truth file found: "<bug_id> <1-based rank>".
Denominators are the category sizes from src/main/resources/categories.tsv
(Table X: NL=83, NLS=59, PE=403, PES=147 disjoint groups; Table VIII: all reports).
MRR and MAP use the cut-off K=10, which reproduces the paper's MRR exactly; MAP lands within ~0.01.

usage: python3 -I scripts/score_rank_files.py [repo_root]
"""
import collections, os, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
K = 10


def category_sizes():
    c = collections.Counter()
    for line in open(os.path.join(ROOT, "src/main/resources/categories.tsv")):
        if not line.startswith("#") and line.strip():
            c[line.rstrip("\n").split("\t")[2]] += 1
    return c


def load(paths):
    ranks = collections.defaultdict(set)
    for path in paths:
        for line in open(path):
            p = line.split()
            if len(p) >= 2 and p[1].lstrip("-").isdigit() and int(p[1]) > 0:
                ranks[(os.path.dirname(path), p[0])].add(int(p[1]))
    return ranks


def metrics(ranks, n):
    t1 = t5 = t10 = 0
    rr = ap = 0.0
    for ks in ranks.values():
        ks = sorted(ks)
        f = ks[0]
        t1 += f <= 1
        t5 += f <= 5
        t10 += f <= 10
        if f <= K:
            rr += 1 / f
        ap += sum((i + 1) / k for i, k in enumerate(ks) if k <= K) / len(ks)
    pct = lambda x: f"{100 * x / n:6.2f}%"
    return f"{pct(t1)} {pct(t5)} {pct(t10)}  {rr / n:.4f}  {ap / n:.4f}"


sizes = category_sizes()
head = f"{'':12} {'Top1':>7} {'Top5':>7} {'Top10':>7}  {'MRR':>6}  {'MAP':>6}"

print("Table X: state-of-the-art IRFL techniques (stored ranks)\n")
groups = [("Dataset_NL", "NL"), ("Dataset_NLS", "STN"), ("Dataset_PE", "PE"), ("Dataset_PES", "STP")]
tools = ["BugLocator", "BLUiR", "BRTracer", "AmaLgam", "BLIA", "BLIZZARD"]
for label, cat in groups:
    print(f"{label} (n={sizes[cat]})\n{head}")
    for t in tools:
        f = os.path.join(ROOT, "IRBL-Evaluation/All", f"{t}_{cat}.txt")
        if os.path.exists(f):
            print(f"{t:12} {metrics(load([f]), sizes[cat])}")
    print()
print(f"Dataset_All (n={sum(sizes[c] for _, c in groups)})\n{head}")
for t in tools:
    files = [os.path.join(ROOT, "IRBL-Evaluation/All", f"{t}_{c}.txt") for _, c in groups]
    # bug ids are only unique per file group here, so merge on (category file, bug id)
    print(f"{t:12} {metrics(load(files), sum(sizes[c] for _, c in groups))}")

print("\nTable VIII: BLCoiR queries with other IR models (stored ranks)\n")
n_all = sum(sizes.values())
print(f"All reports (n={n_all})\n{head}")
for model in ["rVSM", "Indri"]:
    files = [os.path.join(ROOT, "Model-Evaluation", d, f"{model}.txt")
             for d in ["BLIZZARD", "Bench4BL", "GitHub", "LtR"]]
    files = [f for f in files if os.path.exists(f)]
    print(f"{model:12} {metrics(load(files), n_all)}")
print("Lucene       -> run: ./blcoir.sh eval --queries Query --file BLCoiR.txt")
