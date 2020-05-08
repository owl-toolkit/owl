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

package owl.automaton.acceptance.optimization;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.algorithm.SccDecomposition;
import owl.automaton.edge.Edge;

public final class ParityAcceptanceOptimizations {

  private ParityAcceptanceOptimizations() {}

  public static <S> MutableAutomaton<S, ParityAcceptance> minimizePriorities(
    MutableAutomaton<S, ParityAcceptance> automaton) {
    OmegaAcceptanceOptimizations.removeTransientAcceptance(automaton);
    return minimizePriorities(automaton,
      SccDecomposition.of(automaton).sccsWithoutTransient());
  }

  private static <S> MutableAutomaton<S, ParityAcceptance> minimizePriorities(
    MutableAutomaton<S, ParityAcceptance> automaton, List<Set<S>> sccs) {
    /* This optimization simply determines all priorities used in each SCC and then tries to
     * eliminate "gaps". For example, when [0, 2, 4, 5] are used, we actually only need to consider
     * [0, 1]. Furthermore, edges between SCCs are set to an arbitrary priority. */

    ParityAcceptance acceptance = automaton.acceptance();
    int acceptanceSets = acceptance.acceptanceSets();

    // Construct the mapping for the priorities in this map
    Int2IntMap reductionMapping = new Int2IntOpenHashMap();
    reductionMapping.defaultReturnValue(-1);
    // Priorities used in each SCC
    SortedSet<Integer> usedPriorities = new TreeSet<>();
    int usedAcceptanceSets = 0;

    for (Set<S> scc : sccs) {
      reductionMapping.clear();
      usedPriorities.clear();

      // Determine the used priorities
      for (S state : scc) {
        for (Edge<S> edge : automaton.edges(state)) {
          if (edge.hasAcceptanceSets() && scc.contains(edge.successor())) {
            usedPriorities.add(edge.smallestAcceptanceSet());
          }
        }
      }

      // All priorities are used, can't collapse any
      if (usedPriorities.size() == acceptanceSets) {
        usedAcceptanceSets = Math.max(usedAcceptanceSets, acceptanceSets);
        continue;
      }
      if (usedPriorities.isEmpty()) {
        continue;
      }

      // Construct the mapping
      int currentTarget = usedPriorities.first() % 2;
      for (int currentPriority : usedPriorities) {
        if (currentTarget % 2 != currentPriority % 2) {
          currentTarget += 1;
        }
        reductionMapping.put(currentPriority, currentTarget);
        usedAcceptanceSets = Math.max(usedAcceptanceSets, currentTarget + 1);
      }

      // This remaps _all_ outgoing edges of the states in the SCC - including transient edges.
      // Since these are only taken finitely often by any run, their value does not matter.
      automaton.updateEdges(scc, (state, edge) -> edge.withAcceptance(reductionMapping));
      automaton.trim();
    }

    automaton.acceptance(acceptance.withAcceptanceSets(usedAcceptanceSets));
    return automaton;
  }
}
