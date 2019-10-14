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

import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2IntAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.MutableAutomaton;
import owl.automaton.MutableAutomatonUtil;
import owl.automaton.Views;
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
    AcceptanceOptimizations::removeTransientAcceptance,
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
    AcceptanceOptimizations::removeTransientAcceptance,
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
    AcceptanceOptimizations::removeTransientAcceptance,
    AcceptanceOptimizations::removeDeadStates,
    ParityAcceptanceOptimizations::minimizePriorities,
    ParityAcceptanceOptimizations::setAcceptingSets
  );

  public static final OwlModule<OwlModule.Transformer> MODULE = OwlModule.of(
    "optimize-aut",
    "Heuristically removes states and acceptance sets from the given automaton.",
    (commandLine, environment) -> new AcceptanceOptimizationTransformer());

  private AcceptanceOptimizations() {}

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
      apply(mutableAutomaton, List.of(AcceptanceOptimizations::removeTransientAcceptance));
      logger.log(Level.FINE, "Received unsupported acceptance type {0}", acceptance.getClass());
    }

    assert mutableAutomaton.acceptance().getClass().equals(automaton.acceptance().getClass());
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

  public static <S> void removeDeadStates(MutableAutomaton<S, ?> automaton) {
    removeDeadStates(automaton, true);
  }

  /**
   * Remove states from the automaton that cannot belong to an infinite accepting path.
   *
   * @param automaton
   *   The automaton considered by the analysis.
   * @param removeTransientEdges
   *   Remove transient edges and normalise acceptance sets of rejecting sccs.
   */
  public static <S> void removeDeadStates(MutableAutomaton<S, ?> automaton,
    boolean removeTransientEdges) {

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

        Automaton<S, ?> filtered = Views.filtered(automaton, Views.Filter.of(scc));
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
    BitSet rejectingSet = automaton.acceptance().rejectingSet().orElse(null);
    automaton.removeStateIf(s -> rejectingIndices.contains(sccDecomposition.index(s)));
    automaton.updateEdges((state, edge) -> {
      int sccIndex = sccDecomposition.index(state);

      if (removeTransientEdges && !sccs.get(sccIndex).contains(edge.successor())) {
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

  public static <S, A extends OmegaAcceptance> MutableAutomaton<S, A>
  removeTransientAcceptance(MutableAutomaton<S, A> automaton) {
    SccDecomposition<S> sccDecomposition = SccDecomposition.of(automaton);
    sccDecomposition.sccs(); // Force computation of SCCs before updating edges
    sccDecomposition.condensation(); // Force computation of condensation

    automaton.updateEdges((state, edge) -> {
      Set<S> scc = sccDecomposition.scc(state);
      if (sccDecomposition.isTransientScc(scc)) {
        return edge.withoutAcceptance();
      }
      return scc.contains(edge.successor()) ? edge : edge.withoutAcceptance();
    });

    automaton.trim();
    return automaton;
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

