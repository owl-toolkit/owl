package owl.translations.rabinizer;

import java.util.BitSet;
import javax.annotation.Nullable;
import owl.automaton.edge.Edge;
import owl.ltl.EquivalenceClass;

class RabinizerStateFactory {
  final boolean eager;

  RabinizerStateFactory(boolean eager) {
    this.eager = eager;
  }

  BitSet getClassSensitiveAlphabet(EquivalenceClass equivalenceClass) {
    return eager ? equivalenceClass.getAtoms() : equivalenceClass.unfold().getAtoms();
  }

  static final class ProductStateFactory extends RabinizerStateFactory {
    ProductStateFactory(boolean eager) {
      super(eager);
    }

    public BitSet getSensitiveAlphabet(RabinizerState state) {
      BitSet sensitiveAlphabet = getClassSensitiveAlphabet(state.masterState);
      for (MonitorState monitorState : state.monitorStates) {
        for (EquivalenceClass rankedFormula : monitorState.formulaRanking) {
          sensitiveAlphabet.or(getClassSensitiveAlphabet(rankedFormula));
        }
      }
      return sensitiveAlphabet;
    }
  }

  static final class MasterStateFactory extends RabinizerStateFactory {
    private final boolean complete;
    private final boolean fairnessFragment;

    MasterStateFactory(boolean eager, boolean complete, boolean fairnessFragment) {
      super(eager);
      assert !fairnessFragment || eager;
      this.complete = complete;
      this.fairnessFragment = fairnessFragment;
    }

    EquivalenceClass getInitialState(EquivalenceClass formula) {
      return eager ? formula.unfold() : formula;
    }

    @Nullable
    Edge<EquivalenceClass> getSuccessor(EquivalenceClass state, BitSet valuation) {
      EquivalenceClass successor;
      if (eager) {
        if (fairnessFragment) {
          successor = state;
        } else {
          successor = state.temporalStepUnfold(valuation);
        }
      } else {
        successor = state.unfoldTemporalStep(valuation);
      }

      // If the master moves into false, there is no way of accepting, since the finite prefix
      // of the word already violates the formula. Hence, we refrain from creating this state.
      return successor.isFalse() && !complete ? null : Edge.of(successor);
    }
  }
}
