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

package owl.translations.ldba2dpa;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.AutomatonFactory;
import owl.automaton.AutomatonUtil;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.ParityAcceptance.Parity;
import owl.automaton.algorithms.SccDecomposition;
import owl.automaton.edge.Edge;
import owl.automaton.ldba.LimitDeterministicAutomaton;

public final class RankingAutomaton<S, T, U, V> {
  private static final Logger logger = Logger.getLogger(RankingAutomaton.class.getName());

  private final ParityAcceptance acceptance;
  @Nullable
  private final List<Set<S>> initialComponentSccs;
  private final Predicate<S> isAcceptingState;
  private final LanguageLattice<V, T, U> lattice;
  private final LimitDeterministicAutomaton<S, T, BuchiAcceptance, U> ldba;
  private final boolean resetRanking;
  private final List<U> safetyComponents;
  private final List<U> sortingOrder;
  @Nullable
  private RankingState<S, T> initialState = null;

  private RankingAutomaton(LimitDeterministicAutomaton<S, T, BuchiAcceptance, U> ldba,
    boolean resetAfterSccSwitch, LanguageLattice<V, T, U> lattice, Predicate<S> isAcceptingState,
    boolean resetRanking) {
    this.ldba = ldba;

    sortingOrder = ImmutableList.copyOf(ldba.getComponents());

    // Identify  safety components.
    ImmutableList.Builder<U> safetyBuilder = ImmutableList.builder();

    for (U value : sortingOrder) {
      if (lattice.isSafetyAnnotation(value)) {
        safetyBuilder.add(value);
      }
    }

    safetyComponents = safetyBuilder.build();
    logger.log(Level.FINER, "Safety Components: {0}", safetyComponents);

    this.isAcceptingState = isAcceptingState;

    initialComponentSccs = resetAfterSccSwitch
                           ? SccDecomposition.computeSccs(this.ldba.getInitialComponent())
                           : null;

    acceptance = new ParityAcceptance(2, Parity.MIN_ODD);

    this.lattice = lattice;
    this.resetRanking = resetRanking;
  }

  public static <S, T, U, V> Automaton<RankingState<S, T>, ParityAcceptance> of(
    LimitDeterministicAutomaton<S, T, BuchiAcceptance, U> ldba,
    boolean resetAfterSccSwitch,
    LanguageLattice<V, T, U> lattice, Predicate<S> isAcceptingState,
    boolean resetRanking, boolean optimizeInitialState) {
    RankingAutomaton<S, T, U, V> builder = new RankingAutomaton<>(ldba, resetAfterSccSwitch,
      lattice, isAcceptingState, resetRanking);

    Preconditions
      .checkState(builder.initialState == null, "At most one initial state is supported.");
    builder.initialState = builder.buildEdge(ldba.getInitialComponent().getInitialState(),
      List.of(), -1, null).getSuccessor();

    // TODO: add getSensitiveAlphabet Method
    Automaton<RankingState<S, T>, ParityAcceptance> automaton = AutomatonFactory
      .createStreamingAutomaton(builder.acceptance, builder.initialState,
        builder.ldba.getAcceptingComponent().getFactory(), builder::getSuccessor);

    if (optimizeInitialState) {
      return optimizeInitialState(automaton);
    }

    return automaton;
  }

  private static <S, T> Automaton<RankingState<S, T>, ParityAcceptance> optimizeInitialState(
    Automaton<RankingState<S, T>, ParityAcceptance> readOnly) {
    S originalInitialState = readOnly.getInitialState().state;

    if (originalInitialState == null) {
      return readOnly;
    }

    MutableAutomaton<RankingState<S, T>, ParityAcceptance> automaton =
      AutomatonUtil.asMutable(readOnly);

    RankingState<S, T> potentialInitialState = automaton.getInitialState();
    int size = automaton.size();

    for (Set<RankingState<S, T>> scc : SccDecomposition.computeSccs(automaton, false)) {
      for (RankingState<S, T> state : scc) {
        if (!originalInitialState.equals(state.state)) {
          continue;
        }

        int newSize = AutomatonUtil.getReachableStates(automaton, Set.of(state)).size();

        if (newSize < size) {
          size = newSize;
          potentialInitialState = state;
        }
      }
    }

    automaton.setInitialState(potentialInitialState);
    automaton.removeUnreachableStates();
    return automaton;
  }

  private Edge<RankingState<S, T>> buildEdge(S state, List<T> previousRanking,
    int previousSafetyProgress, @Nullable BitSet valuation) {
    if (isAcceptingState.test(state)) {
      return Edge.of(RankingState.of(state), 1);
    }

    // We compute the relevant accepting components, which we can jump to.
    Map<U, Language<V>> existingLanguages = new HashMap<>();
    List<T> safetyTargets = new ArrayList<>();
    List<T> livenessTargets = new ArrayList<>();
    List<T> mixedTargets = new ArrayList<>();
    Language<V> emptyLanguage = lattice.getBottom();

    List<T> epsilonJumps = new ArrayList<>(ldba.getEpsilonJumps(state));
    epsilonJumps.sort(Comparator.comparingInt(x -> sortingOrder.indexOf(ldba.getAnnotation(x))));

    for (T jumpTarget : epsilonJumps) {
      existingLanguages.put(ldba.getAnnotation(jumpTarget), emptyLanguage);

      if (lattice.acceptsSafetyLanguage(jumpTarget)) {
        safetyTargets.add(jumpTarget);
      } else if (lattice.acceptsLivenessLanguage(jumpTarget)) {
        livenessTargets.add(jumpTarget);
      } else {
        mixedTargets.add(jumpTarget);
      }
    }

    // Default rejecting color.
    int edgeColor = 2 * previousRanking.size();
    List<T> ranking = new ArrayList<>(previousRanking.size());
    int safetyProgress = -1;

    boolean extendRanking = true;

    { // Compute ranking successor
      ListIterator<T> iterator = previousRanking.listIterator();

      while (iterator.hasNext()) {
        assert valuation != null : "Valuation is only allowed to be null for empty rankings.";
        T previousState = iterator.next();
        Edge<T> edge = ldba.getAcceptingComponent().getEdge(previousState, valuation);

        if (edge == null) {
          edgeColor = Math.min(2 * iterator.previousIndex(), edgeColor);
          continue;
        }

        T rankingState = edge.getSuccessor();
        @Nullable
        U annotation = ldba.getAnnotation(rankingState);
        Language<V> existingLanguage = existingLanguages.get(annotation);

        if (existingLanguage == null
          || existingLanguage.greaterOrEqual(lattice.getLanguage(rankingState))) {
          edgeColor = Math.min(2 * iterator.previousIndex(), edgeColor);
          continue;
        }

        existingLanguages.replace(annotation,
          existingLanguage.join(lattice.getLanguage(rankingState)));
        ranking.add(rankingState);

        if (lattice.acceptsSafetyLanguage(rankingState) && !iterator.hasNext()) {
          int safetyIndex = safetyComponents.indexOf(annotation);
          edgeColor = Math.min(2 * iterator.previousIndex() + 1, edgeColor);

          logger.log(Level.FINER, "Found safety language {0} with safety index {1}.",
            new Object[] {rankingState, safetyIndex});

          if (resetRanking) {
            existingLanguages.replace(annotation, lattice.getTop());
          }

          if (previousSafetyProgress == safetyIndex) {
            safetyProgress = previousSafetyProgress;
            extendRanking = false;
            break;
          }
        } else if (edge.inSet(0)) {
          edgeColor = Math.min(2 * iterator.previousIndex() + 1, edgeColor);

          if (resetRanking) {
            existingLanguages.replace(annotation, lattice.getTop());
          }
        }
      }
    }

    logger.log(Level.FINER, "Ranking before extension: {0}.", ranking);

    // Extend the ranking
    if (extendRanking) {
      for (T accState : livenessTargets) {
        if (insertableToRanking(accState, existingLanguages)) {
          ranking.add(accState);
        }
      }

      for (T accState : mixedTargets) {
        if (insertableToRanking(accState, existingLanguages)) {
          ranking.add(accState);
        }
      }

      T safety = findNextSafety(safetyTargets, previousSafetyProgress + 1);

      // Wrap around; restart search.
      if (safety == null) {
        safety = findNextSafety(safetyTargets, 0);
      }

      if (safety != null) {
        safetyProgress = safetyComponents.indexOf(ldba.getAnnotation(safety));

        if (insertableToRanking(safety, existingLanguages)) {
          ranking.add(safety);
        }
      }
    }

    logger.log(Level.FINER, "Ranking after extension: {0}.", ranking);

    existingLanguages.forEach((x, y) -> y.free());
    return Edge.of(RankingState.of(state, ranking, safetyProgress), edgeColor);
  }

  @Nullable
  private T findNextSafety(List<T> availableJumps, int i) {
    for (U annotation : safetyComponents.subList(i, safetyComponents.size())) {
      for (T state : availableJumps) {
        assert lattice.acceptsSafetyLanguage(state);

        U stateAnnotation = ldba.getAnnotation(state);

        if (annotation.equals(stateAnnotation)) {
          return state;
        }
      }
    }

    return null;
  }

  @Nullable
  private Edge<RankingState<S, T>> getSuccessor(RankingState<S, T> state, BitSet valuation) {
    if (state.state == null) {
      return null;
    }

    S successor;

    { // We obtain the successor of the state in the initial component.
      Edge<S> edge = ldba.getInitialComponent().getEdge(state.state, valuation);

      // The initial component moved to a rejecting sink. Thus all runs die.
      if (edge == null) {
        return null;
      }

      successor = edge.getSuccessor();
    }

    // If a SCC switch occurs, the ranking and the safety progress is reset.
    if (resetRanking && sccSwitchOccurred(state.state, successor)) {
      return Edge.of(buildEdge(successor, List.of(), -1,
        valuation).getSuccessor());
    }

    Edge<RankingState<S, T>> edge = buildEdge(successor, state.ranking, state.safetyProgress,
      valuation);

    if (edge.largestAcceptanceSet() >= acceptance.getAcceptanceSets()) {
      acceptance.setAcceptanceSets(edge.largestAcceptanceSet() + 1);
    }

    return edge;
  }

  private boolean insertableToRanking(T state, Map<U, Language<V>> existingLanguages) {
    Language<V> existingClass = existingLanguages.get(ldba.getAnnotation(state));
    Language<V> stateClass = lattice.getLanguage(state);
    return existingClass == null || !existingClass.greaterOrEqual(stateClass);
  }

  private boolean sccSwitchOccurred(S state, S successor) {
    return initialComponentSccs != null && initialComponentSccs.stream()
      .anyMatch(x -> x.contains(state) && !x.contains(successor));
  }
}

