package owl.translations.fgx2dpa;

import java.util.List;
import java.util.Map;
import org.immutables.value.Value;
import owl.collections.ValuationSet;
import owl.ltl.Formula;
import owl.util.annotation.HashedTuple;

@Value.Immutable
@HashedTuple
abstract class StateReduction {
  abstract Formula formula();

  abstract List<PromisedSet> permutation();

  abstract Map<State, ValuationSet> successors();


  static StateReduction of(State state, Map<State, ValuationSet> successors) {
    return StateReductionTuple.create(state.formula(), state.permutation(), successors);
  }
}
