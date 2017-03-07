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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import owl.algorithms.SccAnalyser;
import owl.automaton.Automaton;
import owl.automaton.MutableAutomaton;

public final class AutomatonMinimization {
  private AutomatonMinimization() {
  }

  private static <S> boolean isTrap(Automaton<S, ?> automaton, Set<S> trap) {
    assert automaton.getStates().containsAll(trap);
    return trap.stream().allMatch(s -> trap.containsAll(automaton.getSuccessors(s)));
  }

  /**
   * Remove states from the automaton which are unreachable from the set of initial states or
   * that cannot belong to an infinite accepting path.
   *
   * @param automaton
   *     The automaton considered by the analysis.
   *
   * @see #removeDeadStates(MutableAutomaton, Set, Consumer)
   */
  public static <S> void removeDeadStates(MutableAutomaton<S, ?> automaton) {
    removeDeadStates(automaton, automaton.getInitialStates(), s -> {
    });
  }

  /**
   * Remove states from the automaton which are unreachable from the set of initial states or
   * that cannot belong to an infinite accepting path.
   *
   * @param automaton
   *     The automaton considered by the analysis.
   * @param initialStates
   *     The set of states that are the initial states for the reachability analysis. Required to be
   *     part of the automaton.
   *
   * @see #removeDeadStates(MutableAutomaton, Set, Consumer)
   */
  public static <S> void removeDeadStates(MutableAutomaton<S, ?> automaton, Set<S> initialStates) {
    removeDeadStates(automaton, initialStates, s -> {
    });
  }

  /**
   * Remove states from the automaton which are unreachable from the set of initial states or
   * that cannot belong to an infinite accepting path.
   *
   * @param automaton
   *     The automaton considered by the analysis.
   * @param initialStates
   *     The set of states that are the initial states for the reachability analysis. Required to be
   *     part of the automaton.
   * @param removedStatesConsumer
   *     A consumer called exactly once for each state removed from the automaton in no particular
   *     order.
   *
   * @see owl.automaton.acceptance.OmegaAcceptance#containsAcceptingRun(Set,
   * java.util.function.Function)
   */
  public static <S> void removeDeadStates(MutableAutomaton<S, ?> automaton,
    Set<S> initialStates, Consumer<S> removedStatesConsumer) {
    assert automaton.containsStates(initialStates) :
      String.format("States %s not part of the automaton",
        Sets.filter(initialStates, state -> !automaton.containsState(state)));

    automaton.removeUnreachableStates(initialStates, removedStatesConsumer);

    // We start from the bottom of the condensation graph.
    List<Set<S>> sccs =
      Lists.reverse(SccAnalyser.computeSccs(initialStates, automaton::getSuccessors));

    for (Set<S> scc : sccs) {
      if (!Collections.disjoint(scc, initialStates)) {
        // The SCC contains protected states.
        continue;
      }

      if (!isTrap(automaton, scc)) {
        // The SCC is not a BSCC.
        continue;
      }

      // There are no accepting runs.
      if (!automaton.getAcceptance().containsAcceptingRun(scc, automaton::getEdges)) {
        automaton.removeStates(scc);
        scc.forEach(removedStatesConsumer);
      }
    }
  }
}
