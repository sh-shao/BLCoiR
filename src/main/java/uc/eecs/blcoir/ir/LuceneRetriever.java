package uc.eecs.blcoir.ir;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.FSDirectory;
import uc.eecs.blcoir.Config;
import uc.eecs.blcoir.data.Project;

/**
 * Retrieval and Ranking (Section III-E) with Lucene (BM25, StandardAnalyzer) over the corpus indexes
 * shipped in Lucene/Lucene_Index. Index documents are numbered files ("123.java"); the mapping in
 * Lucene/Lucene-Index2File-Mapping translates them back to source paths.
 */
public final class LuceneRetriever implements Closeable {
  private static final int MAX_HITS = 100_000;

  private final IndexReader reader;
  private final IndexSearcher searcher;
  private final QueryParser parser;
  private final Map<String, String> docToPath = new HashMap<>();

  public LuceneRetriever(Config config, Project project) throws IOException {
    Path index = config.indexDir().resolve(project.id);
    this.reader = DirectoryReader.open(FSDirectory.open(index));
    this.searcher = new IndexSearcher(reader);
    this.parser = new QueryParser("contents", new StandardAnalyzer());
    BooleanQuery.setMaxClauseCount(Math.max(BooleanQuery.getMaxClauseCount(), 8192));
    Path mapping = config.indexMappingDir().resolve(project.id + ".txt");
    for (String line : Files.readAllLines(mapping, StandardCharsets.UTF_8)) {
      int c = line.indexOf(':');
      if (c <= 0) continue;
      docToPath.put(line.substring(0, c).trim() + ".java", line.substring(c + 1).trim().replace('\\', '/'));
    }
  }

  /** Ranked list of source file paths (best first). */
  public List<String> search(String queryText) throws IOException {
    List<String> out = new ArrayList<>();
    // lower-case so that words such as AND / OR / NOT are never read as operators
    String q = queryText == null ? "" : queryText.trim().toLowerCase(java.util.Locale.ROOT);
    if (q.isEmpty()) return out;
    Query query;
    try {
      query = parser.parse(QueryParser.escape(q));
    } catch (ParseException e) {
      throw new IOException("cannot parse query: " + q, e);
    }
    for (ScoreDoc sd : searcher.search(query, MAX_HITS).scoreDocs) {
      String path = searcher.doc(sd.doc).get("path");
      if (path == null) continue;
      String key = Paths.get(path.replace('\\', '/')).getFileName().toString();
      String src = docToPath.get(key);
      if (src != null) out.add(src);
    }
    return out;
  }

  /** Number of indexed documents containing the (analysed) term; for diagnostics. */
  public int docFreq(String term) throws IOException {
    return reader.docFreq(new org.apache.lucene.index.Term("contents", term.toLowerCase(java.util.Locale.ROOT)));
  }

  public int numDocs() {
    return reader.numDocs();
  }

  /** 1-based ranks of every ground-truth file found in the result list. */
  public static List<Integer> goldRanks(List<String> ranked, List<String> gold) {
    List<Integer> ranks = new ArrayList<>();
    for (int i = 0; i < ranked.size(); i++) {
      String path = ranked.get(i);
      for (String g : gold) {
        if (path.endsWith("/" + g) || path.equals(g)) {
          ranks.add(i + 1);
          break;
        }
      }
    }
    return ranks;
  }

  @Override
  public void close() throws IOException {
    reader.close();
  }
}
