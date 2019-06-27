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

import com.google.common.collect.Maps;
import java.util.BitSet;
import java.util.List;
import java.util.Set;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.ValuationTree;

/**
 * Mixin interface for implementing an automaton by {@link Automaton#edgeMap(Object)}.
 *
 * <p>It is impossible to implement the incompatible sister interface {@link EdgesAutomatonMixin}
 * and the compiler will reject the code, since there are conflicting defaults for
 * {@link Automaton#preferredEdgeAccess()}.</p>
 *
 * @param <S> the type of the states of the automaton
 * @param <A> the type of the omega-acceptance condition of the automaton
 */
public interface EdgeMapAutomatonMixin<S, A extends OmegaAcceptance> extends Automaton<S, A> {

  List<PreferredEdgeAccess> ACCESS_MODES = List.of(EDGE_MAP, EDGE_TREE, EDGES);

  @Override
  default Set<Edge<S>> edges(S state) {
    return edgeMap(state).keySet();
  }

  @Override
  default Set<Edge<S>> edges(S state, BitSet valuation) {
    return Maps.filterValues(edgeMap(state), x -> x.contains(valuation)).keySet();
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
