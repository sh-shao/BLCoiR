package uc.eecs.blcoir.data;

import java.util.List;

public final class BugReport {
  public final Project project;
  public final int id;
  /** Bug report text without stack traces (BRText/): first line is the summary. */
  public final String text;
  /** Ground truth: source files changed by the fix (relative paths). */
  public final List<String> goldFiles;
  /** PE, NL, STP or STN (see categories.tsv); may be null if unknown. */
  public final String category;

  public BugReport(Project project, int id, String text, List<String> goldFiles, String category) {
    this.project = project;
    this.id = id;
    this.text = text;
    this.goldFiles = goldFiles;
    this.category = category;
  }

  /** Paper grouping: Dataset_PE = PE + STP, Dataset_NL = NL + STN. */
  public String quality() {
    if (category == null) return "?";
    return category.equals("PE") || category.equals("STP") ? "PE" : "NL";
  }

  public boolean hasStackTrace() {
    return "STP".equals(category) || "STN".equals(category);
  }
}
