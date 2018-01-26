package owl.translations.ldba2dpa;

public interface LanguageLattice<S, A, L> {
  Language<L> getBottom();

  Language<L> getLanguage(S state);

  Language<L> getTop();

  boolean isLivenessLanguage(A annotation);

  boolean isSafetyAnnotation(A annotation);

  boolean acceptsSafetyLanguage(S state);

  boolean acceptsLivenessLanguage(S state);
}