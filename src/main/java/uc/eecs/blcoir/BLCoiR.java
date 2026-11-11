package uc.eecs.blcoir;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import uc.eecs.blcoir.data.BugReport;
import uc.eecs.blcoir.data.DatasetLoader;
import uc.eecs.blcoir.data.Project;
import uc.eecs.blcoir.eval.Evaluator;
import uc.eecs.blcoir.kg.KnowledgeGraph;
import uc.eecs.blcoir.nlp.BugReportParser;
import uc.eecs.blcoir.query.QueryFormulator;

/**
 * BLCoiR: Information Retrieval-based Fault Localization for Concurrent Programs (ASE 2023).
 *
 * <pre>
 * query   [--project P] [--out Query-Generated] [--length 32] [--no-coref]
 *         Generate BLCoiR queries for every bug report -> OUT/&lt;project&gt;/BLCoiR.txt
 * eval    --queries DIR [--file BLCoiR.txt] [--project P] [--out results/NAME]
 *         Evaluate query files DIR/&lt;project&gt;/FILE (format: id TAB query)
 * eval    --baseline brtext [--project P] [--out results/NAME]
 *         Baseline: the whole bug report text is the query
 * run     [--project P] [--length 32] [--no-coref]       query + eval in one go
 * explain --project P --bug ID [--no-coref]             show the knowledge graph of one report
 * </pre>
 */
public final class BLCoiR {

  public static void main(String[] argv) throws Exception {
    if (argv.length == 0) {
      usage();
      return;
    }
    Map<String, String> a = parseArgs(argv);
    Config config = Config.fromSystem();
    if (a.containsKey("length")) config.queryLength = Integer.parseInt(a.get("length"));
    if (a.containsKey("no-coref")) config.coref = false;
    if (a.containsKey("source")) config.reportSource = a.get("source");
    if (a.containsKey("paper-selection")) {
      // plain top-L PageRank terms, exactly as described in the paper
      config.excludeOtherVerbs = false;
      config.codeEntityBoost = 1.0;
    }
    DatasetLoader loader = new DatasetLoader(config);
    List<Project> projects = loader.projects(a.get("project"));
    if (projects.isEmpty()) throw new IllegalArgumentException("no project matches " + a.get("project"));

    switch (argv[0]) {
      case "query":
        generate(config, loader, projects, config.home.resolve(a.getOrDefault("out", "Query-Generated")));
        break;
      case "eval":
        evaluate(config, loader, projects, a);
        break;
      case "run": {
        Path qdir = config.home.resolve(a.getOrDefault("queries", "Query-Generated"));
        generate(config, loader, projects, qdir);
        a.put("queries", qdir.toString());
        a.putIfAbsent("out", "results/BLCoiR-generated");
        evaluate(config, loader, projects, a);
        break;
      }
      case "explain":
        explain(config, loader, projects.get(0), Integer.parseInt(a.get("bug")));
        break;
      case "df": // diagnostics: document frequency of terms in a project's index
        try (uc.eecs.blcoir.ir.LuceneRetriever r = new uc.eecs.blcoir.ir.LuceneRetriever(config, projects.get(0))) {
          System.out.println("docs=" + r.numDocs());
          for (String t : a.get("terms").split("[ ,]+")) System.out.println(t + "\t" + r.docFreq(t));
        }
        break;
      default:
        usage();
    }
  }

  static void generate(Config config, DatasetLoader loader, List<Project> projects, Path outDir)
      throws IOException {
    System.err.println("Loading CoreNLP (coref=" + config.coref + ") ...");
    BugReportParser parser = new BugReportParser(config.coref, config.maxReportChars);
    QueryFormulator formulator = new QueryFormulator(config);
    long t0 = System.currentTimeMillis();
    int done = 0;
    for (Project p : projects) {
      List<BugReport> reports = loader.bugReports(p);
      Path f = outDir.resolve(p.id).resolve("BLCoiR.txt");
      Files.createDirectories(f.getParent());
      Path nodesFile = f.resolveSibling("BLCoiR.nodes.tsv");
      try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(f, StandardCharsets.UTF_8));
          PrintWriter nw = new PrintWriter(Files.newBufferedWriter(nodesFile, StandardCharsets.UTF_8))) {
        nw.println("bug_id\tterm\ttypes\tpagerank\tdegree\tfreq\tfirst_sentence\tsurface\tcr_degree");
        for (BugReport br : reports) {
          String q;
          try {
            QueryFormulator.Result r = formulator.formulate(parser.parse(br.text));
            q = r.query();
            KnowledgeGraph g = r.graph;
            for (Map.Entry<String, Double> e : r.scores.entrySet()) {
              String t = e.getKey();
              nw.printf(Locale.ROOT, "%d\t%s\t%s\t%.6f\t%d\t%d\t%d\t%s\t%d%n", br.id, t,
                  g.types(t).toString().replaceAll("[\\[\\] ]", ""), e.getValue(),
                  g.neighbors(t).size(), g.frequency(t), g.firstSentence(t), g.surface(t), g.relationDegree(t));
            }
          } catch (RuntimeException e) {
            System.err.println("  ! " + p + " #" + br.id + ": " + e);
            q = "";
          }
          w.println(br.id + "\t" + q);
          done++;
        }
      }
      System.err.printf(
          Locale.ROOT, "  %-32s %3d reports  (%d total, %.0fs)%n",
          p.id, reports.size(), done, (System.currentTimeMillis() - t0) / 1000.0);
    }
    System.err.println("Queries written to " + outDir);
  }

  static void evaluate(Config config, DatasetLoader loader, List<Project> projects, Map<String, String> a)
      throws IOException {
    Evaluator ev = new Evaluator(config, loader).split(a.get("split"));
    String report;
    if ("brtext".equalsIgnoreCase(a.get("baseline"))) {
      Path out = config.home.resolve(a.getOrDefault("out", "results/baseline-BRText"));
      report = ev.evaluate(projects, br -> BugReportParser.clean(br.text, Integer.MAX_VALUE), out);
      System.out.println("# Baseline BRText\n\n" + report + "\nWritten to " + out);
      return;
    }
    if (!a.containsKey("queries")) throw new IllegalArgumentException("eval needs --queries DIR or --baseline brtext");
    Path qdir = config.home.resolve(a.get("queries"));
    String file = a.getOrDefault("file", "BLCoiR.txt");
    Map<String, String> queries = new HashMap<>();
    for (Project p : projects) {
      Path f = qdir.resolve(p.id).resolve(file);
      if (!Files.exists(f)) continue;
      for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
        int tab = line.indexOf('\t');
        if (tab > 0) queries.put(p.id + "#" + line.substring(0, tab).trim(), line.substring(tab + 1));
      }
    }
    String name = qdir.getFileName() + "-" + file.replace(".txt", "");
    Path out = config.home.resolve(a.getOrDefault("out", "results/" + name));
    report = ev.evaluate(projects, br -> queries.get(br.project.id + "#" + br.id), out);
    System.out.println("# " + qdir + " / " + file + "\n\n" + report + "\nWritten to " + out);
  }

  static void explain(Config config, DatasetLoader loader, Project p, int bugId) throws IOException {
    BugReport br = null;
    for (BugReport b : loader.bugReports(p)) if (b.id == bugId) br = b;
    if (br == null) throw new IllegalArgumentException("no bug " + bugId + " in " + p);
    BugReportParser parser = new BugReportParser(config.coref, config.maxReportChars);
    QueryFormulator.Result r = new QueryFormulator(config).formulate(parser.parse(br.text));
    KnowledgeGraph g = r.graph;
    System.out.println("== " + p + " #" + bugId + " (" + br.category + ")\n" + br.text.trim() + "\n");
    System.out.println("nodes=" + g.nodes().size() + " edges=" + g.edgeCount() + " sources=" + g.edgeSources());
    List<Map.Entry<String, Double>> sorted = new ArrayList<>(r.scores.entrySet());
    sorted.sort((x, y) -> Double.compare(y.getValue(), x.getValue()));
    for (Map.Entry<String, Double> e : sorted.subList(0, Math.min(40, sorted.size())))
      System.out.printf(Locale.ROOT, "  %-28s %-12s %.4f%n", e.getKey(), g.types(e.getKey()), e.getValue());
    System.out.println("\nQuery: " + r.query());
    System.out.println("Gold:  " + br.goldFiles);
  }

  static Map<String, String> parseArgs(String[] argv) {
    Map<String, String> m = new HashMap<>();
    for (int i = 1; i < argv.length; i++) {
      if (!argv[i].startsWith("--")) continue;
      String k = argv[i].substring(2);
      if (i + 1 < argv.length && !argv[i + 1].startsWith("--")) m.put(k, argv[++i]);
      else m.put(k, "true");
    }
    return m;
  }

  static void usage() {
    System.out.println(
        "usage: blcoir <query|eval|run|explain> [options]\n"
            + "  query   [--project P] [--out Query-Generated] [--length 32] [--no-coref]\n"
            + "  eval    --queries DIR [--file BLCoiR.txt] [--project P] [--out results/NAME]\n"
            + "  eval    --baseline brtext [--project P]\n"
            + "          eval also takes --split tune|test (even / odd bug ids)\n"
            + "  run     [--project P] [--length 32] [--no-coref]\n"
            + "  query/run also take --paper-selection (plain top-L PageRank) and --source br|brtext\n"
            + "  explain --project P --bug ID\n"
            + "P may be a project id (GitHub/vertx), a dataset (GitHub) or a name (vertx); comma-separated.\n"
            + "Run from the repository root, or pass -Dblcoir.home=<root>.");
  }

  private BLCoiR() {}
}
