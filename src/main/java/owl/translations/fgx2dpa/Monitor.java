package owl.translations.fgx2dpa;

import com.google.common.collect.Sets;

import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;

import org.immutables.value.Value;

import owl.ltl.Formula;
import owl.ltl.UnaryModalOperator;
import owl.util.annotation.HashedTuple;

@Value.Immutable
@HashedTuple
public abstract class Monitor<F extends UnaryModalOperator> {
  abstract F formula();

  abstract Set<Formula> currentTokens();

  @Value.Derived
  @Value.Auxiliary
  Set<Formula> finalStates() {
    return Sets.filter(currentTokens(), t ->
      t.accept(SafetyAutomaton.FinalStateVisitor.INSTANCE));
  }

  @Value.Derived
  @Value.Auxiliary
  Set<Formula> nonFinalStates() {
    return Sets.difference(currentTokens(), finalStates());
  }


  public static <F extends UnaryModalOperator> Monitor<F>
  of(F formula, Set<Formula> currentTokens) {
    return MonitorTuple.create(formula, currentTokens);
  }

  public static <F extends UnaryModalOperator> Monitor<F> of(F formula) {
    return of(formula, Set.of(formula.operand));
  }

  public Monitor<F> temporalStep(BitSet valuation) {
    Set<Formula> currentTokens = new HashSet<>(Set.of(formula().operand));
    nonFinalStates().forEach(t -> currentTokens.add(t.temporalStep(valuation)));
    return of(formula(), currentTokens);
  }
}
