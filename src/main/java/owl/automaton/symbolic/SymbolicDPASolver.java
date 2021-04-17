package owl.automaton.symbolic;

import com.google.auto.value.AutoValue;
import owl.automaton.acceptance.ParityAcceptance;
import owl.bdd.BddSet;
import owl.collections.ImmutableBitSet;

@FunctionalInterface
public interface SymbolicDPASolver {
  Solution solve(SymbolicAutomaton<? extends ParityAcceptance> dpa, ImmutableBitSet controlledAps);

  @AutoValue
  abstract class Solution {
    public abstract Winner winner();

    public abstract BddSet winningRegion();

    public abstract BddSet strategy();

    static Solution of(Winner winner, BddSet winningRegion, BddSet strategy) {
      return new AutoValue_SymbolicDPASolver_Solution(winner, winningRegion, strategy);
    }

    public enum Winner {
      CONTROLLER,
      ENVIRONMENT
    }
  }

}
