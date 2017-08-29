package owl.translations.ldba2dpa;

public interface Language<T> {
  default void free() {
    // Default implementation does nothing.
  }

  T getT();

  boolean greaterOrEqual(Language<T> language);

  boolean isBottom();

  boolean isTop();

  Language<T> join(Language<T> language);

  default boolean lessOrEqual(Language<T> language) {
    return language.greaterOrEqual(this);
  }
}
