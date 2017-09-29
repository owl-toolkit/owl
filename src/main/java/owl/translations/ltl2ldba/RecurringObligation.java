package owl.translations.ltl2ldba;

import owl.ltl.EquivalenceClass;

public interface RecurringObligation {
  boolean containsLanguageOf(RecurringObligation obligation);

  EquivalenceClass getLanguage();
}
