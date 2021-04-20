package owl.automaton.symbolic;

import static owl.automaton.symbolic.SymbolicAutomaton.VariableType.ATOMIC_PROPOSITION;
import static owl.automaton.symbolic.SymbolicAutomaton.VariableType.STATE;

import java.util.BitSet;
import java.util.Optional;
import owl.bdd.BddSet;
import owl.collections.BitSet2;

public class NaiveStrategyDeterminizer implements StrategyDeterminizer {

  @Override
  public BddSet determinize(
    SymbolicAutomaton<?> automaton,
    BitSet controllableAps,
    BddSet strategy
  ) {
    BitSet inputs = automaton.variableAllocation()
      .variables(STATE, ATOMIC_PROPOSITION)
      .copyInto(new BitSet());
    inputs.andNot(automaton.variableAllocation()
      .localToGlobal(controllableAps, ATOMIC_PROPOSITION));
    BddSet result = strategy.factory().of(false);
    for (BitSet input : BitSet2.powerSet(inputs)) {
      Optional<BitSet> valuation = strategy.intersection(strategy.factory().of(input, inputs))
        .element();
      if (valuation.isPresent()) {
        result = result.union(
          strategy.factory().of(valuation.get(),
            automaton.variableAllocation().variables(SymbolicAutomaton.VariableType.values())
          )
        );
      }
    }
    return result;
  }
}
