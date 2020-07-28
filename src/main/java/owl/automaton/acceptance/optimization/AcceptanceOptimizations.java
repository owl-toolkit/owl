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

import com.google.common.collect.Sets;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.HashMapAutomaton;
import owl.automaton.MutableAutomaton;
import owl.automaton.Views;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.algorithm.LanguageEmptiness;
import owl.automaton.algorithm.SccDecomposition;
import owl.collections.ImmutableBitSet;

public final class AcceptanceOptimizations {

  // TODO: collapse inf / fins?

  private static final List<Consumer<MutableAutomaton<?, GeneralizedRabinAcceptance>>>
    generalizedRabinDefaultAllList = List.of(
    GeneralizedRabinAcceptanceOptimizations::minimizeOverlap,
    GeneralizedRabinAcceptanceOptimizations::minimizeMergePairs,
    GeneralizedRabinAcceptanceOptimizations::removeComplementaryInfSets,
    GeneralizedRabinAcceptanceOptimizations::minimizeEdgeImplications,
    GeneralizedRabinAcceptanceOptimizations::minimizeSccIrrelevant,
    GeneralizedRabinAcceptanceOptimizations::minimizePairImplications,
    GeneralizedRabinAcceptanceOptimizations::minimizeMergePairs,
    GeneralizedRabinAcceptanceOptimizations::removeComplementaryInfSets,
    GeneralizedRabinAcceptanceOptimizations::minimizePairImplications,
    GeneralizedRabinAcceptanceOptimizations::minimizeEdgeImplications,
    GeneralizedRabinAcceptanceOptimizations::minimizeSccIrrelevant,
    GeneralizedRabinAcceptanceOptimizations::mergeBuchiTypePairs
  );

  private static final List<Consumer<MutableAutomaton<?, GeneralizedRabinAcceptance>>>
    rabinDefaultAllList = List.of(
    GeneralizedRabinAcceptanceOptimizations::minimizeOverlap,
    GeneralizedRabinAcceptanceOptimizations::minimizeMergePairs,
    // GeneralizedRabinMinimizations::minimizeComplementaryInf,
    GeneralizedRabinAcceptanceOptimizations::minimizeEdgeImplications,
    GeneralizedRabinAcceptanceOptimizations::minimizeSccIrrelevant,
    GeneralizedRabinAcceptanceOptimizations::minimizePairImplications,
    GeneralizedRabinAcceptanceOptimizations::minimizeMergePairs,
    // GeneralizedRabinMinimizations::minimizeComplementaryInf,
    GeneralizedRabinAcceptanceOptimizations::minimizePairImplications,
    GeneralizedRabinAcceptanceOptimizations::minimizeEdgeImplications,
    GeneralizedRabinAcceptanceOptimizations::minimizeSccIrrelevant,
    GeneralizedRabinAcceptanceOptimizations::mergeBuchiTypePairs
  );

  private AcceptanceOptimizations() {}

  /**
   * Remove states from the automaton that cannot belong to an infinite accepting path. Moreover,
   * all transient edges are cleared of acceptance marks.
   *
   * @param automaton
   *   The automaton considered by the analysis.
   */
  public static <S> void removeDeadStates(MutableAutomaton<S, ?> automaton) {

    var sccDecomposition = SccDecomposition.of(automaton);
    var sccs = sccDecomposition.sccs();
    var condensationGraph = sccDecomposition.condensation();

    var rejectingIndices = new HashSet<Integer>();

    for (int sccIndex = sccs.size() - 1; sccIndex >= 0; sccIndex--) {
      var scc = sccs.get(sccIndex);

      if (sccDecomposition.isTransientScc(scc)) {
        if (rejectingIndices.containsAll(condensationGraph.successors(sccIndex))) {
          // Transient state with transitions to rejecting states
          rejectingIndices.add(sccIndex);
        }
      } else {
        S state = scc.iterator().next();

        Automaton<S, ?> filtered = Views.replaceInitialStates(automaton, scc);
        if (LanguageEmptiness.isEmpty(filtered, Set.of(state))) {
          // Scc is not accepting on its own
          var successors
            = Sets.difference(condensationGraph.successors(sccIndex), Set.of(sccIndex));
          if (rejectingIndices.containsAll(successors)) {
            // The scc only has internal edges or edges to rejecting states
            rejectingIndices.add(sccIndex);
          }
        }
      }
    }

    @Nullable
    ImmutableBitSet rejectingSet = automaton.acceptance().rejectingSet().orElse(null);
    automaton.removeStateIf(s -> rejectingIndices.contains(sccDecomposition.index(s)));
    automaton.updateEdges((state, edge) -> {
      int sccIndex = sccDecomposition.index(state);

      if (!sccs.get(sccIndex).contains(edge.successor())) {
        return edge.withoutAcceptance();
      }

      if (!rejectingIndices.contains(sccIndex)) {
        return edge;
      }

      return rejectingSet == null ? edge : edge.withAcceptance(rejectingSet);
    });

    automaton.trim();
  }

  static <S> void removeIndices(MutableAutomaton<S, ?> automaton, Set<S> states,
    BitSet indicesToRemove) {
    if (indicesToRemove.isEmpty() || states.isEmpty()) {
      return;
    }

    IntUnaryOperator transformer = index -> indicesToRemove.get(index) ? -1 : index;
    automaton.updateEdges(states, (state, edge) -> edge.mapAcceptance(transformer));
    automaton.trim();
  }

  static void removeAndRemapIndices(MutableAutomaton<?, ?> automaton, BitSet indicesToRemove) {
    if (indicesToRemove.isEmpty()) {
      automaton.trim();
      return;
    }

    int acceptanceSets = automaton.acceptance().acceptanceSets();
    Map<Integer, Integer> remapping = new HashMap<>();

    int newIndex = 0;
    for (int index = 0; index < acceptanceSets; index++) {
      if (!indicesToRemove.get(index)) {
        remapping.put(index, newIndex);
        newIndex += 1;
      }
    }

    automaton.updateEdges((state, edge) -> edge.mapAcceptance(x -> remapping.getOrDefault(x, -1)));
    automaton.trim();
  }

  public static <S, A extends EmersonLeiAcceptance> Automaton<S, A> transform(
    Automaton<S, A> automaton) {

    var mutableAutomaton = HashMapAutomaton.copyOf(automaton);
    removeDeadStates(mutableAutomaton);

    if (mutableAutomaton.acceptance() instanceof RabinAcceptance) {

      for (Consumer<MutableAutomaton<?, GeneralizedRabinAcceptance>> optimization
        : rabinDefaultAllList) {
        optimization.accept((MutableAutomaton) mutableAutomaton);
        mutableAutomaton.trim();
      }

    } else if (mutableAutomaton.acceptance() instanceof GeneralizedRabinAcceptance) {

      for (Consumer<MutableAutomaton<?, GeneralizedRabinAcceptance>> optimization
        : generalizedRabinDefaultAllList) {
        optimization.accept((MutableAutomaton) mutableAutomaton);
        mutableAutomaton.trim();
      }

    } else if (mutableAutomaton.acceptance() instanceof ParityAcceptance) {

      var castedAutomaton = (HashMapAutomaton<S, ParityAcceptance>) mutableAutomaton;
      ParityAcceptanceOptimizations.minimizePriorities(castedAutomaton);
      ParityAcceptanceOptimizations.setAcceptingSets(castedAutomaton);

    }

    return mutableAutomaton;
  }
}
