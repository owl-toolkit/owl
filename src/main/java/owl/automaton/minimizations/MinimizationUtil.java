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

import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2IntAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.algorithms.EmptinessCheck;
import owl.automaton.algorithms.SccDecomposition;
import owl.automaton.transformations.ParityUtil;

public final class MinimizationUtil {
  private static final Logger logger = Logger.getLogger(MinimizationUtil.class.getName());
  private static final List<Minimization<Object, GeneralizedRabinAcceptance>>
    generalizedRabinDefaultLightList = List.of(
    GeneralizedRabinMinimizations::minimizeOverlap,
    GenericMinimizations::removeTransientAcceptance,
    // MinimizationUtil::removeDeadStates,
    GeneralizedRabinMinimizations::minimizeComplementaryInf,
    GeneralizedRabinMinimizations::minimizeGloballyIrrelevant,
    GeneralizedRabinMinimizations::minimizeEdgeImplications,
    GeneralizedRabinMinimizations::minimizeSccIrrelevant,
    GeneralizedRabinMinimizations::minimizeTrivial
  );
  private static final List<Minimization<Object, GeneralizedRabinAcceptance>>
    generalizedRabinDefaultAllList = List.of(
    GeneralizedRabinMinimizations::minimizeOverlap,
    GeneralizedRabinMinimizations::minimizeMergePairs,
    GenericMinimizations::removeTransientAcceptance,
    // MinimizationUtil::removeDeadStates,
    GeneralizedRabinMinimizations::minimizeComplementaryInf,
    GeneralizedRabinMinimizations::minimizeGloballyIrrelevant,
    GeneralizedRabinMinimizations::minimizeEdgeImplications,
    GeneralizedRabinMinimizations::minimizeSccIrrelevant,
    GeneralizedRabinMinimizations::minimizeTrivial,
    GeneralizedRabinMinimizations::minimizePairImplications,
    GeneralizedRabinMinimizations::minimizeMergePairs,
    GeneralizedRabinMinimizations::minimizeComplementaryInf,
    GeneralizedRabinMinimizations::minimizePairImplications,
    GeneralizedRabinMinimizations::minimizeEdgeImplications,
    GeneralizedRabinMinimizations::minimizeSccIrrelevant,
    GeneralizedRabinMinimizations::minimizeGloballyIrrelevant
  );
  private static final List<Minimization<Object, GeneralizedRabinAcceptance>>
    rabinDefaultAllList = List.of(
    GeneralizedRabinMinimizations::minimizeOverlap,
    GeneralizedRabinMinimizations::minimizeMergePairs,
    GenericMinimizations::removeTransientAcceptance,
    // MinimizationUtil::removeDeadStates,
    // GeneralizedRabinMinimizations::minimizeComplementaryInf,
    GeneralizedRabinMinimizations::minimizeGloballyIrrelevant,
    GeneralizedRabinMinimizations::minimizeEdgeImplications,
    GeneralizedRabinMinimizations::minimizeSccIrrelevant,
    GeneralizedRabinMinimizations::minimizeTrivial,
    GeneralizedRabinMinimizations::minimizePairImplications,
    GeneralizedRabinMinimizations::minimizeMergePairs,
    // GeneralizedRabinMinimizations::minimizeComplementaryInf,
    GeneralizedRabinMinimizations::minimizePairImplications,
    GeneralizedRabinMinimizations::minimizeEdgeImplications,
    GeneralizedRabinMinimizations::minimizeSccIrrelevant,
    GeneralizedRabinMinimizations::minimizeGloballyIrrelevant
  );

  private static final List<Minimization<Object, ParityAcceptance>>
    parityDefaultList = List.of(
    GenericMinimizations::removeTransientAcceptance,
    // MinimizationUtil::removeDeadStates,
    ParityUtil::minimizePriorities
  );

  private MinimizationUtil() {}

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static <S, A extends OmegaAcceptance> void applyMinimization(
    MutableAutomaton<S, ? extends A> automaton, List<Minimization<S, A>> minimizationList) {
    if (minimizationList.isEmpty()) {
      return;
    }
    logger.log(Level.FINE, "Optimizing automaton with {0}", minimizationList);

    for (Minimization<S, ? extends A> minimization : minimizationList) {
      logger.log(Level.FINEST, () -> String.format("Current automaton: %s", toHoa(automaton)));
      logger.log(Level.FINER, "Applying {0}", minimization);
      minimization.minimize((MutableAutomaton) automaton);
    }

    logger.log(Level.FINEST, () -> String.format("Automaton after optimization:%n%s",
      toHoa(automaton)));
  }

  @SuppressWarnings("unchecked")
  public static <S, A extends OmegaAcceptance> MutableAutomaton<S, A> minimizeDefault(
    MutableAutomaton<S, A> automaton, MinimizationLevel level) {
    OmegaAcceptance acceptance = automaton.acceptance();

    if (acceptance instanceof RabinAcceptance) {
      applyMinimization((MutableAutomaton<Object, RabinAcceptance>) automaton,
        rabinDefaultAllList);
    } else if (acceptance instanceof GeneralizedRabinAcceptance) {
      var dgra = (MutableAutomaton<Object, GeneralizedRabinAcceptance>) automaton;
      if (level == MinimizationLevel.ALL) {
        applyMinimization(dgra, generalizedRabinDefaultAllList);
      } else {
        applyMinimization(dgra, generalizedRabinDefaultLightList);
      }
    } else if (acceptance instanceof ParityAcceptance) {
      var dpa = (MutableAutomaton<Object, ParityAcceptance>) automaton;
      applyMinimization(dpa, parityDefaultList);
    } else {
      // removeDeadStates(automaton);
      logger.log(Level.FINE, "Received unsupported acceptance type {0}", acceptance.getClass());
    }

    return automaton;
  }

  public static <S> void removeAndRemapIndices(MutableAutomaton<S, ?> automaton,
    IntSet indicesToRemove) {
    if (indicesToRemove.isEmpty()) {
      return;
    }

    int acceptanceSets = automaton.acceptance().acceptanceSets();
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
    automaton.updateEdges((state, edge) -> edge.withAcceptance(remapping));
  }

  public static <S> void removeDeadStates(MutableAutomaton<S, ?> automaton) {
    removeDeadStates(automaton, automaton.initialStates(), s -> false, s -> {
    });
  }

  public static <S> void removeDeadStates(MutableAutomaton<S, ?> automaton,
    Predicate<? super S> isProtected,
    Consumer<? super S> removedStatesConsumer) {
    removeDeadStates(automaton, automaton.initialStates(), isProtected, removedStatesConsumer);
  }

  /**
   * Remove states from the automaton which are unreachable from the set of initial states or that
   * cannot belong to an infinite accepting path.
   *
   * @param automaton
   *     The automaton considered by the analysis.
   * @param initialStates
   *     The set of states that are the initial states for the reachability analysis. Required to be
   *     part of the automaton.
   * @param isProtected
   *     If a state is marked as protected and reachable, it won't be removed.
   * @param removedStatesConsumer
   *     A consumer called exactly once for each state removed from the automaton in no particular
   *     order.
   */
  public static <S> void removeDeadStates(MutableAutomaton<S, ?> automaton,
    Set<S> initialStates,
    Predicate<? super S> isProtected,
    Consumer<? super S> removedStatesConsumer) {
    assert automaton.states().containsAll(initialStates) :
      String.format("States %s not part of the automaton",
        Sets.filter(initialStates, state -> !automaton.states().contains(state)));

    automaton.removeUnreachableStates(initialStates, removedStatesConsumer);

    // We start from the bottom of the condensation graph.
    // Check for transient thingies...
    List<Set<S>> sccs = SccDecomposition.computeSccs(automaton, initialStates);

    for (Set<S> scc : sccs) {
      if (scc.stream().anyMatch(isProtected)) {
        // The SCC contains protected states.
        continue;
      }

      // The SCC is not a BSCC. Thus we can reach either an accepting SCC or a SCC with a protected
      // state
      if (!SccDecomposition.isTrap(automaton, scc)) {
        continue;
      }

      // There are no accepting runs.
      if (EmptinessCheck.isRejectingScc(automaton, scc)) {
        logger.log(Level.FINER, "Removing scc {0}", scc);
        automaton.removeStates(scc);
        scc.forEach(removedStatesConsumer);
      }
    }
  }

  static <S> void removeIndices(MutableAutomaton<S, ?> automaton, Set<S> states,
    IntSet indicesToRemove) {
    if (indicesToRemove.isEmpty() || states.isEmpty()) {
      return;
    }

    logger.log(Level.FINER, "Removing acceptance indices {0} on subset", indicesToRemove);
    IntUnaryOperator transformer = index -> indicesToRemove.contains(index) ? -1 : index;
    automaton.updateEdges(states, (state, edge) -> edge.withAcceptance(transformer));
  }

  public enum MinimizationLevel {
    LIGHT, MEDIUM, ALL
  }
}

