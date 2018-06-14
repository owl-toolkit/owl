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

import com.google.common.collect.Collections2;
import de.tum.in.naturals.bitset.BitSets;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.LabelledEdge;
import owl.automaton.edge.LabelledEdges;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;

/**
 * Mixin interface for implementing an automaton by {@link Automaton#edges(Object, BitSet)}.
 *
 * <p>It is impossible to implement the incompatible sister interface
 * {@link LabelledEdgesAutomatonMixin} and the compiler will reject the code, since there are
 * conflicting defaults for {@link Automaton#prefersLabelled()}.</p>
 *
 * @param <S> the type of the states of the automaton
 * @param <A> the type of the omega-acceptance condition of the automaton
 */
public interface EdgesAutomatonMixin<S, A extends OmegaAcceptance> extends Automaton<S, A> {

  @Override
  default Set<S> successors(S state) {
    return new HashSet<>(Collections2.transform(edges(state), Edge::successor));
  }

  @Override
  default Collection<Edge<S>> edges(S state) {
    Set<Edge<S>> edges = new HashSet<>();

    for (BitSet valuation : BitSets.powerSet(factory().alphabetSize())) {
      edges.addAll(edges(state, valuation));
    }

    return edges;
  }

  @Override
  default Collection<LabelledEdge<S>> labelledEdges(S state) {
    ValuationSetFactory factory = factory();
    Map<Edge<S>, ValuationSet> labelledEdges = new HashMap<>();

    for (BitSet valuation : BitSets.powerSet(factory.alphabetSize())) {
      for (Edge<S> edge : edges(state, valuation)) {
        labelledEdges.merge(edge, factory.of(valuation), ValuationSet::union);
      }
    }

    return LabelledEdges.asCollection(labelledEdges);
  }

  @Override
  default boolean prefersLabelled() {
    return false;
  }
}
