package owl.translations.rabinizer;

import java.util.Arrays;
import javax.annotation.concurrent.Immutable;
import owl.ltl.EquivalenceClass;

@Immutable
final class MonitorState {
  final EquivalenceClass[] formulaRanking;
  private final int hashCode;

  MonitorState(EquivalenceClass[] formulaRanking) {
    //noinspection AssignmentToCollectionOrArrayFieldFromParameter
    this.formulaRanking = formulaRanking;
    this.hashCode = Arrays.hashCode(formulaRanking);
  }

  @Override
  public String toString() {
    return Arrays.toString(formulaRanking);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof MonitorState)) {
      return false;
    }

    MonitorState that = (MonitorState) o;
    return Arrays.equals(formulaRanking, that.formulaRanking);
  }

  @Override
  public int hashCode() {
    assert hashCode == Arrays.hashCode(formulaRanking);
    return hashCode;
  }
}
