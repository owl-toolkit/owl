package owl.translations.frequency;

import java.util.BitSet;
import java.util.Objects;
import javax.annotation.Nonnull;
import owl.ltl.EquivalenceClass;

public abstract class EquivalenceClassState<STATE extends AutomatonState<STATE>>
  implements AutomatonState<STATE> {
  public final boolean eager;
  final EquivalenceClass equivalenceClass;

  EquivalenceClassState(EquivalenceClass equivalenceClass, boolean eager) {
    this.equivalenceClass = equivalenceClass;
    this.eager = eager;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    EquivalenceClassState<?> other = (EquivalenceClassState<?>) o;
    return Objects.equals(equivalenceClass, other.equivalenceClass);
  }

  public EquivalenceClass getEquivalenceClass() {
    return equivalenceClass;
  }

  @Override
  @Nonnull
  public BitSet getSensitiveAlphabet() {
    if (!eager) {
      return equivalenceClass.unfold().atomicPropositions();
    }
    return equivalenceClass.atomicPropositions();
  }

  @Override
  public int hashCode() {
    return equivalenceClass.hashCode();
  }

  @Override
  public String toString() {
    return equivalenceClass.toString();
  }
}