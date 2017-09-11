package owl.translations.ltl2ldba;

import com.google.common.collect.ImmutableSet;

final class AnalysisResult<U extends RecurringObligation> {
  enum TYPE {
    MAY, MUST
  }

  final ImmutableSet<Jump<U>> jumps;
  final TYPE type;

  private AnalysisResult(TYPE type, ImmutableSet<Jump<U>> jumps) {
    this.type = type;
    this.jumps = jumps;
  }

  static <U extends RecurringObligation> AnalysisResult<U> buildMay(Iterable<Jump<U>> jumps) {
    return new AnalysisResult<>(TYPE.MAY, ImmutableSet.copyOf(jumps));
  }

  static <U extends RecurringObligation> AnalysisResult<U> buildMust(Jump<U> jump) {
    return new AnalysisResult<>(TYPE.MUST, ImmutableSet.of(jump));
  }
}
