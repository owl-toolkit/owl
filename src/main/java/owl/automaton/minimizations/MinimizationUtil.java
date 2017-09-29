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

package owl.automaton.minimizations;

import static owl.automaton.AutomatonUtil.toHoa;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2IntAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.IntIterators;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import owl.algorithms.SccAnalyser;
import owl.automaton.Automaton;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;

public final class MinimizationUtil {
  private static final Consumer<?> EMPTY_CONSUMER = (x) -> { };
  private static final Logger logger = Logger.getLogger(MinimizationUtil.class.getName());

  private MinimizationUtil() {}

  public static <S, A extends OmegaAcceptance> void applyMinimization(
    MutableAutomaton<S, A> automaton, List<Minimization<S, A>> minimizationList) {
    if (minimizationList.isEmpty()) {
      return;
    }
    logger.log(Level.FINE, "Optimizing automaton with {0}", minimizationList);

    for (Minimization<S, A> minimization : minimizationList) {
      logger.log(Level.FINEST, () -> String.format("Current automaton: %s", toHoa(automaton)));
      logger.log(Level.FINER, "Applying {0}", minimization);
      minimization.minimize(automaton);
    }

    logger.log(Level.FINEST, () -> String.format("Automaton after optimization:%n%s",
      toHoa(automaton)));
  }

  private static <S> boolean isTrap(Automaton<S, ?> automaton, Set<S> trap) {
    assert automaton.getStates().containsAll(trap);
    return trap.stream().allMatch(s -> trap.containsAll(automaton.getSuccessors(s)));
  }

  public static <S> void removeAndRemapIndices(MutableAutomaton<S, ?> automaton,
    IntSet indicesToRemove) {
    if (indicesToRemove.isEmpty()) {
      return;
    }
    OmegaAcceptance acceptance = automaton.getAcceptance();
    int acceptanceSets = acceptance.getAcceptanceSets();

    Int2IntMap remapping = new Int2IntAVLTreeMap();
    remapping.defaultReturnValue(Integer.MAX_VALUE);

    int newIndex = 0;
    for (int index = 0; index < acceptanceSets; index++) {
      if (indicesToRemove.contains(index)) {
        remapping.put(index, -1);
      } else {
        remapping.put(index, newIndex);
        newIndex += 1;
      }
    }

    logger.log(Level.FINER, "Remapping acceptance indices: {0}", remapping);
    automaton.remapAcceptance(automaton.getStates(), remapping);
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
    removeDeadStates(automaton, initialStates, (Consumer<S>) EMPTY_CONSUMER);
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
    removeDeadStates(automaton, automaton.getInitialStates(), (Consumer<S>) EMPTY_CONSUMER);
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

  public static <S> void removeIndices(MutableAutomaton<S, ?> automaton, Set<S> states,
    IntSet indicesToRemove) {
    if (indicesToRemove.isEmpty() || states.isEmpty()) {
      return;
    }
    logger.log(Level.FINER, "Removing acceptance indices {0} on subset", indicesToRemove);
    automaton.remapAcceptance(states, index -> indicesToRemove.contains(index) ? -1 : index);
  }

  static <S> BiFunction<S, Edge<S>, BitSet> removeIndicesFunction(IntSet indicesToRemove,
    @Nullable Set<S> states) {
    // TODO Use this to do index removal only on edges strictly within a set
    return (state, edge) -> {
      if (states != null && !states.contains(edge.getSuccessor())) {
        return null;
      }
      if (!IntIterators.any(indicesToRemove.iterator(), edge::inSet)) {
        return null;
      }
      BitSet newAcceptance = new BitSet();
      edge.acceptanceSetIterator().forEachRemaining((int index) -> {
        if (!indicesToRemove.contains(index)) {
          newAcceptance.set(index);
        }
      });

      return newAcceptance;
    };
  }

}
