/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;

@FunctionalInterface
public interface EdgeRelation<S> {

  /**
   * Returns all outgoing edges of the specified {@code state}.
   *
   * @param state The starting state of the transition.
   * @return The set of all outgoing edges collection.
   * @throws IllegalArgumentException If the transition function is not defined for {@code state}
   */
  Set<Edge<S>> edges(S state);

  default Set<S> successors(S state) {
    Set<Edge<S>> edges = edges(state);
    Set<S> successors = new HashSet<>(edges.size());
    edges.forEach(edge -> successors.add(edge.successor()));
    return successors;
  }

  static <S> EdgeRelation<S> filter(Automaton<S, ?> automaton, Set<S> states) {
    return filter(automaton::edges, edge -> states.contains(edge.successor()));
  }

  static <S> EdgeRelation<S> filter(EdgeRelation<S> edgeRelation, Predicate<Edge<S>> filter) {
    return new MemoizingEdgeRelation<S>(state -> {
      List<Edge<S>> edges = new ArrayList<>(edgeRelation.edges(state));
      edges.removeIf(Predicate.not(filter));
      return Set.of(edges.toArray(Edge[]::new));
    });
  }

  static <S> MemoizingEdgeRelation<S> memoizing(EdgeRelation<S> edgeRelation) {
    if (edgeRelation instanceof EdgeRelation.MemoizingEdgeRelation<S> memoizingEdgeRelation) {
      return memoizingEdgeRelation;
    }

    return new MemoizingEdgeRelation<>(edgeRelation);
  }

  final class MemoizingEdgeRelation<S> implements EdgeRelation<S> {

    private final EdgeRelation<S> relation;
    private final Map<S, Set<Edge<S>>> memoizedEdges = new HashMap<>();
    private final Map<S, Set<S>> memoizedSuccessors = new HashMap<>();

    MemoizingEdgeRelation(EdgeRelation<S> relation) {
      this.relation = relation;
    }

    @Override
    public Set<Edge<S>> edges(S state) {
      return memoizedEdges.computeIfAbsent(state, key -> Set.copyOf(relation.edges(state)));
    }

    @Override
    public Set<S> successors(S state) {
      return memoizedSuccessors.computeIfAbsent(state,
          key -> (Set<S>) Set.of(Edges.successors(edges(key)).toArray(Object[]::new)));
    }
  }
}
