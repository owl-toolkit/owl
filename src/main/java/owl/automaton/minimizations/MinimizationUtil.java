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

package owl.automaton.minimizations;

import it.unimi.dsi.fastutil.ints.Int2IntAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.BitSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.algorithms.LanguageEmptiness;
import owl.automaton.algorithms.SccDecomposition;
import owl.automaton.output.HoaPrinter;
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
      logger.log(Level.FINEST,
        () -> String.format("Current automaton: %s", HoaPrinter.toString(automaton)));
      logger.log(Level.FINER, "Applying {0}", minimization);
      minimization.minimize((MutableAutomaton) automaton);
      automaton.trim();
    }

    logger.log(Level.FINEST, () -> String.format("Automaton after optimization:%n%s",
      HoaPrinter.toString(automaton)));
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
      automaton.trim();
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
    automaton.trim();
  }

  /**
   * Remove states from the automaton that cannot belong to an infinite accepting path.
   *
   * @param automaton
   *     The automaton considered by the analysis.
   *
   */
  public static <S> void removeDeadStates(MutableAutomaton<S, ?> automaton) {
    // We start from the bottom of the condensation graph.
    // Check for transient thingies...
    List<Set<S>> sccs = SccDecomposition.computeSccs(automaton);

    for (Set<S> scc : sccs) {
      // The SCC is not a BSCC. Thus we can reach an accepting SCC.
      if (!SccDecomposition.isTrap(automaton, scc)) {
        continue;
      }

      // There are no accepting runs.
      if (LanguageEmptiness.isEmpty(automaton, scc.iterator().next())) {
        logger.log(Level.FINER, "Removing scc {0}", scc);
        automaton.removeStateIf(scc::contains);
        // Ensure readable automaton.
        automaton.trim();
      }
    }
  }

  static <S> void removeIndices(MutableAutomaton<S, ?> automaton, Set<S> states,
    BitSet indicesToRemove) {
    if (indicesToRemove.isEmpty() || states.isEmpty()) {
      return;
    }

    logger.log(Level.FINER, "Removing acceptance indices {0} on subset", indicesToRemove);
    IntUnaryOperator transformer = index -> indicesToRemove.get(index) ? -1 : index;
    automaton.updateEdges(states, (state, edge) -> edge.withAcceptance(transformer));
    automaton.trim();
  }

  public enum MinimizationLevel {
    LIGHT, MEDIUM, ALL
  }
}

