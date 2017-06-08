package owl.translations.ltl2dpa;

public interface Language<S> {
  Language<S> union(Language<S> l);

  boolean contains(Language<S> l);

  boolean isUniverse();

  boolean isEmpty();

  void free();

  boolean isSafetyLanguage();

  boolean isCosafetyLanguage();

  S getLanguageObject();
}
