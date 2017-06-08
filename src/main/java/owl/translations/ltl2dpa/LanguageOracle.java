package owl.translations.ltl2dpa;

public interface LanguageOracle<S, T, U> {

  Language<S> getLanguage(T state, boolean current);

  Language<S> getEmpty();

  Language<S> getUniverse();

  boolean isPureLiveness(U obligations);

  boolean isPureSafety(U obligations);
}
