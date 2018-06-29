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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;

/**
 * Mixin interface for implementing an automaton by {@link Automaton#labelledEdges(Object)}.
 *
 * <p>It is impossible to implement the incompatible sister interface {@link EdgesAutomatonMixin}
 * and the compiler will reject the code, since there are conflicting defaults for
 * {@link Automaton#prefersLabelled()}.</p>
 *
 * @param <S> the type of the states of the automaton
 * @param <A> the type of the omega-acceptance condition of the automaton
 */
public interface LabelledEdgesAutomatonMixin<S, A extends OmegaAcceptance> extends Automaton<S, A> {

  @Override
  default Set<S> successors(S state) {
    return Edges.successors(edges(state));
  }

  @Override
  default Set<Edge<S>> edges(S state) {
    return labelledEdges(state).keySet();
  }

  @Override
  default Collection<Edge<S>> edges(S state, BitSet valuation) {
    List<Edge<S>> edges = new ArrayList<>();

    forEachLabelledEdge(state, (edge, valuationSet) -> {
      if (valuationSet.contains(valuation)) {
        edges.add(edge);
      }
    });

    return edges;
  }

  @Override
  default boolean prefersLabelled() {
    return true;
  }
}
