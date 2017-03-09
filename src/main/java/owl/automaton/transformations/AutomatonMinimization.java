/*
 * Copyright (C) 2016  (See AUTHORS)
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

package owl.automaton.transformations;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Set;
import owl.algorithms.SccAnalyser;
import owl.automaton.Automaton;
import owl.automaton.MutableAutomaton;

public final class AutomatonMinimization {

  private AutomatonMinimization() {
  }

  private static <S> boolean isTrap(Automaton<S, ?> automaton, Set<S> trap) {
    return trap.stream().allMatch(s -> trap.containsAll(automaton.getSuccessors(s)));
  }

  /**
   * Remove states from the automaton, that are unreachable from the set of protected states or
   * that cannot belong to an infinite accepting path.
   *
   * @param protectedStates
   *     the set of states that are the initial states for a reachability analysis
   *
   */
  public static <S> void removeDeadStates(MutableAutomaton<S, ?> automaton,
    Set<S> protectedStates) {
    automaton.removeUnreachableStates(protectedStates);

    // We start from the bottom of the condensation graph.
    List<Set<S>> sccs = Lists.reverse(SccAnalyser.computeSccs(automaton));

    for (Set<S> scc : sccs) {
      // The SCC contains protected states.
      if (!Sets.intersection(scc, protectedStates).isEmpty()) {
        continue;
      }

      // The SCC is not a BSCC.
      if (!isTrap(automaton, scc)) {
        continue;
      }

      // The SCC is rejecting.
      if (!automaton.getAcceptance().isAccepting(scc, x ->
        Iterables.transform(automaton.getLabelledEdges(x), y -> y.edge))) {
        automaton.removeStates(scc);
      }
    }
  }
}
