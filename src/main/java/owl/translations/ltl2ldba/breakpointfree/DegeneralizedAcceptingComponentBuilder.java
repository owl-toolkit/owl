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

package owl.translations.ltl2ldba.breakpointfree;

import java.util.BitSet;
import java.util.Set;
import javax.annotation.Nonnegative;
import javax.annotation.Nullable;
import owl.automaton.MutableAutomaton;
import owl.automaton.MutableAutomatonFactory;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.edge.Edge;
import owl.factories.Factories;
import owl.ltl.EquivalenceClass;
import owl.translations.ltl2ldba.AbstractAcceptingComponentBuilder;
import owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration;

public final class DegeneralizedAcceptingComponentBuilder extends AbstractAcceptingComponentBuilder
  <DegeneralizedBreakpointFreeState, BuchiAcceptance, FGObligations> {

  public DegeneralizedAcceptingComponentBuilder(Factories factories,
    Set<Configuration> optimisations) {
    super(optimisations, factories);
  }

  @Override
  public MutableAutomaton<DegeneralizedBreakpointFreeState, BuchiAcceptance> build() {
    return MutableAutomatonFactory.create(BuchiAcceptance.INSTANCE, factories.vsFactory, anchors,
      this::edge, x -> null);
  }

  @Override
  protected DegeneralizedBreakpointFreeState createState(EquivalenceClass remainder,
    FGObligations obligations) {
    EquivalenceClass safety = obligations.safetyFactory.initialStateWithRemainder(remainder);

    if (safety.isFalse()) {
      return null;
    }

    EquivalenceClass liveness;

    if (obligations.gfCoSafetyFactories.size() > 0) {
      liveness = obligations.gfCoSafetyFactories.get(0).steppedInitialState();
    } else {
      liveness = factories.eqFactory.getTrue();
    }

    return new DegeneralizedBreakpointFreeState(0, safety, liveness, obligations);
  }

  @Nullable
  public Edge<DegeneralizedBreakpointFreeState> edge(DegeneralizedBreakpointFreeState state,
    BitSet valuation) {
    var obligation = state.obligations;
    var safetyEdge = obligation.safetyFactory.edge(state.safety, valuation);

    if (safetyEdge == null) {
      return null;
    }

    var livenessSuccessor = factories.eqFactory.getTrue();
    int livenessLength = obligation.gfCoSafetyFactories.size();
    boolean acceptingEdge = false;
    int j = state.index;

    if (livenessLength > 0) {
      boolean obtainNewGoal = false;
      var livenessEdge = obligation.gfCoSafetyFactories
        .get(state.index).edge(state.liveness, valuation);

      // Scan for new index if currentSuccessor currentSuccessor is true.
      // In this way we can skip several fulfilled break-points at a time and are not bound to
      // slowly check one by one.
      if (livenessEdge.inSet(0)) {
        obtainNewGoal = true;
        j = scan(state, state.index + 1, valuation);

        if (j >= livenessLength) {
          acceptingEdge = true;
          j = scan(state, 0, valuation);

          if (j >= livenessLength) {
            j = 0;
          }
        }
      }

      if (obtainNewGoal && j < obligation.gfCoSafetyFactories.size()) {
        var factory = obligation.gfCoSafetyFactories.get(j);
        livenessSuccessor = factory.edge(factory.initialState(), valuation).successor();
      } else {
        livenessSuccessor = livenessEdge.successor();
      }
    } else {
      acceptingEdge = true;
    }

    DegeneralizedBreakpointFreeState successor = new DegeneralizedBreakpointFreeState(j,
      safetyEdge.successor(), livenessSuccessor, state.obligations);
    return acceptingEdge ? Edge.of(successor, 0) : Edge.of(successor);
  }

  @Nonnegative
  private int scan(DegeneralizedBreakpointFreeState state, @Nonnegative int i, BitSet valuation) {
    int index = i;
    var factories = state.obligations.gfCoSafetyFactories;
    int livenessLength = factories.size();

    while (index < livenessLength) {
      var factory = factories.get(index);
      var edge = factory.edge(factory.initialState(), valuation);

      if (edge.inSet(0)) {
        index++;
      } else {
        break;
      }
    }

    return index;
  }
}
