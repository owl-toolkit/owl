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

package owl.translations.ltl2ldba.ng;

import java.util.BitSet;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.IntStream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import owl.ltl.EquivalenceClass;
import owl.automaton.AutomatonState;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.collections.BitSets;
import owl.collections.ValuationSet;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.factories.Factories;
import owl.translations.Optimisation;
import owl.translations.ltl2ldba.AbstractAcceptingComponent;
import owl.translations.ltl2ldba.Evaluator;
import owl.translations.ltl2ldba.InitialComponent;
import owl.translations.ltl2ldba.InitialComponentState;
import owl.translations.ltl2ldba.Selector;

public class NondetInitialComponent<S extends AutomatonState<S>, T> extends InitialComponent<S, T> {

  public NondetInitialComponent(@Nonnull
    AbstractAcceptingComponent<S, ? extends GeneralizedBuchiAcceptance, T> acceptingComponent,
    Factories factories,
    EnumSet<Optimisation> optimisations,
    Selector<T> recurringObligationsSelector,
    Evaluator<T> recurringObligationsEvaluator) {
    super(acceptingComponent, factories, optimisations, recurringObligationsSelector,
      recurringObligationsEvaluator);
  }

  static private BitSet collect(IntStream stream) {
    BitSet bitSet = new BitSet();
    stream.forEach(bitSet::set);
    return bitSet;
  }

  @Override
  @Nullable
  public Edge<InitialComponentState> getSuccessor(InitialComponentState state, BitSet valuation) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Map<Edge<InitialComponentState>, ValuationSet> getSuccessors(InitialComponentState state) {
    Map<Edge<InitialComponentState>, ValuationSet> successors = transitions.get(state);

    if (successors == null) {
      BitSet sensitiveAlphabet = state.getSensitiveAlphabet();
      successors = new LinkedHashMap<>();

      for (BitSet valuation : BitSets.powerSet(sensitiveAlphabet)) {
        Edge<InitialComponentState> successor = state.getSuccessor(valuation);

        if (successor == null) {
          continue;
        }

        // Split successor
        Iterable<EquivalenceClass> successorsList = factory
          .splitEquivalenceClass(successor.getSuccessor().getClazz());

        for (EquivalenceClass successorState : successorsList) {
          Edge<InitialComponentState> splitSuccessor = Edges
            .create(new InitialComponentState(this, successorState),
              collect(successor.acceptanceSetStream()));

          ValuationSet oldVs = successors.get(splitSuccessor);
          ValuationSet newVs = valuationSetFactory.createValuationSet(valuation, sensitiveAlphabet);

          if (oldVs == null) {
            successors.put(splitSuccessor, newVs);
          } else {
            oldVs.addAllWith(newVs);
          }
        }
      }

      transitions.put(state, successors);
    }

    return successors;
  }
}
