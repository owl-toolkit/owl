/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nullable;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.ValuationTree;
import owl.factories.ValuationSetFactory;

public class ImplicitNonDeterministicEdgeTreeAutomaton<S, A extends OmegaAcceptance>
  extends AbstractImplicitAutomaton<S, A>
  implements EdgeTreeAutomatonMixin<S, A> {

  @Nullable
  private final BiFunction<S, BitSet, Set<Edge<S>>> edgesFunction;
  private final Function<S, ValuationTree<Edge<S>>> edgeTreeFunction;

  public ImplicitNonDeterministicEdgeTreeAutomaton(ValuationSetFactory factory,
    Collection<S> initialStates, A acceptance,
    @Nullable BiFunction<S, BitSet, Set<Edge<S>>> edgesFunction,
    Function<S, ValuationTree<Edge<S>>> edgeTreeFunction) {
    super(factory, initialStates, acceptance);
    this.edgesFunction = edgesFunction;
    this.edgeTreeFunction = edgeTreeFunction;
  }

  @Override
  public Set<Edge<S>> edges(S state, BitSet valuation) {
    assert cache() == null || cache().contains(state);

    if (edgesFunction != null) {
      return edgesFunction.apply(state, valuation);
    }

    return EdgeTreeAutomatonMixin.super.edges(state, valuation);
  }

  @Override
  public ValuationTree<Edge<S>> edgeTree(S state) {
    assert cache() == null || cache().contains(state);
    return edgeTreeFunction.apply(state);
  }
}
