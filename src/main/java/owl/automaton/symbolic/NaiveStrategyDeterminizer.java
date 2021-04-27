package owl.automaton.symbolic;

import static owl.automaton.symbolic.InputOutputRelabeller.getMapping;
import static owl.automaton.symbolic.InputOutputRelabeller.invert;

import java.util.BitSet;
import owl.bdd.BddSet;
import owl.collections.Pair;

public class NaiveStrategyDeterminizer implements StrategyDeterminizer {

  @Override
  public BddSet determinize(
    SymbolicAutomaton<?> automaton,
    BitSet controllableAps,
    BddSet strategy
  ) {
    Pair<int[], Integer> relabelling = getMapping(automaton.variableAllocation(), controllableAps);
    int[] inverse = invert(relabelling.fst());
    return strategy
      .relabel(i -> relabelling.fst()[i])
      .determinizeRange(relabelling.snd(), automaton.variableAllocation().numberOfVariables())
      .relabel(i -> inverse[i]);
  }
}
