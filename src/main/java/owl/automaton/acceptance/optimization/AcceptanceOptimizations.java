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

import it.unimi.dsi.fastutil.ints.Int2IntAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.BitSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
import owl.automaton.Automaton;
import owl.automaton.MutableAutomaton;
import owl.automaton.MutableAutomatonUtil;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.algorithm.LanguageEmptiness;
import owl.automaton.algorithm.SccDecomposition;
import owl.automaton.hoa.HoaWriter;
import owl.run.modules.OwlModule;

public final class AcceptanceOptimizations {
  private static final Logger logger = Logger.getLogger(AcceptanceOptimizations.class.getName());

  private static final List<Consumer<MutableAutomaton<?, GeneralizedRabinAcceptance>>>
    generalizedRabinDefaultAllList = List.of(
    GeneralizedRabinAcceptanceOptimizations::minimizeOverlap,
    GeneralizedRabinAcceptanceOptimizations::minimizeMergePairs,
    OmegaAcceptanceOptimizations::removeTransientAcceptance,
    // MinimizationUtil::removeDeadStates,
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
    OmegaAcceptanceOptimizations::removeTransientAcceptance,
    // MinimizationUtil::removeDeadStates,
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

  private static final List<Consumer<MutableAutomaton<?, ParityAcceptance>>>
    parityDefaultList = List.of(
    OmegaAcceptanceOptimizations::removeTransientAcceptance,
    // MinimizationUtil::removeDeadStates,
    ParityAcceptanceOptimizations::minimizePriorities
  );

  public static final OwlModule<OwlModule.Transformer> MODULE = OwlModule.of(
    "optimize-aut",
    "Heuristically removes states and acceptance sets from the given automaton.",
    (commandLine, environment) -> new AcceptanceOptimizationTransformer());

  private AcceptanceOptimizations() {}

  @SuppressWarnings("unchecked")
  public static <S, A extends OmegaAcceptance> MutableAutomaton<S, A> optimize(
    Automaton<S, A> automaton) {
    var mutableAutomaton = MutableAutomatonUtil.asMutable(automaton);
    var acceptance = mutableAutomaton.acceptance();

    if (acceptance instanceof RabinAcceptance) {
      apply(mutableAutomaton, rabinDefaultAllList);
    } else if (acceptance instanceof GeneralizedRabinAcceptance) {
      var dgra = (MutableAutomaton<Object, GeneralizedRabinAcceptance>) mutableAutomaton;
      apply(dgra, generalizedRabinDefaultAllList);
    } else if (acceptance instanceof ParityAcceptance) {
      var dpa = (MutableAutomaton<Object, ParityAcceptance>) mutableAutomaton;
      apply(dpa, parityDefaultList);
    } else {
      apply(mutableAutomaton, List.of(OmegaAcceptanceOptimizations::removeTransientAcceptance));
      logger.log(Level.FINE, "Received unsupported acceptance type {0}", acceptance.getClass());
    }

    return mutableAutomaton;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static <S, A extends OmegaAcceptance> void apply(
    MutableAutomaton<S, ?> automaton, List<Consumer<MutableAutomaton<?, A>>> optimizations) {
    logger.log(Level.FINE, "Optimizing automaton with {0}", optimizations);

    for (Consumer<MutableAutomaton<?, A>> optimization : optimizations) {
      logger.log(Level.FINEST,
        () -> String.format("Current automaton: %s", HoaWriter.toString(automaton)));
      logger.log(Level.FINER, "Applying {0}", optimization);
      optimization.accept((MutableAutomaton) automaton);
      automaton.trim();
    }

    logger.log(Level.FINEST, () -> String.format("Automaton after optimization:%n%s",
      HoaWriter.toString(automaton)));
  }

  /**
   * Remove states from the automaton that cannot belong to an infinite accepting path.
   *
   * @param automaton
   *     The automaton considered by the analysis.
   *
   */
  public static <S> void removeDeadStates(MutableAutomaton<S, ?> automaton) {
    var sccDecomposition = SccDecomposition.of(automaton);
    var sccs = sccDecomposition.sccs();
    var condensation = sccDecomposition.condensation();
    var removedSccs = new BitSet();

    for (int i = sccs.size() - 1; i >= 0; i--) {
      var successors = condensation.successors(i);
      boolean isBottomScc = true;

      for (int j : successors) {
        assert j >= i;

        if (j > i && !removedSccs.get(j)) {
          isBottomScc = false;
          break;
        }
      }

      if (isBottomScc) {
        var scc = sccs.get(i);

        if (LanguageEmptiness.isEmpty(automaton, Set.of(scc.iterator().next()))) {
          // There are no accepting runs.
          automaton.removeStateIf(scc::contains);
          // Ensure readable automaton.
          automaton.trim();
          removedSccs.set(i);
        }
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

  static void removeAndRemapIndices(MutableAutomaton<?, ?> automaton, IntSet indicesToRemove) {
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

  public static class AcceptanceOptimizationTransformer implements OwlModule.AutomatonTransformer {
    @Override
    public Object transform(Automaton<Object, ?> object) {
      var mutableAutomaton = MutableAutomatonUtil.asMutable(object);

      try {
        AcceptanceOptimizations.removeDeadStates(mutableAutomaton);
      } catch (UnsupportedOperationException ex) {
        // Ignore exception. Emptiness-check might not be implemented.
        logger.log(Level.FINE,
          "Unsupported acceptance type {0} for emptiness-check",
          mutableAutomaton.acceptance().getClass());
      }

      return optimize(mutableAutomaton);
    }
  }
}

