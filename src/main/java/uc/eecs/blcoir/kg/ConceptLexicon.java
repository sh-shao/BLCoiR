package uc.eecs.blcoir.kg;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import uc.eecs.blcoir.nlp.ParsedReport.Token;

/**
 * Concept Type Modeling (Section III-C, Table III): assigns one of the nine concept types to a
 * token, or {@code null} when the token belongs to none (and is later removed from the graph).
 *
 * <p>Only nouns and verbs are considered (Java naming conventions: classes/fields are nouns, methods
 * are verbs). Code entities and exceptions are recognised from their identifier shape regardless of
 * the POS tag CoreNLP assigns, because tagging of identifiers is unreliable.
 */
public final class ConceptLexicon {

  /** CBG: terms that name concurrency faults. */
  static final Set<String> CBG =
      set(
          "deadlock", "dead-lock", "livelock", "live-lock", "race", "racing", "datarace",
          "starvation", "atomicity", "violation", "contention", "inconsistency", "lockup",
          "interleaving", "reentrancy", "hang", "hangs");

  /** CME: concurrency programming mechanisms. */
  static final Set<String> CME =
      set(
          "lock", "locks", "locking", "thread", "threads", "threading", "multithreading",
          "multi-thread", "multithread", "writelock", "readlock", "mutex", "semaphore", "monitor",
          "latch", "barrier", "executor", "executorservice", "threadpool", "pool", "future",
          "promise", "synchronization", "synchronized", "synchronizer", "concurrency", "concurrent",
          "concurrently", "parallel", "parallelism", "atomic", "volatile", "condition", "eventloop",
          "worker", "scheduler", "fork", "forkjoin", "spinlock", "critical", "thread-safe",
          "threadsafe", "safety", "reentrantlock", "countdownlatch", "cyclicbarrier", "async",
          "asynchronous", "synchronous", "callback", "notify", "notifyall", "wait", "interrupt",
          "daemon", "coroutine", "actor", "cas", "memory-barrier", "happens-before", "visibility");

  /** Stems: any (non-identifier) noun containing one of these is treated as CME (e.g. writelock). */
  static final List<String> CME_STEMS =
      Arrays.asList("lock", "thread", "mutex", "semaphore", "executor", "latch", "synchron",
          "concurren", "atomic", "volatile");

  /** SYB: synonyms of bug. */
  static final Set<String> SYB =
      set(
          "bug", "issue", "problem", "error", "fault", "failure", "defect", "crash", "exception",
          "flaw", "glitch", "regression", "mistake", "incorrectness", "corruption", "leak",
          "timeout", "npe", "oom");

  /** COP: verbs describing concurrency operations. */
  static final Set<String> COP =
      set(
          "lock", "unlock", "fork", "join", "release", "acquire", "wait", "notify", "notifyall",
          "signal", "await", "synchronize", "spawn", "interrupt", "schedule", "submit", "execute",
          "park", "unpark", "yield", "sleep", "suspend", "resume", "parallelize", "dispatch",
          "lock-free", "reenter", "preempt", "serialize", "start", "terminate", "shutdown");

  /** CSY: verbs describing concurrency symptoms. */
  static final Set<String> CSY =
      set(
          "hang", "block", "freeze", "stall", "stick", "starve", "deadlock", "race", "spin",
          "livelock", "timeout", "time-out", "lockup", "stuck", "halt", "wedge", "leak", "corrupt",
          "overwrite", "interleave", "contend");

  /** Verbs that carry no domain information (auxiliaries / copula). */
  static final Set<String> AUX = set("be", "'s", "'re", "'m");

  // ---- identifier shapes -------------------------------------------------------------------

  /** NullPointerException, IllegalStateException, OutOfMemoryError, ... */
  private static final Pattern EXCEPTION =
      Pattern.compile("[A-Za-z_$][\\w$]*[a-z0-9](Exception|Error|Throwable)s?");
  /** camelCase methods/fields: getConnection, handleClosed */
  private static final Pattern CAMEL = Pattern.compile("[a-z][a-z0-9]*([A-Z][a-z0-9]*)+");
  /** PascalCase with at least two humps: HttpClientImpl, ThreadPool */
  private static final Pattern PASCAL = Pattern.compile("([A-Z][a-z0-9]+){2,}[A-Za-z0-9]*|[A-Z]+[a-z0-9]+[A-Z][A-Za-z0-9]*");
  /** snake_case / CONSTANT_CASE */
  private static final Pattern SNAKE = Pattern.compile("[A-Za-z][A-Za-z0-9]*(_[A-Za-z0-9]+)+");
  /** qualified name or call: a.b.C, Foo.bar(), lock() */
  private static final Pattern QUALIFIED =
      Pattern.compile("[A-Za-z_$][\\w$]*(\\.[A-Za-z_$][\\w$]*)+(\\(\\))?|[A-Za-z_$][\\w$]*\\(\\)");

  private static Set<String> set(String... words) {
    return new HashSet<>(Arrays.asList(words));
  }

  public static boolean isException(String word) {
    return EXCEPTION.matcher(word).matches();
  }

  public static boolean isCodeEntity(String word) {
    return CAMEL.matcher(word).matches()
        || PASCAL.matcher(word).matches()
        || SNAKE.matcher(word).matches()
        || QUALIFIED.matcher(word).matches();
  }

  /** Returns the concept type of a token, or null if it is not a concept (removed from DenGraph). */
  public ConceptType classify(Token t) {
    String word = t.word;
    if (word == null || word.isEmpty()) return null;
    if (!word.matches(".*[A-Za-z].*")) return null; // punctuation, numbers

    if (isException(word)) return ConceptType.EXC;
    if (isCodeEntity(word)) return ConceptType.COE;

    String lemma = (t.lemma == null ? word : t.lemma).toLowerCase(Locale.ROOT);
    if (lemma.length() < 2 || lemma.matches(".*\\d.*")) return null;
    String pos = t.pos == null ? "" : t.pos;

    if (pos.startsWith("NN")) {
      if (CBG.contains(lemma)) return ConceptType.CBG;
      if (CME.contains(lemma) || containsStem(lemma)) return ConceptType.CME;
      if (SYB.contains(lemma)) return ConceptType.SYB;
      return ConceptType.OTN;
    }
    if (pos.startsWith("VB")) {
      if (AUX.contains(lemma)) return null;
      if (CSY.contains(lemma)) return ConceptType.CSY;
      if (COP.contains(lemma)) return ConceptType.COP;
      return ConceptType.OTV;
    }
    // Adjectives / adverbs that name a concurrency mechanism ("concurrent", "atomic",
    // "thread-safe", "synchronized") or a fault ("racy") carry the same domain meaning as the noun.
    if (pos.startsWith("JJ") || pos.startsWith("RB")) {
      if (CBG.contains(lemma)) return ConceptType.CBG;
      if (CME.contains(lemma) || containsStem(lemma)) return ConceptType.CME;
    }
    return null;
  }

  private static boolean containsStem(String lemma) {
    for (String s : CME_STEMS) if (lemma.contains(s)) return true;
    return false;
  }
}
