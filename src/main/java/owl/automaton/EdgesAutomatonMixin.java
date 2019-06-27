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

import static owl.automaton.Automaton.PreferredEdgeAccess.EDGES;
import static owl.automaton.Automaton.PreferredEdgeAccess.EDGE_MAP;
import static owl.automaton.Automaton.PreferredEdgeAccess.EDGE_TREE;

import de.tum.in.naturals.bitset.BitSets;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.ValuationSet;
import owl.collections.ValuationTree;
import owl.factories.ValuationSetFactory;

/**
 * Mixin interface for implementing an automaton by {@link Automaton#edges(Object, BitSet)}.
 *
 * <p>It is impossible to implement the incompatible sister interface
 * {@link EdgeMapAutomatonMixin} and the compiler will reject the code, since there are
 * conflicting defaults for {@link Automaton#preferredEdgeAccess()}.</p>
 *
 * @param <S> the type of the states of the automaton
 * @param <A> the type of the omega-acceptance condition of the automaton
 */
public interface EdgesAutomatonMixin<S, A extends OmegaAcceptance> extends Automaton<S, A> {

  List<PreferredEdgeAccess> ACCESS_MODES = List.of(EDGES, EDGE_MAP, EDGE_TREE);

  @Override
  default Set<Edge<S>> edges(S state) {
    Set<Edge<S>> edges = new HashSet<>();

    for (BitSet valuation : BitSets.powerSet(factory().alphabetSize())) {
      edges.addAll(edges(state, valuation));
    }

    return edges;
  }

  @Override
  default Map<Edge<S>, ValuationSet> edgeMap(S state) {
    ValuationSetFactory factory = factory();
    Map<Edge<S>, ValuationSet> labelledEdges = new HashMap<>();

    for (BitSet valuation : BitSets.powerSet(factory.alphabetSize())) {
      for (Edge<S> edge : edges(state, valuation)) {
        labelledEdges.merge(edge, factory.of(valuation), ValuationSet::union);
      }
    }

    return labelledEdges;
  }

  @Override
  default ValuationTree<Edge<S>> edgeTree(S state) {
    return factory().inverse(edgeMap(state));
  }

  @Override
  default List<PreferredEdgeAccess> preferredEdgeAccess() {
    return ACCESS_MODES;
  }
}
