/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
 *
 * This file is part of Owl.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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
    return eager
      ? equivalenceClass.atomicPropositions()
      : equivalenceClass.unfold().atomicPropositions();
  }

  static final class ProductStateFactory extends RabinizerStateFactory {
    ProductStateFactory(boolean eager) {
      super(eager);
    }

    public BitSet getSensitiveAlphabet(RabinizerState state) {
      BitSet sensitiveAlphabet = getClassSensitiveAlphabet(state.masterState());
      for (MonitorState monitorState : state.monitorStates()) {
        for (EquivalenceClass rankedFormula : monitorState.formulaRanking()) {
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
