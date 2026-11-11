package uc.eecs.blcoir.nlp;

import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.coref.data.Dictionaries;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.CoreMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Bug Report Parsing (Section III-B): sentence splitting, POS tagging, lemmatisation (key entity
 * extraction), dependency parsing, and coreference resolution, using Stanford CoreNLP.
 */
public final class BugReportParser {

  private static final Pattern URL = Pattern.compile("(https?|ftp)://\\S+");
  /** Stack-trace frames / log lines that occasionally remain in the text-only reports. */
  private static final Pattern TRACE_LINE =
      Pattern.compile("(?m)^\\s*(at\\s+[\\w$.]+\\(.*\\)|-\\s+[\\w$.]+\\(.*\\)\\s*@bci.*|\\.{3,}|\\.\\s*)$");

  private final StanfordCoreNLP pipeline;
  private final boolean coref;
  private final int maxChars;

  public BugReportParser(boolean coref, int maxChars) {
    this.coref = coref;
    this.maxChars = maxChars;
    Properties props = new Properties();
    props.setProperty(
        "annotators",
        coref ? "tokenize,ssplit,pos,lemma,ner,depparse,coref" : "tokenize,ssplit,pos,lemma,depparse");
    // keep identifiers such as HttpClientImpl.close() or Vert.x in one token as far as possible
    props.setProperty("tokenize.options", "untokenizable=noneDelete,ptb3Escaping=false");
    // each line of a report is treated as a paragraph boundary (titles have no final period)
    props.setProperty("ssplit.newlineIsSentenceBreak", "always");
    props.setProperty("depparse.extradependencies", "NONE");
    if (coref) {
      props.setProperty("coref.algorithm", "statistical");
      props.setProperty("coref.md.type", "dependency");
      props.setProperty("ner.applyFineGrained", "false");
      props.setProperty("ner.useSUTime", "false");
      props.setProperty("ner.buildEntityMentions", "true");
      props.setProperty("coref.maxMentionDistance", "50");
    }
    this.pipeline = new StanfordCoreNLP(props);
  }

  public static String clean(String text, int maxChars) {
    String t = URL.matcher(text).replaceAll(" ");
    t = TRACE_LINE.matcher(t).replaceAll("");
    if (t.length() > maxChars) t = t.substring(0, maxChars);
    return t;
  }

  public ParsedReport parse(String rawText) {
    Annotation doc = new Annotation(clean(rawText, maxChars));
    pipeline.annotate(doc);

    ParsedReport out = new ParsedReport();
    List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);
    for (CoreMap s : sentences) {
      ParsedReport.Sentence ps = new ParsedReport.Sentence();
      for (CoreLabel tok : s.get(CoreAnnotations.TokensAnnotation.class)) {
        ps.tokens.add(new ParsedReport.Token(tok.index(), tok.word(), tok.lemma(), tok.tag()));
      }
      SemanticGraph g = s.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
      if (g != null) {
        for (IndexedWord root : g.getRoots()) {
          ps.dependencies.add(new ParsedReport.Dependency(0, root.index(), "root"));
        }
        for (SemanticGraphEdge e : g.edgeIterable()) {
          ps.dependencies.add(
              new ParsedReport.Dependency(
                  e.getGovernor().index(), e.getDependent().index(), e.getRelation().toString()));
        }
      }
      out.sentences.add(ps);
    }

    if (coref) {
      Map<Integer, CorefChain> chains = doc.get(CorefCoreAnnotations.CorefChainAnnotation.class);
      if (chains != null) {
        for (CorefChain chain : chains.values()) {
          CorefChain.CorefMention rep = chain.getRepresentativeMention();
          if (rep == null || rep.mentionType == Dictionaries.MentionType.PRONOMINAL) continue;
          ParsedReport.TokenRef repRef = new ParsedReport.TokenRef(rep.sentNum - 1, rep.headIndex);
          for (CorefChain.CorefMention m : chain.getMentionsInTextualOrder()) {
            if (m == rep || m.mentionType != Dictionaries.MentionType.PRONOMINAL) continue;
            out.corefs.put(new ParsedReport.TokenRef(m.sentNum - 1, m.headIndex), repRef);
          }
        }
      }
    }
    return out;
  }
}
