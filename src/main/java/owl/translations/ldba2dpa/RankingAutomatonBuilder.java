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
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.ExploreBuilder;
import owl.automaton.MutableAutomaton;
import owl.automaton.MutableAutomatonFactory;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.ParityAcceptance.Priority;
import owl.automaton.algorithms.SccDecomposition;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.collections.Trie;
import owl.factories.ValuationSetFactory;
import owl.translations.Optimisation;

public final class RankingAutomatonBuilder<S, T, U, V>
  implements ExploreBuilder<S, RankingState<S, T>, ParityAcceptance> {

  private final ParityAcceptance acceptance;
  private final Automaton<T, BuchiAcceptance> acceptingComponent;
  private final Predicate<S> clearRanking;
  private final ValuationSetFactory factory;
  private final Automaton<S, NoneAcceptance> initialComponent;
  @Nullable
  private final List<Set<S>> initialComponentSccs;
  private final List<RankingState<S, T>> initialStates;
  private final LanguageLattice<V, T, U> lattice;
  private final LimitDeterministicAutomaton<S, T, BuchiAcceptance, U> ldba;
  private final AtomicInteger sizeCounter;
  @Nullable
  private final Map<S, Trie<T>> trie;
  private final Map<U, Integer> volatileComponents;
  private final int volatileMaxIndex;
  private final boolean clearRankingAnnotation;

  public RankingAutomatonBuilder(LimitDeterministicAutomaton<S, T, BuchiAcceptance, U> ldba,
    AtomicInteger sizeCounter, EnumSet<Optimisation> optimisations,
    LanguageLattice<V, T, U> lattice, Predicate<S> clearRanking,
    boolean clearRankingAnnotation) {
    acceptingComponent = ldba.getAcceptingComponent();
    initialComponent = ldba.getInitialComponent();
    this.ldba = ldba;

    volatileComponents = new HashMap<>();

    for (U value : ldba.getComponents()) {
      assert !volatileComponents.containsKey(value);

      if (lattice.isSafetyLanguage(value)) {
        volatileComponents.put(value, volatileComponents.size());
      }
    }

    volatileMaxIndex = volatileComponents.size();

    if (optimisations.contains(Optimisation.PERMUTATION_SHARING)) {
      trie = new HashMap<>();
    } else {
      trie = null;
    }

    this.factory = ldba.getAcceptingComponent().getFactory();
    this.clearRanking = clearRanking;


    if (optimisations.contains(Optimisation.RESET_AFTER_SCC_SWITCH)) {
      initialComponentSccs = SccDecomposition.computeSccs(initialComponent);
    } else {
      initialComponentSccs = null;
    }

    acceptance = new ParityAcceptance(2, Priority.ODD);
    initialStates = new ArrayList<>();
    this.sizeCounter = sizeCounter;
    this.lattice = lattice;
    this.clearRankingAnnotation = clearRankingAnnotation;
  }

  @Override
  public RankingState<S, T> add(S stateKey) {
    Preconditions.checkState(initialStates.isEmpty(), "At most one initial state is supported.");

    List<T> ranking = new ArrayList<>();
    int index = clearRanking.test(stateKey) ? 0 : appendJumps(stateKey, ranking);
    RankingState<S, T> initialState = RankingState.create(stateKey, ranking, index, trie);
    initialStates.add(initialState);
    return initialState;
  }

  private int appendJumps(S state, List<T> ranking) {
    return appendJumps(state, ranking, Collections.emptyMap(), -1);
  }

  private int appendJumps(S state, List<T> ranking, Map<U, Language<V>> existingLanguages,
    int currentVolatileIndex) {
    List<T> pureEventual = new ArrayList<>();
    List<T> mixed = new ArrayList<>();
    T nextVolatileState = null;
    int nextVolatileStateIndex = -1;

    for (T accState : ldba.getEpsilonJumps(state)) {
      U obligations = ldba.getAnnotation(accState);
      Integer candidateIndex = volatileComponents.get(obligations);

      // It is a volatile state
      if (candidateIndex != null && lattice.getLanguage(accState, true).isTop()) {
        // assert accState.getCurrent().isTrue() : "LTL2LDBA translation is malfunctioning.
        // This state should be suppressed.";

        // The distance is too large...
        if (nextVolatileState != null
          && distance(currentVolatileIndex, candidateIndex) >= distance(currentVolatileIndex,
          nextVolatileStateIndex)) {
          continue;
        }

        Language<V> existingLanguage = existingLanguages.get(obligations);

        if (existingLanguage == null
          || !existingLanguage.greaterOrEqual(lattice.getLanguage(accState, false))) {
          nextVolatileStateIndex = candidateIndex;
          nextVolatileState = accState;
          continue;
        }

        continue;
      }

      Language<V> existingClass = existingLanguages.get(obligations);
      Language<V> stateClass = lattice.getLanguage(accState, false);

      if (existingClass != null && existingClass.greaterOrEqual(stateClass)) {
        continue;
      }

      if (lattice.isLivenessLanguage(obligations)) {
        pureEventual.add(accState);
      } else {
        mixed.add(accState);
      }
    }

    Set<T> suffixes = new HashSet<>(mixed);
    suffixes.addAll(pureEventual);

    if (nextVolatileState != null) {
      suffixes.add(nextVolatileState);
    }

    //noinspection OptionalContainsCollection
    Optional<List<T>> append;

    if (trie == null) {
      append = Optional.empty();
    } else {
      append = trie.computeIfAbsent(state, x -> new Trie<>()).suffix(ranking, suffixes);
    }

    if (append.isPresent()) {
      assert Collections.disjoint(ranking, append.get()) :
        "State already present in ranking: " + append.get();
      ranking.addAll(append.get());
    } else {
      // Impose stable but arbitrary order.
      pureEventual.sort(Comparator.comparingInt(o -> Objects.hashCode(ldba.getAnnotation(o))));
      mixed.sort(Comparator.comparingInt(o -> Objects.hashCode(ldba.getAnnotation(o))));

      assert Collections.disjoint(ranking, pureEventual) :
        "State already present in ranking: " + pureEventual;
      ranking.addAll(pureEventual);
      assert Collections.disjoint(ranking, mixed) : "State already present in ranking: " + mixed;
      ranking.addAll(mixed);

      if (nextVolatileState != null) {
        assert !ranking.contains(nextVolatileState) :
          "State already present in ranking: " + nextVolatileState;
        ranking.add(nextVolatileState);
      }
    }

    if (nextVolatileStateIndex >= 0) {
      return nextVolatileStateIndex;
    }

    return 0;
  }

  @Override
  public MutableAutomaton<RankingState<S, T>, ParityAcceptance> build() {
    MutableAutomaton<RankingState<S, T>, ParityAcceptance> automaton = MutableAutomatonFactory
      .createMutableAutomaton(acceptance, factory);
    // TODO: add getSensitiveAlphabet Method
    AutomatonUtil.exploreDeterministic(automaton, initialStates, this::getSuccessor, sizeCounter);
    automaton.setInitialStates(initialStates);
    List<Set<RankingState<S, T>>> sccs = SccDecomposition.computeSccs(automaton, false);
    S initialState = Iterables.getOnlyElement(initialStates).state;

    for (Set<RankingState<S, T>> scc : Lists.reverse(sccs)) {
      scc.stream().filter(x -> x.state.equals(initialState))
        .findAny().ifPresent(automaton::setInitialState);
    }

    //automaton.setInitialStates(initialStates);
    automaton.removeUnreachableStates();
    return automaton;
  }

  private boolean detectSccSwitch(S state, S successor) {
    if (initialComponentSccs == null) {
      return false;
    }

    Set<S> scc = initialComponentSccs.stream()
      .filter(x -> x.contains(state)).findAny().orElse(Collections.emptySet());
    return !scc.contains(successor);
  }

  private int distance(int base, int index) {
    int distanceIndex = index;

    if (base >= distanceIndex) {
      distanceIndex += volatileMaxIndex + 1;
    }

    return distanceIndex - base;
  }

  /* IDEAS:
   * - suppress jump if the current goal only contains literals and X.
   * - filter in the initial state, when jumps are done.
   * - detect if initial component is true -> move to according state.
   * - move "volatile" formulas to the back. select "infinity" formulas to stay upfront.
   * - if a monitor couldn't be reached from a jump, delete it.
   */
  @Nullable
  private Edge<RankingState<S, T>> getSuccessor(RankingState<S, T> state, BitSet valuation) {
    S successor;

    { // We obtain the successor of the state in the initial component.
      Edge<S> edge = initialComponent.getEdge(state.state, valuation);

      // The initial component moved to a rejecting sink. Thus all runs die.
      if (edge == null) {
        return null;
      }

      successor = edge.getSuccessor();

      // If we reached the state "true" or something that is a safety condition, we drop all jumps.
      if (edge.inSet(0)) {
        return Edges.create(RankingState.create(successor), 1);
      }
    }

    // We compute the relevant accepting components, which we can jump to.
    Map<U, Language<V>> existingLanguages = new HashMap<>();
    Language<V> emptyLanguage = lattice.getBottom();
    boolean sccSwitch = detectSccSwitch(state.state, successor);
    boolean dropRanking = clearRanking.test(successor);

    for (T jumpTarget : ldba.getEpsilonJumps(successor)) {
      existingLanguages.put(ldba.getAnnotation(jumpTarget), emptyLanguage);
    }

    // Default rejecting color.
    int edgeColor = 2 * state.ranking.size();
    List<T> ranking = new ArrayList<>(state.ranking.size());
    boolean activeVolatileComponent = false;
    int nextVolatileIndex = 0;
    int index = -1;

    if (!sccSwitch && !dropRanking) {
      for (T current : state.ranking) {
        index++;
        Edge<T> edge = acceptingComponent.getEdge(current, valuation);

        if (edge == null) {
          edgeColor = Math.min(2 * index, edgeColor);
          continue;
        }

        T rankingSuccessor = edge.getSuccessor();
        @Nullable
        U annotation = ldba.getAnnotation(rankingSuccessor);
        Language<V> existingLanguage = existingLanguages.get(annotation);

        if (existingLanguage == null
          || existingLanguage.greaterOrEqual(lattice.getLanguage(rankingSuccessor, false))) {
          edgeColor = Math.min(2 * index, edgeColor);
          continue;
        }

        existingLanguages.replace(annotation,
          existingLanguage.join(lattice.getLanguage(rankingSuccessor, true)));
        assert !ranking.contains(rankingSuccessor) :
          "State already present in ranking: " + rankingSuccessor;
        ranking.add(rankingSuccessor);

        if (isVolatileState(rankingSuccessor)) {
          activeVolatileComponent = true;
          nextVolatileIndex = volatileComponents.get(annotation);
        }

        if (edge.inSet(0)) {
          edgeColor = Math.min(2 * index + 1, edgeColor);

          if (annotation != null && clearRankingAnnotation) {
            existingLanguages.replace(annotation, lattice.getTop());
          }
        }
      }
    }


    if (!activeVolatileComponent && !dropRanking) {
      nextVolatileIndex = appendJumps(successor, ranking, existingLanguages,
        sccSwitch ? -1 : state.volatileIndex);
    }

    if (edgeColor >= acceptance.getAcceptanceSets()) {
      acceptance.setAcceptanceSets(edgeColor + 1);
    }

    existingLanguages.forEach((x, y) -> y.free());

    RankingState<S, T> successorState = RankingState
      .create(successor, ranking, nextVolatileIndex, trie);

    if (dropRanking) {
      return Edges.create(successorState, 1);
    }

    if (sccSwitch) {
      return Edges.create(successorState);
    }

    return Edges.create(successorState, edgeColor);
  }

  private boolean isVolatileState(T state) {
    return volatileComponents.containsKey(ldba.getAnnotation(state)) && lattice.getLanguage(state,
      true).isTop();
  }
}

