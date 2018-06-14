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
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nullable;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.LabelledEdge;
import owl.factories.ValuationSetFactory;

class ImplicitNonDeterministicLabelledEdgesAutomaton<S, A extends OmegaAcceptance>
  extends ImplicitCachedStatesAutomaton<S, A>
  implements LabelledEdgesAutomatonMixin<S, A> {

  @Nullable
  private final BiFunction<S, BitSet, ? extends Collection<Edge<S>>> edgesFunction;
  private final Function<S, ? extends Collection<LabelledEdge<S>>> labelledEdgesFunction;

  ImplicitNonDeterministicLabelledEdgesAutomaton(ValuationSetFactory factory,
    Collection<S> initialStates, A acceptance,
    @Nullable BiFunction<S, BitSet, ? extends Collection<Edge<S>>> edgesFunction,
    Function<S, ? extends Collection<LabelledEdge<S>>> labelledEdgesFunction) {
    super(factory, Set.copyOf(initialStates), acceptance);
    this.edgesFunction = edgesFunction;
    this.labelledEdgesFunction = labelledEdgesFunction;
  }

  @Override
  public Collection<Edge<S>> edges(S state, BitSet valuation) {
    assert cache() == null || cache().contains(state);

    if (edgesFunction != null) {
      return edgesFunction.apply(state, valuation);
    }

    return LabelledEdgesAutomatonMixin.super.edges(state, valuation);
  }

  @Override
  public Collection<LabelledEdge<S>> labelledEdges(S state) {
    assert cache() == null || cache().contains(state);
    return labelledEdgesFunction.apply(state);
  }
}
