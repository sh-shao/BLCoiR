package uc.eecs.blcoir.eval;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import uc.eecs.blcoir.Config;
import uc.eecs.blcoir.data.BugReport;
import uc.eecs.blcoir.data.DatasetLoader;
import uc.eecs.blcoir.data.Project;
import uc.eecs.blcoir.ir.LuceneRetriever;

/** Runs every query against its project's Lucene index and reports the paper's metrics. */
public final class Evaluator {
  private final Config config;
  private final DatasetLoader loader;
  private java.util.function.Predicate<BugReport> filter = br -> true;

  /**
   * Restricts evaluation to a split: "tune" = even bug ids, "test" = odd bug ids (held out while
   * adjusting rules), anything else = all reports.
   */
  public Evaluator split(String split) {
    if ("tune".equals(split)) filter = br -> br.id % 2 == 0;
    else if ("test".equals(split)) filter = br -> br.id % 2 != 0;
    else filter = br -> true;
    return this;
  }

  public Evaluator(Config config, DatasetLoader loader) {
    this.config = config;
    this.loader = loader;
  }

  /**
   * @param queryFor returns the query for a bug report, or null if there is none (the report then
   *     counts as a miss, so all methods are compared on the same 692 reports)
   */
  public String evaluate(List<Project> projects, Function<BugReport, String> queryFor, Path outDir)
      throws IOException {
    Files.createDirectories(outDir);
    Map<String, Metrics> byDataset = new TreeMap<>(), byQuality = new TreeMap<>();
    Map<String, Metrics> byDatasetQuality = new TreeMap<>(), byProject = new LinkedHashMap<>();
    Metrics all = new Metrics();
    int missingQueries = 0;

    try (PrintWriter ranksOut =
        new PrintWriter(Files.newBufferedWriter(outDir.resolve("ranks.tsv"), StandardCharsets.UTF_8))) {
      ranksOut.println("project\tbug_id\tcategory\tfirst_rank\tgold_ranks\tquery");
      for (Project p : projects) {
        List<BugReport> reports = loader.bugReports(p);
        if (reports.isEmpty()) continue;
        try (LuceneRetriever retriever = new LuceneRetriever(config, p)) {
          for (BugReport br : reports) {
            if (!filter.test(br)) continue;
            String q = queryFor.apply(br);
            if (q == null) missingQueries++;
            List<Integer> ranks = LuceneRetriever.goldRanks(retriever.search(q), br.goldFiles);
            Metrics m = new Metrics();
            m.add(ranks);
            all.merge(m);
            byProject.computeIfAbsent(p.id, k -> new Metrics()).merge(m);
            byDataset.computeIfAbsent(p.dataset(), k -> new Metrics()).merge(m);
            byQuality.computeIfAbsent(br.quality(), k -> new Metrics()).merge(m);
            byDatasetQuality.computeIfAbsent(p.dataset() + " / " + br.quality(), k -> new Metrics()).merge(m);
            if (br.category != null) {
              // ST subsets used for Table X (Dataset_NLS / Dataset_PES)
              if (br.category.equals("STN")) byQuality.computeIfAbsent("NLS (NL with stack trace)", k -> new Metrics()).merge(m);
              if (br.category.equals("STP")) byQuality.computeIfAbsent("PES (PE with stack trace)", k -> new Metrics()).merge(m);
              if (br.category.equals("NL")) byQuality.computeIfAbsent("NL without stack trace", k -> new Metrics()).merge(m);
              if (br.category.equals("PE")) byQuality.computeIfAbsent("PE without stack trace", k -> new Metrics()).merge(m);
            }
            ranksOut.printf(
                "%s\t%d\t%s\t%d\t%s\t%s%n",
                p.id, br.id, br.category, ranks.isEmpty() ? -1 : ranks.get(0),
                ranks.stream().map(String::valueOf).collect(Collectors.joining(",")),
                q == null ? "" : q.replace('\t', ' ').replace('\n', ' '));
          }
        }
      }
    }

    StringBuilder sb = new StringBuilder();
    sb.append("## By bug-report quality\n\n"
        + "NL / PE = Tables VII and IX (stack-trace reports included). Table X uses the four disjoint\n"
        + "groups: NL without stack trace, NLS, PE without stack trace, PES.\n\n").append(Metrics.header()).append('\n');
    for (Map.Entry<String, Metrics> e : byQuality.entrySet()) sb.append(e.getValue().row(e.getKey())).append('\n');
    sb.append(all.row("**All**")).append("\n\n");
    sb.append("## By dataset\n\n").append(Metrics.header()).append('\n');
    for (Map.Entry<String, Metrics> e : byDataset.entrySet()) sb.append(e.getValue().row(e.getKey())).append('\n');
    sb.append('\n').append("## By dataset and quality\n\n").append(Metrics.header()).append('\n');
    for (Map.Entry<String, Metrics> e : byDatasetQuality.entrySet()) sb.append(e.getValue().row(e.getKey())).append('\n');
    sb.append('\n').append("## By project\n\n").append(Metrics.header()).append('\n');
    for (Map.Entry<String, Metrics> e : byProject.entrySet()) sb.append(e.getValue().row(e.getKey())).append('\n');
    if (missingQueries > 0)
      sb.append("\n_").append(missingQueries).append(" bug reports had no query and count as misses._\n");
    Files.write(outDir.resolve("summary.md"), sb.toString().getBytes(StandardCharsets.UTF_8));
    return sb.toString();
  }
}
