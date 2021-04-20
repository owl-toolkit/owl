package owl.automaton.symbolic;

import java.util.BitSet;
import owl.bdd.BddSet;

@FunctionalInterface
public interface StrategyDeterminizer {
  BddSet determinize(SymbolicAutomaton<?> automaton, BitSet controllableAps, BddSet strategy);
}
