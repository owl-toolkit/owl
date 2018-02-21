package owl.translations.dra2dpa;

import de.tum.in.naturals.IntPreOrder;
import org.immutables.value.Value;
import owl.automaton.util.AnnotatedState;
import owl.util.annotation.HashedTuple;

@Value.Immutable
@HashedTuple
public abstract class IARState<R> implements AnnotatedState<R> {
  @Override
  public abstract R state();

  public abstract IntPreOrder record();


  public static <R> IARState<R> active(R originalState, IntPreOrder record) {
    return IARStateTuple.create(originalState, record);
  }

  public static <R> IARState<R> trivial(R originalState) {
    return IARStateTuple.create(originalState, IntPreOrder.empty());
  }


  @Override
  public String toString() {
    if (record().size() == 0) {
      return String.format("{%s}", state());
    }
    return String.format("{%s|%s}", state(), record());
  }
}
