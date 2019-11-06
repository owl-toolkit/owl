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

import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.ValuationSet;

/**
 * Mixin interface for implementing an automaton by {@link Automaton#edgeTree(Object)}.
 *
 * <p>It is impossible to implement the incompatible sister interface {@link EdgesAutomatonMixin}
 * and the compiler will reject the code, since there are conflicting defaults for
 * {@link Automaton#preferredEdgeAccess()}.</p>
 *
 * @param <S> the type of the states of the automaton
 * @param <A> the type of the omega-acceptance condition of the automaton
 */
public interface EdgeTreeAutomatonMixin<S, A extends OmegaAcceptance> extends Automaton<S, A> {

  List<PreferredEdgeAccess> ACCESS_MODES = List.of(EDGE_TREE, EDGE_MAP, EDGES);

  @Override
  default Map<Edge<S>, ValuationSet> edgeMap(S state) {
    return edgeTree(state).inverse(factory());
  }

  @Override
  default Set<Edge<S>> edges(S state) {
    return edgeTree(state).values();
  }

  @Override
  default Set<Edge<S>> edges(S state, BitSet valuation) {
    return edgeTree(state).get(valuation);
  }

  @Override
  default Set<S> successors(S state) {
    return edgeTree(state).values(Edge::successor);
  }

  @Override
  default List<PreferredEdgeAccess> preferredEdgeAccess() {
    return ACCESS_MODES;
  }
}
