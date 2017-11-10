package owl.translations.ldba2dpa;

public interface LanguageLattice<S, T, U> {
  Language<S> getBottom();

  Language<S> getLanguage(T state);

  Language<S> getTop();

  boolean isLivenessLanguage(U annotation);

  boolean isSafetyAnnotation(U annotation);

  boolean acceptsSafetyLanguage(T state);

  boolean acceptsLivenessLanguage(T state);
}