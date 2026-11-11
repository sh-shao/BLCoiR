package uc.eecs.blcoir.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import uc.eecs.blcoir.Config;

/** Loads projects, bug report texts, ground truth and categories from the repository layout. */
public final class DatasetLoader {
  private static final Pattern CHANGESET_ENTRY =
      Pattern.compile("<id>\\s*(\\d+)\\s*<files>(.*?)</files>\\s*</id>", Pattern.DOTALL);

  private final Config config;
  private final Map<String, String> categories; // "project\tid" -> category

  public DatasetLoader(Config config) {
    this.config = config;
    this.categories = loadCategories();
  }

  /** All projects = directories under Dataset/ that contain a changeset.xml. */
  public List<Project> projects() throws IOException {
    Path root = config.datasetDir();
    try (Stream<Path> s = Files.walk(root, 4)) {
      return s.filter(p -> p.getFileName().toString().equals("changeset.xml"))
          .map(p -> new Project(root.relativize(p.getParent()).toString()))
          .sorted()
          .collect(Collectors.toList());
    }
  }

  public List<Project> projects(String filter) throws IOException {
    List<Project> all = projects();
    if (filter == null || filter.isEmpty()) return all;
    List<Project> out = new ArrayList<>();
    for (Project p : all) {
      for (String f : filter.split(",")) {
        f = f.trim();
        if (p.id.equals(f) || p.id.startsWith(f + "/") || p.name().equalsIgnoreCase(f)) {
          out.add(p);
          break;
        }
      }
    }
    return out;
  }

  public List<BugReport> bugReports(Project project) throws IOException {
    Map<Integer, List<String>> gold = changeset(project);
    // "brtext" (default): report text without stack traces, as in the paper; "br": full report
    Path textDir =
        "br".equals(config.reportSource)
            ? config.datasetDir().resolve(project.id).resolve("BR")
            : config.brTextDir().resolve(project.id);
    List<BugReport> out = new ArrayList<>();
    for (Map.Entry<Integer, List<String>> e : gold.entrySet()) {
      int id = e.getKey();
      Path f = textDir.resolve(id + ".txt");
      if (!Files.exists(f)) f = config.datasetDir().resolve(project.id).resolve("BR").resolve(id + ".txt");
      if (!Files.exists(f)) continue;
      String text = new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
      out.add(new BugReport(project, id, text, e.getValue(), categories.get(project.id + "\t" + id)));
    }
    return out;
  }

  /** Parses Dataset/<project>/changeset.xml : bug id -> fixed files. */
  public Map<Integer, List<String>> changeset(Project project) throws IOException {
    Path f = config.datasetDir().resolve(project.id).resolve("changeset.xml");
    String xml = new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
    Map<Integer, List<String>> out = new TreeMap<>();
    Matcher m = CHANGESET_ENTRY.matcher(xml);
    while (m.find()) {
      int id = Integer.parseInt(m.group(1));
      List<String> files = out.computeIfAbsent(id, k -> new ArrayList<>());
      for (String line : m.group(2).split("\\R")) {
        String path = line.trim().replace('\\', '/');
        if (path.length() > 2 && !files.contains(path)) files.add(path);
      }
    }
    return out;
  }

  private static Map<String, String> loadCategories() {
    Map<String, String> out = new HashMap<>();
    InputStream in = DatasetLoader.class.getResourceAsStream("/categories.tsv");
    if (in == null) return out;
    try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      String line;
      while ((line = r.readLine()) != null) {
        if (line.startsWith("#") || line.isBlank()) continue;
        String[] p = line.split("\t");
        out.put(p[0] + "\t" + p[1], p[2]);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return out;
  }
}
