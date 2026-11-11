"""Extract the PE / NL / STP / STN bug-report categories from the original
src/java/utils/config/ClassificationID.java into a TSV used by the evaluator.

PE  = report text contains program entities, no stack trace
NL  = natural language only, no stack trace
STP = has stack trace + program entities   (counted as PE in the paper)
STN = has stack trace, natural language    (counted as NL in the paper)
"""
import re, sys, pathlib

PROJECTS = {
    "CAMEL": "Bench4BL/Apache/CAMEL", "HBASE": "Bench4BL/Apache/HBASE", "HIVE": "Bench4BL/Apache/HIVE",
    "CODEC": "Bench4BL/Commons/CODEC", "COLLECTIONS": "Bench4BL/Commons/COLLECTIONS",
    "COMPRESS": "Bench4BL/Commons/COMPRESS", "CONFIGURATION": "Bench4BL/Commons/CONFIGURATION",
    "IO": "Bench4BL/Commons/IO", "LANG": "Bench4BL/Commons/LANG", "MATH": "Bench4BL/Commons/MATH",
    "AMQP": "Bench4BL/Spring/AMQP", "BATCH": "Bench4BL/Spring/BATCH", "DATACMNS": "Bench4BL/Spring/DATACMNS",
    "DATAGRAPH": "Bench4BL/Spring/DATAGRAPH", "DATAJPA": "Bench4BL/Spring/DATAJPA",
    "DATAMONGO": "Bench4BL/Spring/DATAMONGO", "DATAREDIS": "Bench4BL/Spring/DATAREDIS",
    "SEC": "Bench4BL/Spring/SEC", "SECOAUTH": "Bench4BL/Spring/SECOAUTH", "SGF": "Bench4BL/Spring/SGF",
    "SHDP": "Bench4BL/Spring/SHDP", "SPR": "Bench4BL/Spring/SPR", "SWF": "Bench4BL/Spring/SWF",
    "SWS": "Bench4BL/Spring/SWS", "WFCORE": "Bench4BL/Wildfly/WFCORE", "WFLY": "Bench4BL/Wildfly/WFLY",
    "ECF": "BLIZZARD/ecf", "JDT_CORE": "BLIZZARD/eclipse.jdt.core", "JDT_DEBUG": "BLIZZARD/eclipse.jdt.debug",
    "JDT_UI": "BLIZZARD/eclipse.jdt.ui", "PDE_UI": "BLIZZARD/eclipse.pde.ui", "TOMCAT70": "BLIZZARD/tomcat70",
    "ASPECTJ": "LtR/AspectJ", "BIRT": "LtR/Birt", "PLATFORM": "LtR/Platform", "SWT": "LtR/SWT",
    "DRUID": "GitHub/druid", "GRPC": "GitHub/grpc", "PRESTO": "GitHub/presto", "PULSAR": "GitHub/pulsar",
    "REDISSON": "GitHub/redisson", "ROCKETMQ": "GitHub/rocketmq", "TRINO": "GitHub/trino", "VERTX": "GitHub/vertx",
}
CATS = ("PE", "NL", "STP", "STN")

src = pathlib.Path(sys.argv[1]).read_text()
out = {}
for m in re.finditer(r"public static List<Integer> ([A-Z0-9_]+)\s*=\s*Arrays\.asList\(([^;]*?)\);", src, re.S):
    name, body = m.group(1), m.group(2)
    for cat in CATS:
        if name.endswith("_" + cat):
            proj = name[: -len(cat) - 1]
            if proj in PROJECTS:
                for bid in re.findall(r"\d+", body):
                    key = (PROJECTS[proj], int(bid))
                    if key in out and out[key] != cat:
                        print(f"warn: {key} in {out[key]} and {cat}", file=sys.stderr)
                    out[key] = cat
            break

with open(sys.argv[2], "w") as f:
    f.write("# project\tbug_id\tcategory (PE|NL|STP|STN)\n")
    for (proj, bid), cat in sorted(out.items()):
        f.write(f"{proj}\t{bid}\t{cat}\n")
from collections import Counter
print(len(out), Counter(out.values()), file=sys.stderr)
