package owl.translations.ldba2dpa;

public interface LanguageLattice<S, T, U> {
  Language<S> getBottom();

  Language<S> getLanguage(T state, boolean current);

  Language<S> getTop();

  boolean isLivenessLanguage(U annotation);

  boolean isSafetyLanguage(U annotation);
}