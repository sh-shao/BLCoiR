package uc.eecs.blcoir.kg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import uc.eecs.blcoir.nlp.ParsedReport;
import uc.eecs.blcoir.nlp.ParsedReport.Dependency;
import uc.eecs.blcoir.nlp.ParsedReport.Sentence;
import uc.eecs.blcoir.nlp.ParsedReport.Token;
import uc.eecs.blcoir.nlp.ParsedReport.TokenRef;

/**
 * Knowledge Graph Construction (Section III-C, Algorithm 1 lines 9-24).
 *
 * <ol>
 *   <li>DenGraph = dependency graph of every sentence, with coreferent pronouns replaced by the
 *       representative mention so that they share one node (line 9).
 *   <li>Nodes that do not belong to a concept type are removed; their predecessors are linked to
 *       their successors (lines 12-21).
 *   <li>Concept relations CR1-CR9 (Table IV) add semantic edges (lines 22-24).
 * </ol>
 *
 * Nodes are terms (lower-cased lemmas / identifier parts), so the same concept mentioned in several
 * sentences is a single node.
 */
public final class KnowledgeGraphBuilder {

  private final ConceptLexicon lexicon = new ConceptLexicon();

  /** A token of the report after concept typing and coreference substitution. */
  private static final class Concept {
    final ConceptType type;
    final List<String> terms;

    Concept(ConceptType type, List<String> terms) {
      this.type = type;
      this.terms = terms;
    }
  }

  public KnowledgeGraph build(ParsedReport report) {
    KnowledgeGraph kg = new KnowledgeGraph();

    // ---- concept typing of every token (Concept Type Modeling) ----
    List<Map<Integer, Concept>> concepts = new ArrayList<>();
    for (Sentence s : report.sentences) {
      Map<Integer, Concept> m = new LinkedHashMap<>();
      for (Token t : s.tokens) {
        ConceptType type = lexicon.classify(t);
        if (type != null) {
          List<String> terms = terms(t, type);
          if (!terms.isEmpty()) m.put(t.index, new Concept(type, terms));
        }
      }
      concepts.add(m);
    }
    // ---- coreference: anaphor takes the concept of its representative mention (line 9) ----
    for (Map.Entry<TokenRef, TokenRef> e : report.corefs.entrySet()) {
      TokenRef ana = e.getKey(), rep = e.getValue();
      if (rep.sentence >= concepts.size() || ana.sentence >= concepts.size()) continue;
      Concept c = concepts.get(rep.sentence).get(rep.token);
      if (c != null) concepts.get(ana.sentence).put(ana.token, c);
    }

    Set<String> cr9 = new LinkedHashSet<>(); // participants of CR1, CR3, CR5

    for (int si = 0; si < report.sentences.size(); si++) {
      Sentence s = report.sentences.get(si);
      Map<Integer, Concept> typed = concepts.get(si);

      for (Map.Entry<Integer, Concept> ce : typed.entrySet()) {
        Concept c = ce.getValue();
        String word = s.token(ce.getKey()).word.toLowerCase(Locale.ROOT);
        for (String term : c.terms) {
          kg.addNode(term, c.type);
          kg.noteOccurrence(term, si, c.terms.size() == 1 ? word.replaceAll("[^a-z0-9_]", "") : term);
        }
        // parts of one identifier (HttpClientImpl.close -> httpclientimpl, close) stay connected
        for (int i = 1; i < c.terms.size(); i++)
          kg.addEdge(c.terms.get(i - 1), c.terms.get(i), "identifier");
      }

      // ---- DenGraph with untyped nodes removed (lines 12-21) ----
      for (int[] e : prunedDependencyEdges(s, typed.keySet())) {
        connect(kg, typed.get(e[0]), typed.get(e[1]), "dependency");
      }

      // ---- Concept Relation Modeling (Table IV) ----
      List<Concept> list = new ArrayList<>(typed.values());
      for (int i = 0; i < list.size(); i++) {
        for (int j = 0; j < list.size(); j++) {
          if (i == j) continue;
          Concept a = list.get(i), b = list.get(j);
          String cr = pairRelation(a.type, b.type);
          if (cr != null) {
            connect(kg, a, b, cr);
            if (cr.equals("CR1") || cr.equals("CR3") || cr.equals("CR5")) {
              cr9.addAll(a.terms);
              cr9.addAll(b.terms);
            }
          }
        }
      }
      // CR7: COP --obj--> OTN ;  CR8: OTN <-nsubj- g -obj-> CBG
      Map<Integer, List<Integer>> subjects = new HashMap<>(), objects = new HashMap<>();
      for (Dependency d : s.dependencies) {
        String rel = d.relation;
        if (isObject(rel)) {
          objects.computeIfAbsent(d.governor, k -> new ArrayList<>()).add(d.dependent);
          Concept gov = typed.get(d.governor), dep = typed.get(d.dependent);
          if (gov != null && dep != null && gov.type == ConceptType.COP && dep.type == ConceptType.OTN)
            connect(kg, gov, dep, "CR7");
        } else if (rel.startsWith("nsubj")) {
          subjects.computeIfAbsent(d.governor, k -> new ArrayList<>()).add(d.dependent);
        }
      }
      for (Map.Entry<Integer, List<Integer>> e : subjects.entrySet()) {
        for (int subj : e.getValue()) {
          Concept cs = typed.get(subj);
          if (cs == null || cs.type != ConceptType.OTN) continue;
          for (int obj : objects.getOrDefault(e.getKey(), List.of())) {
            Concept co = typed.get(obj);
            if (co != null && co.type == ConceptType.CBG) connect(kg, cs, co, "CR8");
          }
        }
      }
    }

    // CR9 = CR1 + CR3 + CR5: concurrency-related concepts are linked to each other, which raises
    // their weight during term weighting.
    List<String> c9 = new ArrayList<>(cr9);
    for (int i = 0; i < c9.size(); i++)
      for (int j = i + 1; j < c9.size(); j++) kg.addEdge(c9.get(i), c9.get(j), "CR9");

    return kg;
  }

  /** CR1-CR6 are type-pair patterns between concepts co-occurring in one sentence. */
  static String pairRelation(ConceptType a, ConceptType b) {
    if (b == ConceptType.CBG) {
      if (a == ConceptType.COE) return "CR1";
      if (a == ConceptType.CME) return "CR3";
      if (a == ConceptType.COP) return "CR5";
    }
    if (b.isBugIndicator()) {
      if (a == ConceptType.COE) return "CR2";
      if (a == ConceptType.CME) return "CR4";
      if (a == ConceptType.COP) return "CR6";
    }
    return null;
  }

  private static boolean isObject(String rel) {
    return rel.equals("obj") || rel.equals("dobj") || rel.equals("iobj");
  }

  private static void connect(KnowledgeGraph kg, Concept a, Concept b, String source) {
    for (String ta : a.terms) for (String tb : b.terms) kg.addEdge(ta, tb, source);
  }

  /**
   * Lines 12-21 of Algorithm 1: every node that is not a concept is removed, and each of its
   * predecessors is linked to each of its successors. Returns the remaining (governor, dependent)
   * edges between concept tokens.
   */
  static List<int[]> prunedDependencyEdges(Sentence s, Set<Integer> keep) {
    Map<Integer, Set<Integer>> out = new HashMap<>(), in = new HashMap<>();
    for (Dependency d : s.dependencies) {
      if (d.governor == 0 || d.relation.equals("punct")) continue;
      out.computeIfAbsent(d.governor, k -> new LinkedHashSet<>()).add(d.dependent);
      in.computeIfAbsent(d.dependent, k -> new LinkedHashSet<>()).add(d.governor);
    }
    for (Token t : s.tokens) {
      int i = t.index;
      if (keep.contains(i)) continue;
      Set<Integer> preds = in.getOrDefault(i, Set.of()), succs = out.getOrDefault(i, Set.of());
      for (int p : preds) {
        out.get(p).remove(i);
        for (int q : succs) {
          if (p == q) continue;
          out.get(p).add(q);
          in.computeIfAbsent(q, k -> new LinkedHashSet<>()).add(p);
        }
      }
      for (int q : succs) in.get(q).remove(i);
      in.remove(i);
      out.remove(i);
    }
    List<int[]> edges = new ArrayList<>();
    for (Map.Entry<Integer, Set<Integer>> e : out.entrySet())
      for (int q : e.getValue()) edges.add(new int[] {e.getKey(), q});
    return edges;
  }

  static boolean DROP_PACKAGES = !"false".equals(System.getProperty("blcoir.dropPackages"));

  /** Terms (graph nodes) for a typed token. Identifiers are split at '.' into their parts. */
  static List<String> terms(Token t, ConceptType type) {
    List<String> out = new ArrayList<>();
    if (type == ConceptType.COE || type == ConceptType.EXC) {
      String w = t.word.replace("()", "").replaceAll("[^\\w$.]", "");
      String[] parts = w.split("\\.");
      // In a qualified name (org.redisson.RedissonTopic.removeAllListeners) the lower-case package
      // segments before the first type name occur in nearly every file of the project, so they are
      // dropped; the type and member names are kept.
      int first = 0;
      if (DROP_PACKAGES && parts.length > 2) {
        while (first < parts.length - 1 && !parts[first].isEmpty()
            && Character.isLowerCase(parts[first].charAt(0))) first++;
      }
      for (String part : java.util.Arrays.copyOfRange(parts, first, parts.length)) {
        String p = part.replace("$", "").toLowerCase(Locale.ROOT);
        if (p.length() >= 2 && !p.matches("\\d+") && !out.contains(p)) out.add(p);
      }
    } else {
      String l = (t.lemma == null ? t.word : t.lemma).toLowerCase(Locale.ROOT);
      l = l.replaceAll("^[^a-z0-9]+|[^a-z0-9]+$", "");
      if (l.length() >= 2) out.add(l);
    }
    return out;
  }
}
