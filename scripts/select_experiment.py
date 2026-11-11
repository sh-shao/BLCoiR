"""Query-selection experiments on the dumped knowledge-graph nodes (Query-Generated/**/BLCoiR.nodes.tsv).

Rebuilds queries from node features with a given rule, then scores them with the Java evaluator
on the tune split (even bug ids). Used only to choose rules; the odd ids stay held out.
usage: python3 -I scripts/select_experiment.py ROOT OUTDIR SPLIT name1 [name2 ...]
"""
import csv, collections, os, re, subprocess, sys

ROOT, OUT, SPLIT = sys.argv[1], sys.argv[2], sys.argv[3]
STOP = set()
for line in open(os.path.join(ROOT, "src/main/resources/stopwords.txt")):
    if not line.startswith("#"): STOP.update(line.split())

# the hand-tuned stop list of the original code (src/java/uc/eecs/nlp/StopWords.java)
OSTOP = set(re.findall(r'"([^"]+)"', open(os.path.join(ROOT, "src/java/uc/eecs/nlp/StopWords.java")).read()))

def load():
    nodes = collections.defaultdict(list)  # (project, id) -> list of dict
    base = os.environ.get("NODES", os.path.join(ROOT, "Query-Generated"))
    for d, _, files in os.walk(base):
        if "BLCoiR.nodes.tsv" in files:
            proj = os.path.relpath(d, base)
            for r in csv.DictReader(open(os.path.join(d, "BLCoiR.nodes.tsv")), delimiter="\t"):
                r["types"] = set(r["types"].split(",")) if r["types"] else set()
                for k in ("pagerank",): r[k] = float(r[k])
                for k in ("degree", "freq", "first_sentence", "cr_degree"): r[k] = int(r[k])
                nodes[(proj, r["bug_id"])].append(r)
    return nodes

CONC = {"CBG", "CME", "COP", "CSY"}

def rule(name):
    """Rule = '+'-separated options, e.g. noOTV+coe2+title2+surf+L24.
    noOTV: drop pure other-verbs | noCME: drop pure CME/COP terms | coeK: x K for COE/EXC
    titleK: x K for terms first seen in the title | concK: x K for CBG/CSY/EXC
    surf: use the surface form instead of the lemma | both: add surface form if it differs | L<n>: length"""
    opts = name.split("+"); L = 32; mult = []; filters = []; form = "lemma"
    for o in opts:
        if o == "pr": pass
        elif o.startswith("L"): L = int(o[1:])
        elif o == "noOTV": filters.append(lambda n: n["types"] != {"OTV"})
        elif o == "noCME": filters.append(lambda n: not (n["types"] <= {"CME", "COP"}))
        elif o == "noSYB": filters.append(lambda n: n["types"] != {"SYB"})
        elif o.startswith("coe"): k = float(o[3:]); mult.append(lambda n, k=k: k if n["types"] & {"COE", "EXC"} else 1)
        elif o.startswith("title"): k = float(o[5:]); mult.append(lambda n, k=k: k if n["first_sentence"] == 0 else 1)
        elif o.startswith("conc"): k = float(o[4:]); mult.append(lambda n, k=k: k if n["types"] & {"CBG", "CSY", "EXC"} else 1)
        elif o.startswith("freq"): k = float(o[4:]); mult.append(lambda n, k=k: n["freq"] ** k)
        elif o in ("surf", "both"): form = o
        elif o == "crOnly": filters.append(lambda n: n["cr_degree"] > 0 or bool(n["types"] & {"COE", "EXC", "CBG"}))
        elif o.startswith("crx"): k = float(o[3:]); mult.append(lambda n, k=k: k if n["cr_degree"] > 0 else 1)
        elif o == "ostop": filters.append(lambda n: n["term"] not in OSTOP)
        else: raise SystemExit("unknown option " + o)
    def score(n):
        v = n["pagerank"]
        for m in mult: v *= m(n)
        return v
    def f(ns):
        out = []
        cand = [n for n in ns if n["term"] not in STOP and len(n["term"]) > 1 and not re.search(r"\d", n["term"])
                and all(fl(n) for fl in filters)]
        cand.sort(key=score, reverse=True)
        for n in cand[:L]:
            t, sf = n["term"], n["surface"] or n["term"]
            if form == "surf" and sf not in STOP and len(sf) > 1: out.append(sf)
            else:
                out.append(t)
                if form == "both" and sf != t and sf not in STOP and len(sf) > 1: out.append(sf)
        return out
    return f

def write(nodes, f, tag):
    d = os.path.join(OUT, tag)
    for (proj, bid), ns in nodes.items():
        p = os.path.join(d, proj); os.makedirs(p, exist_ok=True)
    files = collections.defaultdict(list)
    for (proj, bid), ns in nodes.items(): files[proj].append(f"{bid}\t{' '.join(f(ns))}")
    for proj, lines in files.items():
        open(os.path.join(d, proj, "BLCoiR.txt"), "w").write("\n".join(lines) + "\n")
    return d

def evaluate(d):
    out = subprocess.run([os.path.join(ROOT, "blcoir.sh"), "eval", "--queries", d, "--split", SPLIT,
                          "--out", d + "-res"], capture_output=True, text=True).stdout
    rows = {}
    for line in out.splitlines():
        m = re.match(r"\| (NL|PE|\*\*All\*\*) \| (\d+) \| \d+ \(([\d.]+)%\) \| \d+ \(([\d.]+)%\) \| \d+ \(([\d.]+)%\) \| ([\d.]+) \| ([\d.]+) \|", line)
        if m and m.group(1) not in rows: rows[m.group(1).strip("*")] = (m.group(3), m.group(5), m.group(6), m.group(7))
    return rows

nodes = load()
print(f"{'rule':18} {'PE top1/top10/MRR/MAP':32} {'NL MRR/MAP':14} {'All MRR/MAP':14} avgLen")
for name in sys.argv[4:]:
    f = rule(name)
    lens = [len(f(ns)) for ns in nodes.values()]
    r = evaluate(write(nodes, f, name))
    pe, nl, al = r.get("PE"), r.get("NL"), r.get("All")
    print(f"{name:18} {pe[0]:>6}/{pe[1]:>6}/{pe[2]}/{pe[3]}   {nl[2]}/{nl[3]}  {al[2]}/{al[3]}  {sum(lens)/len(lens):.1f}", flush=True)
