package uc.eecs.blcoir.eval;

import java.util.List;
import java.util.Locale;

/** Top@K, MRR and MAP (Section IV-A4) over a set of bug reports. */
public final class Metrics {
  public int n;
  public int top1, top5, top10;
  public double sumRR, sumAP;
  /** Cut-off at K = 10, as in the paper's tables (Fig. 4 shows MRR/MAP for different K). */
  public double sumRR10, sumAP10;

  /** @param ranks 1-based ranks of the ground-truth files retrieved for one bug report. */
  public void add(List<Integer> ranks) {
    n++;
    if (ranks.isEmpty()) return;
    int first = ranks.get(0);
    if (first <= 1) top1++;
    if (first <= 5) top5++;
    if (first <= 10) top10++;
    sumRR += 1.0 / first;
    sumAP += averagePrecision(ranks);
    if (first <= 10) sumRR10 += 1.0 / first;
    sumAP10 += averagePrecisionAt(ranks, 10);
  }

  /** AvgP = 1/|K| * sum_{k in K} Prec@k, with K the positions of the relevant files. */
  public static double averagePrecision(List<Integer> ranks) {
    if (ranks.isEmpty()) return 0;
    double s = 0;
    for (int i = 0; i < ranks.size(); i++) s += (i + 1) / (double) ranks.get(i);
    return s / ranks.size();
  }

  /** Precision summed over relevant files ranked within K, divided by the number found overall. */
  public static double averagePrecisionAt(List<Integer> ranks, int k) {
    if (ranks.isEmpty()) return 0;
    double s = 0;
    for (int i = 0; i < ranks.size() && ranks.get(i) <= k; i++) s += (i + 1) / (double) ranks.get(i);
    return s / ranks.size();
  }

  public void merge(Metrics o) {
    n += o.n;
    top1 += o.top1;
    top5 += o.top5;
    top10 += o.top10;
    sumRR += o.sumRR;
    sumAP += o.sumAP;
    sumRR10 += o.sumRR10;
    sumAP10 += o.sumAP10;
  }

  public double mrr() {
    return n == 0 ? 0 : sumRR / n;
  }

  public double map() {
    return n == 0 ? 0 : sumAP / n;
  }

  private String pct(int k) {
    return String.format(Locale.ROOT, "%d (%.2f%%)", k, n == 0 ? 0 : 100.0 * k / n);
  }

  public static String header() {
    return "| Subset | #BR | Top1 | Top5 | Top10 | MRR | MAP | MRR@10 | MAP@10 |\n"
        + "|---|---:|---:|---:|---:|---:|---:|---:|---:|";
  }

  public String row(String label) {
    return String.format(
        Locale.ROOT, "| %s | %d | %s | %s | %s | %.4f | %.4f | %.4f | %.4f |",
        label, n, pct(top1), pct(top5), pct(top10), mrr(), map(),
        n == 0 ? 0 : sumRR10 / n, n == 0 ? 0 : sumAP10 / n);
  }
}
