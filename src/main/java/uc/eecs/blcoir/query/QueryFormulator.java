package uc.eecs.blcoir.query;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import uc.eecs.blcoir.Config;
import uc.eecs.blcoir.kg.ConceptType;
import uc.eecs.blcoir.kg.KnowledgeGraph;
import uc.eecs.blcoir.kg.KnowledgeGraphBuilder;
import uc.eecs.blcoir.nlp.ParsedReport;
import uc.eecs.blcoir.rank.PageRank;

/** Algorithm 1 lines 9-25: parsed report -> knowledge graph -> PageRank -> top-L query terms. */
public final class QueryFormulator {

  public static final class Result {
    public final List<String> terms;
    public final Map<String, Double> scores;
    public final KnowledgeGraph graph;

    Result(List<String> terms, Map<String, Double> scores, KnowledgeGraph graph) {
      this.terms = terms;
      this.scores = scores;
      this.graph = graph;
    }

    public String query() {
      return String.join(" ", terms);
    }
  }

  private static final Set<String> STOP = loadStopWords();

  private final Config config;
  private final KnowledgeGraphBuilder builder = new KnowledgeGraphBuilder();

  public QueryFormulator(Config config) {
    this.config = config;
  }

  public Result formulate(ParsedReport report) {
    KnowledgeGraph kg = builder.build(report);
    LinkedHashMap<String, Double> scores =
        new PageRank(config.damping, config.pageRankMaxIter, config.pageRankEpsilon).score(kg);
    // Selection score: PageRank, with code entities / exceptions boosted (config.codeEntityBoost).
    Map<String, Double> sel = new LinkedHashMap<>();
    for (Map.Entry<String, Double> e : scores.entrySet()) {
      Set<ConceptType> ty = kg.types(e.getKey());
      boolean code = ty.contains(ConceptType.COE) || ty.contains(ConceptType.EXC);
      sel.put(e.getKey(), e.getValue() * (code ? config.codeEntityBoost : 1.0));
    }
    List<Map.Entry<String, Double>> sorted = new ArrayList<>(sel.entrySet());
    // stable sort: ties keep first-occurrence order
    sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
    List<String> terms = new ArrayList<>();
    for (Map.Entry<String, Double> e : sorted) {
      String t = e.getKey();
      if (STOP.contains(t) || t.length() < 2 || t.matches(".*\\d.*")) continue;
      // Other Verbs stay in the graph (they connect concepts) but are not used as query terms.
      if (config.excludeOtherVerbs && kg.types(t).equals(EnumSet.of(ConceptType.OTV))) continue;
      terms.add(t);
      if (terms.size() >= config.queryLength) break;
    }
    return new Result(terms, scores, kg);
  }

  public static boolean isStopWord(String term) {
    return STOP.contains(term);
  }

  private static Set<String> loadStopWords() {
    Set<String> s = new HashSet<>();
    try (BufferedReader r =
        new BufferedReader(
            new InputStreamReader(
                QueryFormulator.class.getResourceAsStream("/stopwords.txt"),
                StandardCharsets.UTF_8))) {
      String line;
      while ((line = r.readLine()) != null) {
        if (line.startsWith("#")) continue;
        for (String w : line.trim().split("\\s+")) if (!w.isEmpty()) s.add(w);
      }
    } catch (IOException | NullPointerException e) {
      throw new IllegalStateException("stopwords.txt missing from classpath", e);
    }
    return s;
  }
}
