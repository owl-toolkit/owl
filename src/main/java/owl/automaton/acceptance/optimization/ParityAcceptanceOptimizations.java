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

package owl.automaton.acceptance.optimization;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.algorithm.SccDecomposition;
import owl.automaton.edge.Edge;

public final class ParityAcceptanceOptimizations {

  private ParityAcceptanceOptimizations() {}

  public static <S> MutableAutomaton<S, ParityAcceptance> setAcceptingSets(
    MutableAutomaton<S, ParityAcceptance> automaton) {
    int maximalAcceptance = automaton.states().stream()
      .map(automaton::edges)
      .flatMap(Collection::stream)
      .flatMapToInt(sEdge -> sEdge.colours().last().stream())
      .max()
      .orElse(-1);
    automaton.acceptance(automaton.acceptance().withAcceptanceSets(maximalAcceptance + 1));
    return automaton;
  }

  /**
   * This optimization simply determines all priorities used in each SCC and then tries to
   * eliminate "gaps". For example, when [0, 2, 4, 5] are used, we actually only need to
   * consider [0, 1]. Moreover for all transient edges the acceptance marks are removed.
   *
   * @param automaton the automaton
   * @param <S> the state type
   * @return a compacted acceptance.
   */
  public static <S> MutableAutomaton<S, ParityAcceptance> minimizePriorities(
    MutableAutomaton<S, ParityAcceptance> automaton) {

    ParityAcceptance acceptance = automaton.acceptance();
    List<Set<S>> sccs = SccDecomposition.of(automaton).sccs();
    int usedAcceptanceSets = 0;
    boolean max = automaton.acceptance().parity().max();

    for (Set<S> scc : sccs) {
      Map<Integer, Integer> reductionMapping = new HashMap<>();
      SortedSet<Integer> usedPriorities = new TreeSet<>();

      // Determine the used priorities
      for (S state : scc) {
        for (Edge<S> edge : automaton.edges(state)) {
          if (scc.contains(edge.successor())) {
            if (max) {
              edge.colours().last().ifPresent(usedPriorities::add);
            } else {
              edge.colours().first().ifPresent(usedPriorities::add);
            }
          }
        }
      }

      // Construct the mapping
      int currentTarget = usedPriorities.isEmpty()
        ? 0
        : usedPriorities.first() % 2;

      for (int currentPriority : usedPriorities) {
        if (currentTarget % 2 != currentPriority % 2) {
          currentTarget += 1;
        }

        reductionMapping.put(currentPriority, currentTarget);
        usedAcceptanceSets = Math.max(usedAcceptanceSets, currentTarget + 1);
      }

      automaton.updateEdges(scc, (state, edge) -> scc.contains(edge.successor())
        && !edge.colours().isEmpty()
        ? edge.withAcceptance(
          reductionMapping.getOrDefault(edge.colours().first().orElseThrow(), -1))
        : edge.withoutAcceptance());
      automaton.trim();
    }

    automaton.acceptance(acceptance.withAcceptanceSets(usedAcceptanceSets));
    return automaton;
  }
}
