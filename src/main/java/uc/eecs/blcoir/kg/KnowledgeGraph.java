package uc.eecs.blcoir.kg;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The bug-report knowledge graph: concept nodes (terms) with their concept types, connected by
 * undirected edges. PageRank is applied on the undirected graph (Section III-D), so edges are stored
 * symmetrically.
 */
public final class KnowledgeGraph {
  private final Map<String, Set<String>> adj = new LinkedHashMap<>();
  private final Map<String, EnumSet<ConceptType>> types = new LinkedHashMap<>();
  /** Diagnostics: occurrences, first sentence index, first surface form of each term. */
  private final Map<String, int[]> stats = new LinkedHashMap<>(); // {freq, firstSentence}
  private final Map<String, String> surface = new LinkedHashMap<>();
  private final Map<String, Integer> relationEdges = new LinkedHashMap<>();

  public void noteOccurrence(String term, int sentence, String surfaceForm) {
    int[] st = stats.computeIfAbsent(term, k -> new int[] {0, sentence});
    st[0]++;
    surface.putIfAbsent(term, surfaceForm);
  }

  public int frequency(String term) {
    return stats.containsKey(term) ? stats.get(term)[0] : 0;
  }

  public int firstSentence(String term) {
    return stats.containsKey(term) ? stats.get(term)[1] : -1;
  }

  public String surface(String term) {
    return surface.getOrDefault(term, term);
  }

  /** How many edges each source (dependency, CR1..CR9) contributed; for --explain output. */
  private final Map<String, Integer> edgeSources = new TreeMap<>();

  public void addNode(String term, ConceptType type) {
    adj.computeIfAbsent(term, k -> new LinkedHashSet<>());
    EnumSet<ConceptType> t = types.computeIfAbsent(term, k -> EnumSet.noneOf(ConceptType.class));
    if (type != null) t.add(type);
  }

  /** Adds an undirected edge; returns true if it is new. */
  public boolean addEdge(String a, String b, String source) {
    if (a.equals(b)) return false;
    addNode(a, null);
    addNode(b, null);
    boolean added = adj.get(a).add(b);
    adj.get(b).add(a);
    if (added) {
      edgeSources.merge(source, 1, Integer::sum);
      if (source.startsWith("CR")) {
        relationEdges.merge(a, 1, Integer::sum);
        relationEdges.merge(b, 1, Integer::sum);
      }
    }
    return added;
  }

  public Set<String> nodes() {
    return Collections.unmodifiableSet(adj.keySet());
  }

  public Set<String> neighbors(String term) {
    return adj.getOrDefault(term, Collections.emptySet());
  }

  public Set<ConceptType> types(String term) {
    return types.getOrDefault(term, EnumSet.noneOf(ConceptType.class));
  }

  public int edgeCount() {
    int n = 0;
    for (Set<String> s : adj.values()) n += s.size();
    return n / 2;
  }

  /** Number of concept-relation (CR1-CR9) edges touching the term. */
  public int relationDegree(String term) {
    return relationEdges.getOrDefault(term, 0);
  }

  public Map<String, Integer> edgeSources() {
    return Collections.unmodifiableMap(edgeSources);
  }

  public boolean hasEdge(String a, String b) {
    return adj.containsKey(a) && adj.get(a).contains(b);
  }
}
