package owl.translations.ltl2ldba;

import owl.ltl.EquivalenceClass;

public interface RecurringObligation {
  EquivalenceClass getLanguage();

  boolean containsLanguageOf(RecurringObligation obligation);
}
