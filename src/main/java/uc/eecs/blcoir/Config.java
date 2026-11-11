package uc.eecs.blcoir;

import java.nio.file.Path;
import java.nio.file.Paths;

/** Paths and hyper-parameters. Defaults follow the paper (Section III-D, IV-B2). */
public final class Config {

  /** Repository root; everything below is resolved against it. Override with -Dblcoir.home=... */
  public final Path home;

  /** Query length L. The paper selects L = 32 (Fig. 5). */
  public int queryLength = 32;

  /** PageRank damping factor d (Section III-D). */
  public double damping = 0.85;

  public int pageRankMaxIter = 200;
  public double pageRankEpsilon = 1e-8;

  /**
   * Query-selection refinements (not specified in the paper; chosen on the even-id tune split and
   * checked on the odd-id held-out split). Disable both with --paper-selection.
   */
  public boolean excludeOtherVerbs = true;

  public double codeEntityBoost = 2.0;

  /** Whether to run CoreNLP coreference resolution (Algorithm 1, line 8). */
  public boolean coref = true;

  /** Which report text queries are built from: "brtext" (BRText/, default) or "br" (Dataset/.../BR). */
  public String reportSource = "brtext";

  /** Reports longer than this (characters) are truncated before parsing, to bound CoreNLP time. */
  public int maxReportChars = 12000;

  public Config(Path home) {
    this.home = home.toAbsolutePath().normalize();
  }

  public static Config fromSystem() {
    return new Config(Paths.get(System.getProperty("blcoir.home", ".")));
  }

  public Path datasetDir() {
    return home.resolve("Dataset");
  }

  public Path brTextDir() {
    return home.resolve("BRText");
  }

  public Path indexDir() {
    return home.resolve("Lucene").resolve("Lucene_Index");
  }

  public Path indexMappingDir() {
    return home.resolve("Lucene").resolve("Lucene-Index2File-Mapping");
  }
}
