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

package owl.translations.ldba2dpa;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.Iterables;
import de.tum.in.naturals.bitset.BitSets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.AutomatonFactory;
import owl.automaton.MutableAutomaton;
import owl.automaton.MutableAutomatonUtil;
import owl.automaton.Views;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.ParityAcceptance.Parity;
import owl.automaton.algorithms.SccDecomposition;
import owl.automaton.edge.Edge;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.automaton.util.AnnotatedState;
import owl.factories.EquivalenceClassFactory;
import owl.ltl.EquivalenceClass;
import owl.ltl.LtlLanguageExpressible;
import owl.ltl.SyntacticFragment;
import owl.translations.ltl2ldba.RecurringObligation;

public final class FlatRankingAutomaton {
  private FlatRankingAutomaton() {}

  public static <T extends LtlLanguageExpressible, A extends RecurringObligation>
  Automaton<FlatRankingState<EquivalenceClass, T>, ParityAcceptance> of(
    LimitDeterministicAutomaton<EquivalenceClass, T, BuchiAcceptance, A> ldba,
    Predicate<EquivalenceClass> isAcceptingState, boolean optimizeInitialState,
    boolean atMostOneProComponent) {
    checkArgument(ldba.initialStates().equals(ldba.initialComponent().initialStates()));
    var builder = new Builder<>(ldba, isAcceptingState, atMostOneProComponent);
    var automaton = AutomatonFactory.create(builder.ldba.acceptingComponent().factory(),
      builder.initialState, builder.acceptance, builder::edge);
    return optimizeInitialState ? optimizeInitialState(automaton) : automaton;
  }

  static final class Builder<T extends LtlLanguageExpressible, A extends RecurringObligation> {
    private static final Logger logger = Logger.getLogger(Builder.class.getName());

    ParityAcceptance acceptance;
    final FlatRankingState<EquivalenceClass, T> initialState;
    final EquivalenceClassFactory factory;
    final List<A> safetyComponents;
    final List<Set<EquivalenceClass>> initialComponentSccs;
    final Predicate<EquivalenceClass> isAcceptingState;
    final LimitDeterministicAutomaton<EquivalenceClass, T, BuchiAcceptance, A> ldba;
    final boolean atMostOneProComponent;

    Builder(LimitDeterministicAutomaton<EquivalenceClass, T, BuchiAcceptance, A> ldba,
      Predicate<EquivalenceClass> isAcceptingState, boolean atMostOneProComponent) {
      this.ldba = ldba;
      this.initialComponentSccs = SccDecomposition.computeSccs(ldba.initialComponent());
      // Identify  safety components.
      this.isAcceptingState = isAcceptingState;
      acceptance = new ParityAcceptance(2 * Math.max(1, ldba.acceptingComponent().size() + 1),
        Parity.MIN_ODD);
      EquivalenceClass ldbaInitialState = ldba.initialComponent().onlyInitialState();
      factory = ldbaInitialState.factory();
      safetyComponents = ldba.components().stream()
        .filter(RecurringObligation::isSafety)
        .sorted(RecurringObligation::compareTo)
        .collect(Collectors.toUnmodifiableList());
      logger.log(Level.FINER, "Safety Components: {0}", safetyComponents);
      this.atMostOneProComponent = atMostOneProComponent;
      initialState = edge(ldbaInitialState, List.of(), -1, null).successor();
    }

    Edge<FlatRankingState<EquivalenceClass, T>> edge(EquivalenceClass state,
      List<T> previousRanking, int previousSafetyProgress, @Nullable BitSet valuation) {
      if (isAcceptingState.test(state)) {
        return Edge.of(FlatRankingState.of(state), 1);
      }

      // We compute the relevant accepting components, which we can jump to.
      Map<A, Optional<EquivalenceClass>> existingLanguages = new HashMap<>();
      List<List<T>> targets = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
      Map<A, T> safetyTargetsLookup = new HashMap<>();

      List<T> epsilonJumps = new ArrayList<>(ldba.epsilonJumps(state));
      // Think about reversering?
      epsilonJumps.sort(Comparator.comparing(ldba::annotation, RecurringObligation::compareTo));

      for (T jumpTarget : epsilonJumps) {
        A annotation = ldba.annotation(jumpTarget);
        existingLanguages.put(annotation, Optional.empty());

        if (annotation.isSafety()) {
          if (jumpTarget.language().modalOperators().stream()
            .allMatch(SyntacticFragment.SAFETY::contains)) {
            safetyTargetsLookup.put(annotation, jumpTarget);
          } else {
            targets.get(2).add(jumpTarget);
          }
        } else if (annotation.isLiveness()) {
          targets.get(0).add(jumpTarget);
        } else {
          targets.get(1).add(jumpTarget);
        }
      }

      // Default rejecting color.
      int edgeColor = 2 * previousRanking.size();
      List<T> ranking = new ArrayList<>(previousRanking.size());

      boolean extendRanking = true;

      { // Compute componentMap successor
        ListIterator<T> iterator = previousRanking.listIterator();

        while (iterator.hasNext()) {
          assert valuation != null : "Valuation is only allowed to be null for empty rankings.";
          Edge<T> rankingEdge = ldba.acceptingComponent().edge(iterator.next(), valuation);

          if (rankingEdge == null) {
            edgeColor = Math.min(2 * iterator.previousIndex(), edgeColor);
            continue;
          }

          T rankingSuccessor = rankingEdge.successor();
          A annotation = ldba.annotation(rankingSuccessor);

          // There are no jumps to this component anymore.
          if (!existingLanguages.containsKey(annotation)) {
            edgeColor = Math.min(2 * iterator.previousIndex(), edgeColor);
            continue;
          }

          Optional<EquivalenceClass> existingLanguage = existingLanguages.get(annotation);
          assert !atMostOneProComponent || existingLanguage.isEmpty();
          EquivalenceClass stateLanguage = rankingSuccessor.language();

          if (existingLanguage.isPresent() && stateLanguage.implies(existingLanguage.get())) {
            edgeColor = Math.min(2 * iterator.previousIndex(), edgeColor);
            continue;
          }

          ranking.add(rankingSuccessor);

          if (rankingEdge.inSet(0)) {
            edgeColor = Math.min(2 * iterator.previousIndex() + 1, edgeColor);
            existingLanguages.replace(annotation, Optional.of(factory.getTrue()));
          } else {
            existingLanguages.replace(annotation, Optional.of(existingLanguage.isPresent()
              ? existingLanguage.orElseThrow().or(stateLanguage)
              : stateLanguage));
          }

          // Check last element of ranking (could be safety)
          if (!iterator.hasNext()
            && previousSafetyProgress >= 0
            && safetyComponents.indexOf(annotation) == previousSafetyProgress) {
            extendRanking = false;
          }
        }
      }

      logger.log(Level.FINER, "Ranking before extension: {0}.", ranking);

      int safetyProgress;

      // Extend the componentMap
      if (extendRanking) {
        for (T accState : Iterables.concat(targets.get(0), targets.get(1), targets.get(2))) {
          if (insertableToRanking(accState, existingLanguages)) {
            ranking.add(accState);
          }
        }

        safetyProgress = -1;

        if (!safetyComponents.isEmpty()) {
          int currentSafetyProgress = previousSafetyProgress;
          BitSet stateAP = state.atomicPropositions(true);

          for (int fuel = safetyComponents.size(); fuel > 0; fuel--) {
            currentSafetyProgress = (currentSafetyProgress + 1) % safetyComponents.size();
            A safetyComponent = safetyComponents.get(currentSafetyProgress);
            T safety = safetyTargetsLookup.get(safetyComponent);

            // Filter components which are not available anymore.
            BitSet obligationAP = new BitSet();
            safetyComponent.modalOperators().forEach(
              x -> obligationAP.or(x.atomicPropositions(true)));

            if (BitSets.isSubset(obligationAP, stateAP) && safety != null
              && insertableToRanking(safety, existingLanguages)) {
              ranking.add(safety);
              safetyProgress = currentSafetyProgress;
              break;
            }
          }
        }
      } else {
        safetyProgress = ranking.isEmpty() ? -1 : previousSafetyProgress;
      }

      logger.log(Level.FINER, "Ranking after extension: {0}.", ranking);
      assert edgeColor < acceptance.acceptanceSets();
      return Edge.of(FlatRankingState.of(state, ranking, safetyProgress), edgeColor);
    }

    @Nullable
    Edge<FlatRankingState<EquivalenceClass, T>> edge(
      FlatRankingState<EquivalenceClass, T> state, BitSet valuation) {
      // We obtain the successor of the state in the initial component.
      var edge = ldba.initialComponent().edge(state.state(), valuation);

      // The initial component moved to a rejecting sink. Thus all runs die.
      if (edge == null) {
        return null;
      }

      var successor = edge.successor();

      // If a SCC switch occurs, the ranking and the safety progress is reset.
      if (initialComponentSccs.stream()
        .anyMatch(x -> x.contains(state.state()) && !x.contains(successor))) {
        return edge(successor, List.of(), -1, valuation).withoutAcceptance();
      }

      return edge(successor, state.ranking(), state.safetyProgress(), valuation);
    }

    private boolean insertableToRanking(T state,
      Map<A, Optional<EquivalenceClass>> existingLanguages) {
      var existingLanguage = existingLanguages.get(ldba.annotation(state));

      if (existingLanguage == null || existingLanguage.isEmpty()) {
        return true;
      }

      if (atMostOneProComponent) {
        return false;
      }

      return !state.language().implies(existingLanguage.orElseThrow());
    }
  }

  private static <S extends AnnotatedState<?>, A extends OmegaAcceptance> Automaton<S, A>
    optimizeInitialState(Automaton<S, A> readOnly) {
    var originalInitialState = readOnly.onlyInitialState().state();
    MutableAutomaton<S, A> automaton = MutableAutomatonUtil.asMutable(readOnly);
    int size = automaton.size();

    for (Set<S> scc : SccDecomposition.computeSccs(automaton, false)) {
      for (S state : scc) {
        if (!originalInitialState.equals(state.state()) || !automaton.states().contains(state)) {
          continue;
        }

        int newSize = Views.replaceInitialState(automaton, Set.of(state)).size();

        if (newSize < size) {
          size = newSize;
          automaton.initialStates(Set.of(state));
          automaton.trim();
        }
      }
    }

    return automaton;
  }
}
