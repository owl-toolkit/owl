package owl.translations.ltl2ldba;

import com.google.common.collect.ImmutableSet;

public final class JumpAnalysisResult<U extends RecurringObligation> {

  public final ImmutableSet<Jump<U>> jumps;
  public final TYPE type;

  private JumpAnalysisResult(TYPE type,
    ImmutableSet<Jump<U>> jumps) {
    this.type = type;
    this.jumps = jumps;
  }

  public static <U extends RecurringObligation> JumpAnalysisResult<U> buildMay(
    Iterable<Jump<U>> jumps) {
    return new JumpAnalysisResult<>(TYPE.MAY, ImmutableSet.copyOf(jumps));
  }

  public static <U extends RecurringObligation> JumpAnalysisResult<U> buildMust(Jump<U> jump) {
    return new JumpAnalysisResult<>(TYPE.MUST, ImmutableSet.of(jump));
  }

  public static <U extends RecurringObligation> JumpAnalysisResult<U> buildTrivial() {
    return new JumpAnalysisResult<>(TYPE.TRIVIAL, ImmutableSet.of());
  }

  enum TYPE {
    TRIVIAL, MAY, MUST
  }
}
