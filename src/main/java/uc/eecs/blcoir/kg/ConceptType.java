package uc.eecs.blcoir.kg;

/** The nine concept types of Table III. */
public enum ConceptType {
  // nouns
  /** Concurrency Bug: deadlock, livelock, race, ... */
  CBG,
  /** Concurrency Mechanism: lock, thread, writelock, ... */
  CME,
  /** Synonym for Bug: bug, issue, problem, error, ... */
  SYB,
  /** Code Entity: class and method names, lock(), unlock(), ... */
  COE,
  /** Exceptions: NullPointerException, ... */
  EXC,
  /** Other Nouns. */
  OTN,
  // verbs
  /** Concurrency Operation: lock, fork, release, ... */
  COP,
  /** Concurrency Symptom: hang, block, freeze, ... */
  CSY,
  /** Other Verbs. */
  OTV;

  /** SYB | EXC | CSY : the "bug-indicating" side of CR2, CR4, CR6. */
  public boolean isBugIndicator() {
    return this == SYB || this == EXC || this == CSY;
  }
}
