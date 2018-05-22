package owl.translations.fgx2dpa;

import java.util.List;
import java.util.Set;

import org.immutables.value.Value;
import owl.ltl.BooleanConstant;
import owl.ltl.Formula;
import owl.util.annotation.HashedTuple;

@Value.Immutable
@HashedTuple
abstract class State {
  abstract Formula formula();

  abstract Set<Monitor> monitorsG();

  abstract Set<Monitor> monitorsF();

  abstract int priority();

  abstract List<PromisedSet> permutation();

  public static State of(Formula formula, Set<Monitor> monitorsG, Set<Monitor> monitorsF,
                         int priority, List<PromisedSet> permutation) {
    return StateTuple.create(formula, monitorsG, monitorsF, priority, permutation);
  }

  static State of(BooleanConstant constant) {
    int priority = constant.equals(BooleanConstant.TRUE) ? 2 : 3;
    return of(constant, Set.of(), Set.of(), priority,
      List.of(PromisedSet.of(Set.of(), List.of(),constant)));
  }
}
