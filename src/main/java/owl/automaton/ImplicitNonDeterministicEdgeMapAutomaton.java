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

package owl.automaton;

import java.util.BitSet;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nullable;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;

class ImplicitNonDeterministicEdgeMapAutomaton<S, A extends OmegaAcceptance>
  extends AbstractImplicitAutomaton<S, A>
  implements EdgeMapAutomatonMixin<S, A> {

  @Nullable
  private final BiFunction<S, BitSet, Set<Edge<S>>> edgesFunction;
  private final Function<S, ? extends Map<Edge<S>, ValuationSet>> edgeMapFunction;

  ImplicitNonDeterministicEdgeMapAutomaton(ValuationSetFactory factory,
    Collection<S> initialStates, A acceptance,
    @Nullable BiFunction<S, BitSet, Set<Edge<S>>> edgesFunction,
    Function<S, ? extends Map<Edge<S>, ValuationSet>> edgeMapFunction) {
    super(factory, initialStates, acceptance);
    this.edgesFunction = edgesFunction;
    this.edgeMapFunction = edgeMapFunction;
  }

  @Override
  public Set<Edge<S>> edges(S state, BitSet valuation) {
    assert cache() == null || cache().contains(state);

    if (edgesFunction != null) {
      return edgesFunction.apply(state, valuation);
    }

    return EdgeMapAutomatonMixin.super.edges(state, valuation);
  }

  @Override
  public Map<Edge<S>, ValuationSet> edgeMap(S state) {
    assert cache() == null || cache().contains(state);
    return edgeMapFunction.apply(state);
  }
}
