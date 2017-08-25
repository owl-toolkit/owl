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

package owl.translations.ltl2dpa;

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

import javax.annotation.Nullable;

import owl.automaton.Automaton;
import owl.automaton.AutomatonFactory;
import owl.automaton.AutomatonUtil;
import owl.automaton.ExploreBuilder;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.ParityAcceptance.Priority;
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
  private final ValuationSetFactory factory;
  private final Automaton<S, NoneAcceptance> initialComponent;
  private final List<RankingState<S, T>> initialStates;
  private final LimitDeterministicAutomaton<S, T, BuchiAcceptance, U> ldba;
  private final AtomicInteger sizeCounter;
  @Nullable
  private final Map<S, Trie<T>> trie;
  private final Map<U, Integer> volatileComponents;
  private final int volatileMaxIndex;
  private final LanguageOracle<V, T, U> oracle;

  private RankingAutomatonBuilder(LimitDeterministicAutomaton<S, T, BuchiAcceptance, U> ldba,
    AtomicInteger sizeCounter, EnumSet<Optimisation> optimisations, ValuationSetFactory factory,
    LanguageOracle<V, T, U> oracle) {
    acceptingComponent = ldba.getAcceptingComponent();
    initialComponent = ldba.getInitialComponent();
    this.ldba = ldba;

    volatileComponents = new HashMap<>();

    for (U value : ldba.getComponents()) {
      assert !volatileComponents.containsKey(value);

      if (oracle.isPureSafety(value)) {
        volatileComponents.put(value, volatileComponents.size());
      }
    }

    volatileMaxIndex = volatileComponents.size();

    if (optimisations.contains(Optimisation.PERMUTATION_SHARING)) {
      trie = new HashMap<>();
    } else {
      trie = null;
    }

    this.factory = factory;

    acceptance = new ParityAcceptance(2, Priority.ODD);
    initialStates = new ArrayList<>();
    this.sizeCounter = sizeCounter;
    this.oracle = oracle;
  }

  public static <S, T, U, V> RankingAutomatonBuilder<S, T, U, V> create(
    LimitDeterministicAutomaton<S, T, BuchiAcceptance, U> ldba,
    AtomicInteger sizeCounter, EnumSet<Optimisation> optimisations, ValuationSetFactory factory,
    LanguageOracle<V, T, U> oracle) {
    return new RankingAutomatonBuilder<>(ldba, sizeCounter, optimisations, factory, oracle);
  }

  @Override
  public RankingState<S, T> add(S stateKey) {
    List<T> ranking = new ArrayList<>();
    int index = appendJumps(stateKey, ranking);
    RankingState<S, T> initialState = RankingState.create(stateKey, ranking, index, trie);
    initialStates.add(initialState);
    return initialState;
  }

  private int appendJumps(S state, List<T> ranking) {
    return appendJumps(state, ranking, Collections.emptyMap(), -1);
  }

  private int appendJumps(S state, List<T> ranking,
    Map<U, Language<V>> existingClasses, int currentVolatileIndex) {
    List<T> pureEventual = new ArrayList<>();
    List<T> mixed = new ArrayList<>();
    T nextVolatileState = null;
    int nextVolatileStateIndex = -1;

    for (T accState : ldba.getEpsilonJumps(state)) {
      U obligations = ldba.getAnnotation(accState);
      Integer candidateIndex = volatileComponents.get(obligations);

      // It is a volatile state
      if (candidateIndex != null && oracle.getLanguage(accState, true).isUniverse()) {
        // assert accState.getCurrent().isTrue() : "LTL2LDBA translation is malfunctioning.
        // This state should be suppressed.";

        // The distance is too large...
        if (nextVolatileState != null
          && distance(currentVolatileIndex, candidateIndex) >= distance(currentVolatileIndex,
          nextVolatileStateIndex)) {
          continue;
        }

        Language<V> existingClass = existingClasses.get(obligations);

        if (existingClass == null || !existingClass.contains(oracle.getLanguage(accState, false))) {
          nextVolatileStateIndex = candidateIndex;
          nextVolatileState = accState;
          continue;
        }

        continue;
      }

      Language<V> existingClass = existingClasses.get(obligations);
      Language<V> stateClass = oracle.getLanguage(accState, false);

      if (existingClass != null && existingClass.contains(stateClass)) {
        continue;
      }

      if (oracle.isPureLiveness(obligations) && oracle.getLanguage(accState, true)
          .isCosafetyLanguage()) {
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
    MutableAutomaton<RankingState<S, T>, ParityAcceptance> automaton = AutomatonFactory
      .createMutableAutomaton(acceptance, factory);
    // TODO: add getSensitiveAlphabet Method
    AutomatonUtil.exploreDeterministic(automaton, initialStates, this::getSuccessor, sizeCounter);
    automaton.setInitialStates(initialStates);
    return automaton;
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
    Language<V> emptyLanguage = oracle.getEmpty();

    for (T jumpTarget : ldba.getEpsilonJumps(successor)) {
      existingLanguages.put(ldba.getAnnotation(jumpTarget), emptyLanguage);
    }

    // Default rejecting color.
    int edgeColor = 2 * state.ranking.size();
    List<T> ranking = new ArrayList<>(state.ranking.size());
    boolean activeVolatileComponent = false;
    int nextVolatileIndex = 0;
    int index = -1;

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
        || existingLanguage.contains(oracle.getLanguage(rankingSuccessor, false))) {
        edgeColor = Math.min(2 * index, edgeColor);
        continue;
      }

      existingLanguages.replace(annotation,
        existingLanguage.union(oracle.getLanguage(rankingSuccessor, true)));
      assert !ranking.contains(rankingSuccessor) :
        "State already present in ranking: " + rankingSuccessor;
      ranking.add(rankingSuccessor);

      if (isVolatileState(rankingSuccessor)) {
        activeVolatileComponent = true;
        nextVolatileIndex = volatileComponents.get(annotation);
      }

      if (edge.inSet(0)) {
        edgeColor = Math.min(2 * index + 1, edgeColor);

        if (annotation != null) {
          existingLanguages.replace(annotation, oracle.getUniverse());
        }
      }
    }

    if (!activeVolatileComponent) {
      nextVolatileIndex = appendJumps(successor, ranking, existingLanguages, state.volatileIndex);
    }

    if (edgeColor >= acceptance.getAcceptanceSets()) {
      acceptance.setAcceptanceSets(edgeColor + 1);
    }

    existingLanguages.forEach((x, y) -> y.free());

    return Edges
      .create(RankingState.create(successor, ranking, nextVolatileIndex, trie), edgeColor);
  }

  private boolean isVolatileState(T state) {
    return volatileComponents.containsKey(ldba.getAnnotation(state)) && oracle.getLanguage(state,
        true).isUniverse();
  }
}

