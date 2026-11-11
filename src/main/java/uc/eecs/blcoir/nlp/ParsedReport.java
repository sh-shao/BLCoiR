package uc.eecs.blcoir.nlp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CoreNLP-independent view of a parsed bug report: tokens, typed dependencies per sentence, and
 * coreference links. Keeping this separate makes the knowledge-graph code testable without loading
 * CoreNLP models.
 */
public final class ParsedReport {

  public static final class Token {
    /** 1-based index within its sentence (CoreNLP convention). */
    public final int index;
    public final String word;
    public final String lemma;
    public final String pos;

    public Token(int index, String word, String lemma, String pos) {
      this.index = index;
      this.word = word;
      this.lemma = lemma;
      this.pos = pos;
    }

    @Override
    public String toString() {
      return word + "/" + pos;
    }
  }

  public static final class Dependency {
    public final int governor; // token index, 0 = ROOT
    public final int dependent;
    public final String relation; // UD relation, e.g. nsubj, obj, obl:to

    public Dependency(int governor, int dependent, String relation) {
      this.governor = governor;
      this.dependent = dependent;
      this.relation = relation;
    }

    @Override
    public String toString() {
      return relation + "(" + governor + "," + dependent + ")";
    }
  }

  public static final class Sentence {
    public final List<Token> tokens = new ArrayList<>();
    public final List<Dependency> dependencies = new ArrayList<>();

    public Token token(int index) {
      return tokens.get(index - 1);
    }
  }

  /** A position in the report: sentence (0-based) and token index (1-based). */
  public static final class TokenRef {
    public final int sentence;
    public final int token;

    public TokenRef(int sentence, int token) {
      this.sentence = sentence;
      this.token = token;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof TokenRef
          && ((TokenRef) o).sentence == sentence
          && ((TokenRef) o).token == token;
    }

    @Override
    public int hashCode() {
      return sentence * 31 + token;
    }
  }

  public final List<Sentence> sentences = new ArrayList<>();

  /**
   * Coreference: head token of an anaphoric mention -> head token of the representative mention of
   * its chain (e.g. "its" -> "XsltBuilder").
   */
  public final Map<TokenRef, TokenRef> corefs = new HashMap<>();
}
