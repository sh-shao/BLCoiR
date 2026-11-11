package uc.eecs.blcoir.rank;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import uc.eecs.blcoir.kg.KnowledgeGraph;

/**
 * Term Weighting (Section III-D). On an undirected graph, In(V) = Out(V) = neighbours(V):
 *
 * <pre>S(Vi) = (1 - d) + d * sum_{j in In(Vi)} S(Vj) / |Out(Vj)|</pre>
 */
public final class PageRank {
  private final double d;
  private final int maxIter;
  private final double eps;

  public PageRank(double damping, int maxIter, double eps) {
    this.d = damping;
    this.maxIter = maxIter;
    this.eps = eps;
  }

  /** Scores in node insertion order (stable for tie-breaking). */
  public LinkedHashMap<String, Double> score(KnowledgeGraph g) {
    Map<String, Double> s = new HashMap<>();
    for (String v : g.nodes()) s.put(v, 1.0);
    for (int it = 0; it < maxIter; it++) {
      Map<String, Double> next = new HashMap<>();
      double delta = 0;
      for (String v : g.nodes()) {
        double sum = 0;
        for (String u : g.neighbors(v)) sum += s.get(u) / g.neighbors(u).size();
        double val = (1 - d) + d * sum;
        next.put(v, val);
        delta = Math.max(delta, Math.abs(val - s.get(v)));
      }
      s = next;
      if (delta < eps) break;
    }
    LinkedHashMap<String, Double> out = new LinkedHashMap<>();
    for (String v : g.nodes()) out.put(v, s.get(v));
    return out;
  }
}
