package uc.eecs.blcoir.data;

/**
 * One subject project, identified by its path relative to {@code Dataset/}, e.g. {@code
 * GitHub/vertx} or {@code Bench4BL/Apache/CAMEL}.
 */
public final class Project implements Comparable<Project> {
  public final String id;

  public Project(String id) {
    this.id = id.replace('\\', '/');
  }

  /** The source dataset: BLIZZARD, LtR, Bench4BL or GitHub. */
  public String dataset() {
    return id.substring(0, id.indexOf('/'));
  }

  public String name() {
    return id.substring(id.lastIndexOf('/') + 1);
  }

  @Override
  public int compareTo(Project o) {
    return id.compareTo(o.id);
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Project && ((Project) o).id.equals(id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return id;
  }
}
