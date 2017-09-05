/*
 * Copyright (C) 2016  (See AUTHORS)
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

package owl.translations.frequency;

import java.util.BitSet;
import java.util.Collection;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.edge.Edge;
import owl.factories.Factories;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;

public final class MasterAutomaton extends Automaton<MasterAutomaton.MasterState, AllAcceptance> {
  public MasterAutomaton(Formula formula, Factories factories,
    Collection<Optimisation> optimisations) {
    super(AllAcceptance.INSTANCE, factories);

    EquivalenceClass clazz = factories.eqFactory.of(formula);

    if (optimisations.contains(Optimisation.EAGER)) {
      setInitialState(new MasterState(clazz.unfold(), true));
    } else {
      setInitialState(new MasterState(clazz, false));
    }
  }

  @Override
  protected void toHoaBodyEdge(MasterState state, HoaConsumerExtended hoa) {
    // empty
  }

  public static final class MasterState extends EquivalenceClassState<MasterState> {
    MasterState(EquivalenceClass equivalenceClass, boolean eager) {
      super(equivalenceClass, eager);
    }

    @Nullable
    @Override
    public Edge<MasterState> getSuccessor(@Nonnull BitSet valuation) {
      EquivalenceClass successor = eager
        ? equivalenceClass.temporalStepUnfold(valuation)
        : equivalenceClass.unfoldTemporalStep(valuation);

      if (successor.isFalse()) {
        return null;
      }

      return Edge.of(new MasterState(successor, eager));
    }
  }
}
