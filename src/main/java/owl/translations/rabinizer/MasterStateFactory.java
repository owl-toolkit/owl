package owl.translations.rabinizer;

import java.util.BitSet;
import javax.annotation.Nullable;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.ltl.EquivalenceClass;

final class MasterStateFactory extends RabinizerStateFactory {
  MasterStateFactory(boolean eager) {
    super(eager);
  }

  @Nullable
  Edge<EquivalenceClass> getMasterSuccessor(EquivalenceClass state, BitSet valuation) {
    EquivalenceClass successor = eager
      ? state.temporalStepUnfold(valuation)
      : state.unfoldTemporalStep(valuation);
    // If the master moves into false, there is no way of accepting, since the finite prefix
    // of the word already violates the formula. Hence, we refrain from creating this state.
    return successor.isFalse() ? null : Edges.create(successor);
  }

  public EquivalenceClass getInitialState(EquivalenceClass formula) {
    return eager ? formula.unfold() : formula;
  }
}
