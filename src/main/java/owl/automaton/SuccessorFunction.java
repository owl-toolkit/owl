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
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import owl.automaton.edge.Edge;

@FunctionalInterface
public interface SuccessorFunction<S> extends Function<S, Collection<S>> {

  @Override
  default Collection<S> apply(S s) {
    return successors(s);
  }

  /**
   * Returns all successors of the specified {@code state}.
   *
   * @param state
   *     The starting state of the transition.
   *
   * @return The successor collection.
   *
   * @throws IllegalArgumentException
   *     If the transition function is not defined for {@code state}
   */
  Collection<S> successors(S state);

  static <S> SuccessorFunction<S> filter(Automaton<S, ?> automaton,
    Set<S> states) {
    return filter(automaton, states, e -> true);
  }

  static <S> SuccessorFunction<S> filter(Automaton<S, ?> automaton,
    Set<S> states, Predicate<? super Edge<S>> edgeFilter) {
    return state -> {
      if (!states.contains(state)) {
        return List.of();
      }

      List<S> successors = new ArrayList<>();

      for (Edge<S> edge : automaton.edges(state)) {
        if (edgeFilter.test(edge) && states.contains(edge.successor())) {
          successors.add(edge.successor());
        }
      }

      return successors;
    };
  }

  static <S> SuccessorFunction<S> of(Function<S, ? extends Collection<S>> successorFunction) {
    return successorFunction::apply;
  }
}
