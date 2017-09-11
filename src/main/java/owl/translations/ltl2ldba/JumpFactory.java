package owl.translations.ltl2ldba;

import owl.ltl.EquivalenceClass;

public interface JumpFactory<U extends RecurringObligation> {
  JumpAnalysisResult<U> getAvailableJumps(EquivalenceClass state);
}
