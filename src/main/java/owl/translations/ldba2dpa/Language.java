package owl.translations.ldba2dpa;

public interface Language<L> {
  default void free() {
    // Default implementation does nothing.
  }

  L getT();

  boolean greaterOrEqual(Language<L> language);

  boolean isBottom();

  boolean isTop();

  Language<L> join(Language<L> language);

  default boolean lessOrEqual(Language<L> language) {
    return language.greaterOrEqual(this);
  }
}
