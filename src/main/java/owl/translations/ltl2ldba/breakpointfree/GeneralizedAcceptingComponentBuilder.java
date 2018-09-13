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

import static owl.ltl.SyntacticFragment.SAFETY;

import com.google.common.base.Preconditions;
import java.util.BitSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnegative;
import javax.annotation.Nullable;
import owl.automaton.MutableAutomaton;
import owl.automaton.MutableAutomatonFactory;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.edge.Edge;
import owl.factories.Factories;
import owl.ltl.EquivalenceClass;
import owl.translations.canonical.DeterministicConstructions;
import owl.translations.ltl2ldba.AbstractAcceptingComponentBuilder;
import owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration;

public final class GeneralizedAcceptingComponentBuilder extends AbstractAcceptingComponentBuilder
  <GeneralizedBreakpointFreeState, GeneralizedBuchiAcceptance, FGObligations> {

  @Nonnegative
  private int acceptanceSets;

  public GeneralizedAcceptingComponentBuilder(Factories factories,
    Set<Configuration> optimisations) {
    super(optimisations, factories);
    acceptanceSets = 1;
  }

  @Override
  public MutableAutomaton<GeneralizedBreakpointFreeState, GeneralizedBuchiAcceptance> build() {
    return MutableAutomatonFactory.create(GeneralizedBuchiAcceptance.of(acceptanceSets),
      factories.vsFactory, anchors, this::edge, x -> null);
  }

  @Override
  protected GeneralizedBreakpointFreeState createState(EquivalenceClass remainder,
    FGObligations obligations) {
    Preconditions.checkArgument(remainder.modalOperators().stream().allMatch(SAFETY::contains));

    EquivalenceClass safety = obligations.safetyAutomaton.onlyInitialStateWithRemainder(remainder);
    List<EquivalenceClass> liveness = obligations.gfCoSafetyAutomata.stream()
      .map(DeterministicConstructions.GfCoSafety::onlyInitialState)
      .collect(Collectors.toUnmodifiableList());

    // If it is necessary, increase the number of acceptance conditions.
    if (liveness.size() > acceptanceSets) {
      acceptanceSets = liveness.size();
    }

    if (safety.isFalse()) {
      return null;
    }

    return new GeneralizedBreakpointFreeState(safety, liveness, obligations);
  }

  @Nullable
  private Edge<GeneralizedBreakpointFreeState> edge(GeneralizedBreakpointFreeState state,
    BitSet valuation) {
    FGObligations obligations = state.obligations;
    Edge<EquivalenceClass> safetyEdge = obligations.safetyAutomaton.edge(state.safety, valuation);

    if (safetyEdge == null) {
      return null;
    }

    EquivalenceClass[] livenessSuccessor = new EquivalenceClass[state.liveness.size()];

    BitSet acceptance = new BitSet();
    acceptance.set(state.liveness.size(), acceptanceSets);

    for (int i = 0; i < state.liveness.size(); i++) {
      var edge = obligations.gfCoSafetyAutomata.get(i).edge(state.liveness.get(i), valuation);

      livenessSuccessor[i] = edge.successor();
      if (edge.inSet(0)) {
        acceptance.set(i);
      }
    }

    return Edge.of(new GeneralizedBreakpointFreeState(
      safetyEdge.successor(), List.of(livenessSuccessor), state.obligations), acceptance);
  }
}
