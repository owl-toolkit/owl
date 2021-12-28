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

package owl.automaton;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.bdd.BddSet;
import owl.bdd.BddSetFactory;
import owl.bdd.FactorySupplier;
import owl.bdd.MtBdd;
import owl.collections.Collections3;

@SuppressWarnings("ObjectEquality") // We use identity hash maps
public final class HashMapAutomaton<S, A extends EmersonLeiAcceptance>
  implements MutableAutomaton<S, A> {

  private static final Logger logger = Logger.getLogger(HashMapAutomaton.class.getName());

  private final List<String> atomicPropositions;
  private A acceptance;
  private final Set<S> initialStates;
  private IdentityHashMap<S, Map<Edge<S>, BddSet>> transitions;
  private final IdentityHashMap<S, MtBdd<Edge<S>>> cachedTrees;
  private Map<S, S> uniqueStates;
  private final BddSetFactory valuationSetFactory;
  private State state = State.READ;

  private HashMapAutomaton(
    List<String> atomicPropositions,
    BddSetFactory valuationSetFactory,
    A acceptance) {

    this.valuationSetFactory = valuationSetFactory;
    this.acceptance = acceptance;
    this.atomicPropositions = List.copyOf(atomicPropositions);
    checkArgument(Collections3.isDistinct(this.atomicPropositions));

    // Warning: Before doing ANY operation on transitions one needs to make the key unique!
    transitions = new IdentityHashMap<>();
    cachedTrees = new IdentityHashMap<>();
    uniqueStates = new HashMap<>();
    initialStates = new HashSet<>();
  }


  // Acceptance

  @Override
  public A acceptance() {
    return acceptance;
  }

  @Override
  public List<String> atomicPropositions() {
    return atomicPropositions;
  }

  @Override
  public void acceptance(A acceptance) {
    this.acceptance = acceptance;
  }


  // Initial States

  @Override
  public Set<S> initialStates() {
    readMode();
    return Collections.unmodifiableSet(initialStates);
  }

  @Override
  public void initialStates(Collection<? extends S> initialStates) {
    writeMode();

    // If we have to remove an initial state, we need to clear and rebuild.
    if (!initialStates.containsAll(this.initialStates)) {
      state = State.WRITE_REBUILD;
      this.initialStates.clear();
    }

    initialStates.forEach(this::addInitialState);
  }

  @Override
  public void addInitialState(S initialState) {
    // Insert into state set and mark as initial.
    initialStates.add(makeUnique(initialState));
  }

  @Override
  public void removeInitialState(S initialState) {
    state = State.WRITE_REBUILD;
    initialStates.remove(initialState);
  }


  // States

  @Override
  public Set<S> states() {
    readMode();
    return Collections.unmodifiableSet(uniqueStates.keySet());
  }

  @Override
  public void addState(S state) {
    writeMode();

    if (!uniqueStates.containsKey(state)) {
      this.state = State.WRITE_REBUILD;
      makeUnique(state);
    }
  }

  @Override
  public void removeStateIf(Predicate<? super S> stateFilter) {
    writeMode();

    if (!uniqueStates.keySet().removeIf(stateFilter)) {
      // There is no matching state. We can leave all data structures as they are.
      return;
    }

    state = State.WRITE_REBUILD;
    initialStates.removeIf(stateFilter);
    Predicate<Edge<S>> edgeFilter = edge -> stateFilter.test(edge.successor());
    transitions.entrySet().removeIf(entry -> {
      boolean removeState = stateFilter.test(entry.getKey());

      if (!removeState) {
        entry.getValue().keySet().removeIf(edgeFilter);
      }

      return removeState;
    });
  }


  // Edges

  @Nullable
  @Override
  public Edge<S> edge(S state, BitSet valuation) {
    readMode();

    for (Map.Entry<Edge<S>, BddSet> entry : edgeMapInternal(state).entrySet()) {
      if (entry.getValue().contains(valuation)) {
        return entry.getKey();
      }
    }

    return null;
  }

  @Override
  public Set<Edge<S>> edges(S state) {
    readMode();
    return Collections.unmodifiableSet(edgeMapInternal(state).keySet());
  }

  @Override
  public Map<Edge<S>, BddSet> edgeMap(S state) {
    readMode();
    return Collections.unmodifiableMap(edgeMapInternal(state));
  }

  @Override
  public MtBdd<Edge<S>> edgeTree(S state) {
    readMode();
    S uniqueState = uniqueStates.get(Objects.requireNonNull(state));
    checkArgument(uniqueState != null, "state (%s) is not present in the automaton.", state);
    return cachedTrees.computeIfAbsent(uniqueState, x -> factory().toMtBdd(edgeMap(x)));
  }

  @Override
  public Set<S> successors(S state) {
    readMode();
    return Edges.successors(edgeMapInternal(state).keySet());
  }

  @Override
  public void addEdge(S source, BddSet valuations, Edge<? extends S> edge) {
    cachedTrees.remove(makeUnique(source));
    edgeMapInternal(source).merge(makeUnique(edge), valuations, BddSet::union);
  }

  @Override
  public void removeEdge(S source, BddSet valuations, S destination) {
    writeMode();

    BddSet complement = valuations.complement();
    boolean edgeRemoved = edgeMapInternal(source).entrySet().removeIf(entry -> {
      if (!destination.equals(entry.getKey().successor())) {
        return false;
      }

      BddSet edgeValuation = entry.getValue().intersection(complement);
      entry.setValue(edgeValuation);
      return edgeValuation.isEmpty();
    });

    // Rebuild transition table only if the removed edge is not a self-loop and something was
    // removed.
    if (!source.equals(destination) && edgeRemoved) {
      state = State.WRITE_REBUILD;
    }
  }

  @Override
  public void updateEdges(Set<? extends S> states, BiFunction<? super S, Edge<S>, Edge<S>> f) {
    writeMode();

    for (S state : states) {
      Map<Edge<S>, BddSet> map = edgeMapInternal(state);
      Map<Edge<S>, BddSet> secondMap = new HashMap<>();

      map.entrySet().removeIf(entry -> {
        Edge<S> oldEdge = entry.getKey();
        Edge<S> newEdge = f.apply(state, oldEdge);

        if (newEdge == null) {
          if (!state.equals(oldEdge.successor())) {
            // Rebuild transition table only if the removed edge is not a self-loop.
            this.state = State.WRITE_REBUILD;
          }

          return true;
        }

        if (oldEdge.equals(newEdge)) {
          return false;
        }

        if (!oldEdge.successor().equals(newEdge.successor())) {
          // There is a new successor, thus the reachable state set might have changed.
          this.state = State.WRITE_REBUILD;
        }

        newEdge = makeUnique(newEdge);
        secondMap.merge(newEdge, entry.getValue(), BddSet::union);
        return true;
      });

      secondMap.forEach((edge, valuations) -> map.merge(edge, valuations, BddSet::union));
    }
  }

  @Override
  public void updateEdges(BiFunction<S, Edge<S>, Edge<S>> updater) {
    updateEdges(transitions.keySet(), updater);
  }

  // Misc.

  @Override
  public BddSetFactory factory() {
    return valuationSetFactory;
  }

  @Override
  public void trim() {
    cachedTrees.clear();

    if (state != State.WRITE_REBUILD) {
      state = State.READ;
      return;
    }

    state = State.READ;
    Set<S> exploredStates = new HashSet<>(initialStates());
    Deque<S> workQueue = new ArrayDeque<>(exploredStates);

    Map<S, Map<Edge<S>, BddSet>> oldTransitions = transitions;
    transitions = new IdentityHashMap<>(oldTransitions.size());
    uniqueStates = new HashMap<>(uniqueStates.size());

    // Ensure that the initial states are in the unique map.
    initialStates.forEach(this::makeUnique);

    while (!workQueue.isEmpty()) {
      S state = workQueue.remove();
      oldTransitions.get(state).forEach((edge, valuationSet) -> {
        addEdge(state, valuationSet, edge);

        if (exploredStates.add(edge.successor())) {
          workQueue.add(edge.successor());
        }
      });
    }

    logger.log(Level.FINEST, "Cleared {0} states", oldTransitions.size() - transitions.size());
    verify(state == State.READ, "Concurrent modification.");
  }

  @VisibleForTesting
  boolean checkConsistency() {
    checkState(transitions.keySet().containsAll(initialStates));
    checkState(transitions.keySet().equals(uniqueStates.keySet()));

    Multimap<S, S> successors = HashMultimap.create();
    Multimap<S, S> predecessors = HashMultimap.create();

    // No "outgoing" edges
    transitions.forEach((state, edges) -> {
      Set<S> successorStates = Edges.successors(edges.keySet());
      checkState(transitions.keySet().containsAll(successorStates));
      successors.putAll(state, successorStates);
      for (S successor : successorStates) {
        predecessors.put(successor, state);
      }
    });

    return true;
  }

  private void readMode() {
    checkState(state == State.READ, "trim() must be called.");
  }

  private void writeMode() {
    if (state == State.READ) {
      state = State.WRITE;
    }
  }

  private Map<Edge<S>, BddSet> edgeMapInternal(S state) {
    S uniqueState = uniqueStates.get(Objects.requireNonNull(state));
    checkArgument(uniqueState != null, "State %s not in automaton", state);
    Map<Edge<S>, BddSet> successors = transitions.get(uniqueState);
    assert successors.values().stream().noneMatch(BddSet::isEmpty);
    return successors;
  }

  private S makeUnique(S state) {
    Objects.requireNonNull(state);

    S uniqueState = uniqueStates.putIfAbsent(state, state);
    if (uniqueState == null) {
      transitions.put(state, new LinkedHashMap<>());
      return state;
    }

    assert transitions.containsKey(uniqueState) :
      String.format("Inconsistent mapping for %s", uniqueState);
    return uniqueState;
  }

  @SuppressWarnings("unchecked")
  private Edge<S> makeUnique(Edge<? extends S> edge) {
    S successor = edge.successor();
    S uniqueSuccessor = makeUnique(successor);

    Edge<S> castedEdge = (Edge<S>) edge;
    return successor == uniqueSuccessor // NOPMD
      ? castedEdge
      : castedEdge.withSuccessor(uniqueSuccessor);
  }

  /**
   * Creates an empty automaton with given acceptance condition.
   *
   * @param <S> The states of the automaton.
   * @param <A> The acceptance condition of the automaton.
   *
   * @return Empty automaton with the specified parameters.
   */
  public static <S, A extends EmersonLeiAcceptance> HashMapAutomaton<S, A> create(
    List<String> atomicPropositions,
    A acceptance) {

    return new HashMapAutomaton<>(
      atomicPropositions,
      FactorySupplier.defaultSupplier().getBddSetFactory(),
      acceptance);
  }

  /**
   * Creates an empty automaton with given acceptance condition. The {@code valuationSetFactory} is
   * used as transition backend.
   *
   * @param acceptance The acceptance of the new automaton.
   * @param vsFactory The alphabet.
   * @param <S> The states of the automaton.
   * @param <A> The acceptance condition of the automaton.
   *
   * @return Empty automaton with the specified parameters.
   */
  public static <S, A extends EmersonLeiAcceptance> HashMapAutomaton<S, A> create(
    List<String> atomicPropositions,
    BddSetFactory vsFactory,
    A acceptance) {

    return new HashMapAutomaton<>(atomicPropositions, vsFactory, acceptance);
  }

  public static <S, A extends EmersonLeiAcceptance> HashMapAutomaton<S, A> copyOf(
    Automaton<S, A> source) {
    HashMapAutomaton<S, A> target = new HashMapAutomaton<>(
      source.atomicPropositions(), source.factory(), source.acceptance());

    source.initialStates().forEach(target::addInitialState);
    // Use a work-list algorithm in case source is an on-the-fly generated automaton.
    Deque<S> workList = new ArrayDeque<>(source.initialStates());
    Set<S> visited = new HashSet<>(workList);

    while (!workList.isEmpty()) {
      S state1 = workList.remove();
      target.addState(state1);
      source.edgeMap(state1).forEach((x, y) -> {
        target.addEdge(state1, y, x);
        if (visited.add(x.successor())) {
          workList.add(x.successor());
        }
      });
    }

    target.trim();
    assert source.states().equals(target.states());
    return target;
  }

  @Override
  public Set<Edge<S>> edges(S state, BitSet valuation) {
    return Maps.filterValues(edgeMap(state), x -> x.contains(valuation)).keySet();
  }

  private enum State {
    READ, // Read operations are allowed.
    WRITE, // Write operation happened, but no rebuild is required.
    WRITE_REBUILD // Write operation happened and a rebuild is required.
  }
}

