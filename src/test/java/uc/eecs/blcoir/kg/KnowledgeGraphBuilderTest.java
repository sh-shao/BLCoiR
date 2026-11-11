package uc.eecs.blcoir.kg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import org.junit.Test;
import uc.eecs.blcoir.Config;
import uc.eecs.blcoir.nlp.ParsedReport;
import uc.eecs.blcoir.nlp.ParsedReport.Dependency;
import uc.eecs.blcoir.nlp.ParsedReport.Sentence;
import uc.eecs.blcoir.nlp.ParsedReport.Token;
import uc.eecs.blcoir.query.QueryFormulator;
import uc.eecs.blcoir.rank.PageRank;

public class KnowledgeGraphBuilderTest {

  /** Fig. 2: "Deadlock if using separate thread to write to ServletOutputStream". */
  static ParsedReport figure2() {
    Sentence s = new Sentence();
    String[][] toks = {
      {"Deadlock", "deadlock", "NN"}, {"if", "if", "IN"}, {"using", "use", "VBG"},
      {"separate", "separate", "JJ"}, {"thread", "thread", "NN"}, {"to", "to", "TO"},
      {"write", "write", "VB"}, {"to", "to", "IN"}, {"ServletOutputStream", "ServletOutputStream", "NNP"}
    };
    for (int i = 0; i < toks.length; i++) s.tokens.add(new Token(i + 1, toks[i][0], toks[i][1], toks[i][2]));
    int[][] deps = {{0, 1}, {1, 3}, {3, 2}, {3, 5}, {5, 4}, {3, 7}, {7, 6}, {7, 9}, {9, 8}, {7, 5}};
    String[] rels = {"root", "dep", "mark", "obj", "amod", "xcomp", "mark", "obl", "case", "nsubj"};
    for (int i = 0; i < deps.length; i++) s.dependencies.add(new Dependency(deps[i][0], deps[i][1], rels[i]));
    ParsedReport r = new ParsedReport();
    r.sentences.add(s);
    return r;
  }

  @Test
  public void conceptTypes() {
    ConceptLexicon lex = new ConceptLexicon();
    Sentence s = figure2().sentences.get(0);
    assertEquals(ConceptType.CBG, lex.classify(s.token(1)));
    assertNull(lex.classify(s.token(2))); // "if"
    assertEquals(ConceptType.OTV, lex.classify(s.token(3)));
    assertNull(lex.classify(s.token(4))); // "separate" (adjective)
    assertEquals(ConceptType.CME, lex.classify(s.token(5)));
    assertEquals(ConceptType.COE, lex.classify(s.token(9)));
    assertEquals(ConceptType.EXC, lex.classify(new Token(1, "NullPointerException", "NullPointerException", "NN")));
    assertEquals(ConceptType.COP, lex.classify(new Token(1, "acquire", "acquire", "VB")));
    assertEquals(ConceptType.CSY, lex.classify(new Token(1, "hangs", "hang", "VBZ")));
    assertEquals(ConceptType.SYB, lex.classify(new Token(1, "issue", "issue", "NN")));
  }

  @Test
  public void figure3Graph() {
    KnowledgeGraph g = new KnowledgeGraphBuilder().build(figure2());
    assertEquals(5, g.nodes().size()); // deadlock, use, thread, write, servletoutputstream
    assertFalse(g.nodes().contains("if"));
    assertFalse(g.nodes().contains("separate"));
    // dependency edges that survive node removal
    assertTrue(g.hasEdge("deadlock", "use"));
    assertTrue(g.hasEdge("use", "thread"));
    assertTrue(g.hasEdge("write", "servletoutputstream"));
    // CR1 (COE + CBG): the blue edge of Fig. 3
    assertTrue(g.hasEdge("deadlock", "servletoutputstream"));
    // CR3 (CME + CBG)
    assertTrue(g.hasEdge("deadlock", "thread"));
    // CR9 links the concurrency-related concepts with each other
    assertTrue(g.hasEdge("thread", "servletoutputstream"));
  }

  @Test
  public void removalRedirectsPredecessorsToSuccessors() {
    // a -> x -> b with x untyped  ==>  a -> b
    Sentence s = new Sentence();
    s.tokens.add(new Token(1, "lock", "lock", "NN"));
    s.tokens.add(new Token(2, "of", "of", "IN"));
    s.tokens.add(new Token(3, "Foo", "Foo", "NN"));
    s.dependencies.add(new Dependency(1, 2, "dep"));
    s.dependencies.add(new Dependency(2, 3, "dep"));
    java.util.List<int[]> e = KnowledgeGraphBuilder.prunedDependencyEdges(s, java.util.Set.of(1, 3));
    assertEquals(1, e.size());
    assertEquals(1, e.get(0)[0]);
    assertEquals(3, e.get(0)[1]);
  }

  @Test
  public void identifiersAreSplitAtDots() {
    assertEquals(
        java.util.List.of("httpclientimpl", "close"),
        KnowledgeGraphBuilder.terms(new Token(1, "HttpClientImpl.close()", "x", "NN"), ConceptType.COE));
  }

  @Test
  public void pageRankRanksConcurrencyHubFirst() {
    KnowledgeGraph g = new KnowledgeGraphBuilder().build(figure2());
    Map<String, Double> s = new PageRank(0.85, 200, 1e-10).score(g);
    double total = s.values().stream().mapToDouble(Double::doubleValue).sum();
    assertEquals(g.nodes().size(), total, 1e-6); // undirected PR with (1-d) keeps the mass at N
    Config paper = new Config(java.nio.file.Paths.get("."));
    paper.excludeOtherVerbs = false;
    paper.codeEntityBoost = 1.0;
    QueryFormulator.Result r = new QueryFormulator(paper).formulate(figure2());
    // thread is the only node with 4 neighbours; all others have 3
    assertEquals("thread", r.terms.get(0));
    for (String v : g.nodes()) if (!v.equals("thread")) assertTrue(s.get("thread") > s.get(v));
    assertEquals(5, r.terms.size());

    // default selection: code entity boosted to the front, other verbs (use, write) left out
    QueryFormulator.Result d = new QueryFormulator(new Config(java.nio.file.Paths.get("."))).formulate(figure2());
    assertEquals(java.util.List.of("servletoutputstream", "thread", "deadlock"), d.terms);
  }
}
