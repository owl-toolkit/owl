package owl.translations.ltl2ldba;

import java.util.Collection;
import java.util.Set;

final class AnalysisResult<U extends RecurringObligation> {
  final Set<Jump<U>> jumps;
  final TYPE type;

  private AnalysisResult(TYPE type, Set<Jump<U>> jumps) {
    this.type = type;
    this.jumps = jumps;
  }

  static <U extends RecurringObligation> AnalysisResult<U> buildMay(Collection<Jump<U>> jumps) {
    return new AnalysisResult<>(TYPE.MAY, Set.copyOf(jumps));
  }

  static <U extends RecurringObligation> AnalysisResult<U> buildMust(Jump<U> jump) {
    return new AnalysisResult<>(TYPE.MUST, Set.of(jump));
  }

  enum TYPE {
    MAY, MUST
  }
}
