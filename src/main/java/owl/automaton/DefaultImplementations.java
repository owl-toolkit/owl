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

import de.tum.in.naturals.bitset.BitSets;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import owl.automaton.edge.Edge;

final class DefaultImplementations {

  private DefaultImplementations() {
  }

  static <S> Set<S> visit(Automaton<S, ?> automaton, Automaton.EdgeVisitor<S> visitor) {
    Set<S> exploredStates = new HashSet<>(automaton.initialStates());
    Deque<S> workQueue = new ArrayDeque<>(exploredStates);
    Set<BitSet> powerSet = BitSets.powerSet(automaton.factory().alphabetSize());

    while (!workQueue.isEmpty()) {
      S state = workQueue.remove();
      visitor.enter(state);

      for (BitSet valuation : powerSet) {
        for (Edge<S> edge : automaton.edges(state, valuation)) {
          S successor = edge.successor();

          if (exploredStates.add(successor)) {
            workQueue.add(successor);
          }

          visitor.visit(state, valuation, edge);
        }
      }

      visitor.exit(state);
    }

    return exploredStates;
  }

  static <S> Set<S> visit(Automaton<S, ?> automaton, Automaton.EdgeMapVisitor<S> visitor) {
    Set<S> exploredStates = new HashSet<>(automaton.initialStates());
    Deque<S> workQueue = new ArrayDeque<>(exploredStates);

    while (!workQueue.isEmpty()) {
      S state = workQueue.remove();

      var edges = automaton.edgeMap(state);
      edges.keySet().forEach((edge) -> {
        S successor = edge.successor();

        if (exploredStates.add(successor)) {
          workQueue.add(successor);
        }
      });

      visitor.enter(state);
      visitor.visit(state, edges);
      visitor.exit(state);
    }

    return exploredStates;
  }

  static <S> Set<S> visit(Automaton<S, ?> automaton, Automaton.EdgeTreeVisitor<S> visitor) {
    Set<S> exploredStates = new HashSet<>(automaton.initialStates());
    Deque<S> workQueue = new ArrayDeque<>(exploredStates);

    while (!workQueue.isEmpty()) {
      S state = workQueue.remove();

      var edges = automaton.edgeTree(state);
      edges.values().forEach((edge) -> {
        S successor = edge.successor();

        if (exploredStates.add(successor)) {
          workQueue.add(successor);
        }
      });

      visitor.enter(state);
      visitor.visit(state, edges);
      visitor.exit(state);
    }

    return exploredStates;
  }

  /**
   * Returns all states reachable from the initial states.
   *
   * @param automaton
   *     The automaton.
   *
   * @return All from the initial states reachable states.
   */
  static <S> Set<S> getReachableStates(Automaton<S, ?> automaton) {
    Set<S> reachableStates = new HashSet<>(automaton.initialStates());
    Deque<S> workQueue = new ArrayDeque<>(reachableStates);

    while (!workQueue.isEmpty()) {
      for (S successor : automaton.successors(workQueue.remove())) {
        if (reachableStates.add(successor)) {
          workQueue.add(successor);
        }
      }
    }

    return reachableStates;
  }
}
