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
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import owl.automaton.AutomatonUtil;
import owl.automaton.ExploreBuilder;
import owl.automaton.MutableAutomaton;
import owl.automaton.MutableAutomatonFactory;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.ParityAcceptance.Priority;
import owl.automaton.algorithms.SccDecomposition;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.translations.Optimisation;

public final class RankingAutomatonBuilder<S, T, U, V>
  implements ExploreBuilder<S, RankingState<S, T>, ParityAcceptance> {
  private static final Logger logger = Logger.getLogger(RankingAutomatonBuilder.class.getName());

  private final ParityAcceptance acceptance;
  @Nullable
  private final List<Set<S>> initialComponentSccs;
  private final Predicate<S> isAcceptingState;
  private final LanguageLattice<V, T, U> lattice;
  private final LimitDeterministicAutomaton<S, T, BuchiAcceptance, U> ldba;
  private final boolean resetRanking;
  private final List<U> safetyComponents;
  private final AtomicInteger sizeCounter;
  private final List<U> sortingOrder;
  @Nullable
  private RankingState<S, T> initialState = null;

  public RankingAutomatonBuilder(LimitDeterministicAutomaton<S, T, BuchiAcceptance, U> ldba,
    AtomicInteger sizeCounter, EnumSet<Optimisation> optimisations,
    LanguageLattice<V, T, U> lattice, Predicate<S> isAcceptingState,
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

    initialComponentSccs = optimisations.contains(Optimisation.RESET_AFTER_SCC_SWITCH)
      ? SccDecomposition.computeSccs(this.ldba.getInitialComponent())
      : null;

    acceptance = new ParityAcceptance(2, Priority.ODD);

    this.sizeCounter = sizeCounter;
    this.lattice = lattice;
    this.resetRanking = resetRanking;
  }

  @Override
  public RankingState<S, T> add(S state) {
    Preconditions.checkState(initialState == null, "At most one initial state is supported.");
    initialState = buildEdge(state, Collections.emptyList(), -1, null).getSuccessor();
    return initialState;
  }

  @Override
  public MutableAutomaton<RankingState<S, T>, ParityAcceptance> build() {
    MutableAutomaton<RankingState<S, T>, ParityAcceptance> automaton =
      MutableAutomatonFactory.createMutableAutomaton(acceptance,
        ldba.getAcceptingComponent().getFactory());

    if (initialState == null) {
      return automaton;
    }

    // TODO: add getSensitiveAlphabet Method
    AutomatonUtil.exploreDeterministic(automaton, Collections.singletonList(initialState),
      this::getSuccessor, sizeCounter);
    automaton.setInitialState(initialState);
    List<Set<RankingState<S, T>>> sccs = SccDecomposition.computeSccs(automaton, false);

    for (Set<RankingState<S, T>> scc : Lists.reverse(sccs)) {
      scc.stream().filter(x -> Objects.equals(x.state, initialState))
        .findAny().ifPresent(automaton::setInitialState);
    }

    automaton.removeUnreachableStates();
    return automaton;
  }

  private Edge<RankingState<S, T>> buildEdge(S state, List<T> previousRanking,
    int previousSafetyProgress, @Nullable BitSet valuation) {
    if (isAcceptingState.test(state)) {
      return Edges.create(RankingState.create(state), 1);
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

      for (T previousState; iterator.hasNext(); ) {

        assert valuation != null : "Valuation is only allowed to be null for empty rankings.";
        previousState = iterator.next();
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
            new Object[]{rankingState, safetyIndex});

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
    return Edges.create(RankingState.create(state, ranking, safetyProgress), edgeColor);
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
      return Edges.create(buildEdge(successor, Collections.emptyList(), -1,
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

