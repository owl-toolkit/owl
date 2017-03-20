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
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.translations.Optimisation;
import owl.translations.ltl2ldba.DegeneralizedBreakpointState;
import owl.translations.ltl2ldba.RecurringObligations;

final class RankingAutomatonBuilder
  implements ExploreBuilder<EquivalenceClass, RankingState, ParityAcceptance> {

  private final ParityAcceptance acceptance;
  private final Automaton<DegeneralizedBreakpointState, BuchiAcceptance> acceptingComponent;
  private final ValuationSetFactory factory;
  private final Automaton<EquivalenceClass, NoneAcceptance> initialComponent;
  private final List<RankingState> initialStates;
  private final LimitDeterministicAutomaton<EquivalenceClass,
    DegeneralizedBreakpointState, BuchiAcceptance, RecurringObligations> ldba;
  private final AtomicInteger sizeCounter;
  @Nullable
  private final Map<EquivalenceClass, Trie<DegeneralizedBreakpointState>> trie;
  private final Map<RecurringObligations, Integer> volatileComponents;
  private final int volatileMaxIndex;

  private RankingAutomatonBuilder(
    LimitDeterministicAutomaton<EquivalenceClass, DegeneralizedBreakpointState, BuchiAcceptance,
      RecurringObligations> ldba,
    AtomicInteger sizeCounter, EnumSet<Optimisation> optimisations, ValuationSetFactory factory) {
    acceptingComponent = ldba.getAcceptingComponent();
    initialComponent = ldba.getInitialComponent();
    this.ldba = ldba;

    volatileComponents = new HashMap<>();

    for (RecurringObligations value : ldba.getComponents()) {
      assert !volatileComponents.containsKey(value);

      if (value.isPureSafety()) {
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
  }

  public static RankingAutomatonBuilder create(
    LimitDeterministicAutomaton<EquivalenceClass,
      DegeneralizedBreakpointState, BuchiAcceptance, RecurringObligations> ldba,
    AtomicInteger sizeCounter, EnumSet<Optimisation> optimisations, ValuationSetFactory factory) {
    return new RankingAutomatonBuilder(ldba, sizeCounter, optimisations, factory);
  }

  @Override
  public RankingState add(EquivalenceClass stateKey) {
    List<DegeneralizedBreakpointState> ranking = new ArrayList<>();
    int index = appendJumps(stateKey, ranking);
    RankingState initialState = RankingState.create(stateKey, ranking, index, trie);
    initialStates.add(initialState);
    return initialState;
  }

  private int appendJumps(EquivalenceClass state, List<DegeneralizedBreakpointState> ranking) {
    return appendJumps(state, ranking, Collections.emptyMap(), -1);
  }

  private int appendJumps(EquivalenceClass state, List<DegeneralizedBreakpointState> ranking,
    Map<RecurringObligations, EquivalenceClass> existingClasses, int currentVolatileIndex) {
    List<DegeneralizedBreakpointState> pureEventual = new ArrayList<>();
    List<DegeneralizedBreakpointState> mixed = new ArrayList<>();
    DegeneralizedBreakpointState nextVolatileState = null;
    int nextVolatileStateIndex = -1;

    for (DegeneralizedBreakpointState accState : ldba.getEpsilonJumps(state)) {
      RecurringObligations obligations = ldba.getAnnotation(accState);
      Integer candidateIndex = volatileComponents.get(obligations);

      // It is a volatile state
      if (candidateIndex != null && accState.getCurrent().isTrue()) {
        // assert accState.getCurrent().isTrue() : "LTL2LDBA translation is malfunctioning.
        // This state should be suppressed.";

        // The distance is too large...
        if (nextVolatileState != null
          && distance(currentVolatileIndex, candidateIndex) >= distance(currentVolatileIndex,
          nextVolatileStateIndex)) {
          continue;
        }

        EquivalenceClass existingClass = existingClasses.get(obligations);

        if (existingClass == null || !accState.getLabel().implies(existingClass)) {
          nextVolatileStateIndex = candidateIndex;
          nextVolatileState = accState;
          continue;
        }

        continue;
      }

      EquivalenceClass existingClass = existingClasses.get(obligations);
      EquivalenceClass stateClass = accState.getLabel();

      if (existingClass != null && stateClass.implies(existingClass)) {
        continue;
      }

      if (obligations.isPureLiveness() && (accState.getCurrent()
        .testSupport(Formula::isPureEventual))) {
        pureEventual.add(accState);
      } else {
        mixed.add(accState);
      }
    }

    Set<DegeneralizedBreakpointState> suffixes = new HashSet<>(mixed);
    suffixes.addAll(pureEventual);

    if (nextVolatileState != null) {
      suffixes.add(nextVolatileState);
    }

    //noinspection OptionalContainsCollection
    Optional<List<DegeneralizedBreakpointState>> append;

    if (trie == null) {
      append = Optional.empty();
    } else {
      append = trie
        .computeIfAbsent(state, x -> new Trie<>())
        .suffix(ranking, suffixes);
    }

    if (append.isPresent()) {
      assert Collections.disjoint(ranking, append.get()) :
        "State already present in ranking: " + append.get();
      ranking.addAll(append.get());
    } else {
      // Impose stable but arbitrary order.
      pureEventual.sort(Comparator.comparingInt(o -> ldba.getAnnotation(o).hashCode()));
      mixed.sort(Comparator.comparingInt(o -> ldba.getAnnotation(o).hashCode()));

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
  public MutableAutomaton<RankingState, ParityAcceptance> build() {
    MutableAutomaton<RankingState, ParityAcceptance> automaton = AutomatonFactory
      .create(acceptance, factory);
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
  private Edge<RankingState> getSuccessor(RankingState state, BitSet valuation) {
    // We obtain the successor of the state in the initial component.
    EquivalenceClass successor = initialComponent.getSuccessor(state.state, valuation);

    // The initial component moved to a rejecting sink. Thus all runs die.
    if (successor == null) {
      return null;
    }

    // If we reached the state "true": we're done and can loop forever.
    if (successor.isTrue()) {
      return Edges.create(RankingState.create(successor), 1);
    }

    // We compute the relevant accepting components, which we can jump to.
    Map<RecurringObligations, EquivalenceClass> existingClasses = new HashMap<>();
    EquivalenceClass falseClass = successor.getFactory().getFalse();

    for (DegeneralizedBreakpointState jumpTarget : ldba.getEpsilonJumps(successor)) {
      existingClasses.put(ldba.getAnnotation(jumpTarget), falseClass);
    }

    // Default rejecting color.
    int edgeColor = 2 * state.ranking.size();
    List<DegeneralizedBreakpointState> ranking = new ArrayList<>(state.ranking.size());
    boolean activeVolatileComponent = false;
    int nextVolatileIndex = 0;
    int index = -1;

    for (DegeneralizedBreakpointState current : state.ranking) {
      index++;
      Edge<DegeneralizedBreakpointState> edge = acceptingComponent.getEdge(current, valuation);

      if (edge == null) {
        edgeColor = Math.min(2 * index, edgeColor);
        continue;
      }

      DegeneralizedBreakpointState rankingSuccessor = edge.getSuccessor();
      RecurringObligations obligations = ldba.getAnnotation(rankingSuccessor);
      EquivalenceClass existingClass = existingClasses.get(obligations);

      if (existingClass == null || rankingSuccessor.getLabel().implies(existingClass)) {
        edgeColor = Math.min(2 * index, edgeColor);
        continue;
      }

      existingClasses.replace(obligations, existingClass.orWith(rankingSuccessor.getCurrent()));
      assert !ranking.contains(rankingSuccessor) :
        "State already present in ranking: " + rankingSuccessor;
      ranking.add(rankingSuccessor);

      if (isVolatileState(rankingSuccessor)) {
        activeVolatileComponent = true;
        nextVolatileIndex = volatileComponents.get(obligations);
      }

      if (edge.inSet(0)) {
        edgeColor = Math.min(2 * index + 1, edgeColor);
        existingClasses.replace(obligations, successor.getFactory().getTrue());
      }
    }

    if (!activeVolatileComponent) {
      nextVolatileIndex = appendJumps(successor, ranking, existingClasses, state.volatileIndex);
    }

    if (edgeColor >= acceptance.getAcceptanceSets()) {
      acceptance.setAcceptanceSets(edgeColor + 1);
    }

    existingClasses.forEach((x, y) -> y.free());

    return Edges
      .create(RankingState.create(successor, ranking, nextVolatileIndex, trie), edgeColor);
  }

  private boolean isVolatileState(DegeneralizedBreakpointState state) {
    return volatileComponents.containsKey(ldba.getAnnotation(state)) && state.getCurrent().isTrue();
  }
}

