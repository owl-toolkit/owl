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

package owl.automaton;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Collections2;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import jhoafparser.consumer.HOAConsumer;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.LabelledEdge;
import owl.automaton.output.HoaConsumerExtended;
import owl.collections.ValuationSet;
import owl.collections.ValuationSetMapUtil;
import owl.factories.ValuationSetFactory;
import owl.util.TriConsumer;

// TODO: use Cofoja to ensure invariants.
@SuppressWarnings("ObjectEquality") // We use identity hash maps
final class HashMapAutomaton<S, A extends OmegaAcceptance> implements MutableAutomaton<S, A> {
  private final A acceptance;
  private final Set<S> initialStates;
  private final Function<S, Map<Edge<S>, ValuationSet>> mapSupplier = x -> new LinkedHashMap<>();
  private final IdentityHashMap<S, Map<Edge<S>, ValuationSet>> transitions;
  private final Map<S, S> uniqueStates;
  private final ValuationSetFactory valuationSetFactory;
  @Nullable
  private String name = null;

  HashMapAutomaton(ValuationSetFactory valuationSetFactory, A acceptance) {
    this.valuationSetFactory = valuationSetFactory;
    this.acceptance = acceptance;

    // Warning: Before doing ANY operation on transitions one needs to make the key unique!
    transitions = new IdentityHashMap<>();
    uniqueStates = new HashMap<>();
    initialStates = new HashSet<>();
  }

  @Override
  public void addEdge(S source, ValuationSet valuations, Edge<? extends S> edge) {
    if (valuations.isEmpty()) {
      return;
    }

    Map<Edge<S>, ValuationSet> map = transitions.computeIfAbsent(makeUnique(source), mapSupplier);
    Edge<S> uniqueEdge = makeUnique(edge);
    transitions.computeIfAbsent(uniqueEdge.getSuccessor(), mapSupplier);
    ValuationSetMapUtil.add(map, uniqueEdge, valuations.copy());
  }

  @Override
  public void addInitialStates(Collection<? extends S> states) {
    assert states.stream().allMatch(Objects::nonNull);
    addStates(states);
    states.stream().map(this::makeUnique).forEach(initialStates::add);
  }

  @Override
  public void addState(S state) {
    transitions.putIfAbsent(makeUnique(state), new LinkedHashMap<>());
  }

  @Override
  public void addStates(Collection<? extends S> states) {
    states.forEach(this::addState);
  }

  @VisibleForTesting
  boolean checkConsistency() {
    checkState(transitions.keySet().containsAll(initialStates));
    checkState(transitions.keySet().equals(uniqueStates.keySet()));

    Multimap<S, S> successors = HashMultimap.create();
    Multimap<S, S> predecessors = HashMultimap.create();

    // No "outgoing" edges
    transitions.forEach((state, edges) -> {
      Set<S> successorStates = Sets.newHashSet(ValuationSetMapUtil.viewSuccessors(edges));
      checkState(transitions.keySet().containsAll(successorStates));
      successors.putAll(state, successorStates);
      for (S successor : successorStates) {
        predecessors.put(successor, state);
      }
    });

    return true;
  }

  @Override
  public boolean containsState(S state) {
    return uniqueStates.containsKey(state);
  }

  @Override
  public boolean containsStates(Collection<? extends S> states) {
    return uniqueStates.keySet().containsAll(states);
  }

  @Override
  public void forEachLabelledEdge(S state, BiConsumer<Edge<S>, ValuationSet> action) {
    getEdgeMap(state).forEach(action);
  }

  @Override
  public void forEachLabelledEdge(TriConsumer<S, Edge<S>, ValuationSet> action) {
    transitions.forEach((state, map) ->
      map.forEach((edge, valuationSet) -> action.accept(state, edge, valuationSet)));
  }

  @Override
  public void free() {
    transitions.forEach((state, edges) -> edges.values().forEach(ValuationSet::free));
  }

  @Override
  public A getAcceptance() {
    return acceptance;
  }

  @Nullable
  @Override
  public Edge<S> getAnyEdge(S state, BitSet valuation) {
    return ValuationSetMapUtil.findFirst(getEdgeMap(state), valuation);
  }

  @Nullable
  @Override
  public Edge<S> getEdge(S state, BitSet valuation) {
    return ValuationSetMapUtil.findOnly(getEdgeMap(state), valuation);
  }

  private Map<Edge<S>, ValuationSet> getEdgeMap(S state) {
    Map<Edge<S>, ValuationSet> successors = transitions.get(makeUnique(state));
    checkArgument(successors != null, "State %s not in automaton", state);
    return successors;
  }

  @Override
  public Set<Edge<S>> getEdges(S state) {
    return Collections.unmodifiableSet(getEdgeMap(state).keySet());
  }

  @Override
  public Set<Edge<S>> getEdges(S state, BitSet valuation) {
    return ValuationSetMapUtil.findAll(getEdgeMap(state), valuation);
  }

  @Override
  public ValuationSetFactory getFactory() {
    return valuationSetFactory;
  }

  @Override
  public ImmutableSet<S> getInitialStates() {
    return ImmutableSet.copyOf(initialStates);
  }

  @Override
  public Collection<LabelledEdge<S>> getLabelledEdges(S state) {
    Map<Edge<S>, ValuationSet> transitionMap = getEdgeMap(state);

    return Collections.unmodifiableCollection(Collections2.transform(
      Maps.transformValues(transitionMap, ValuationSet::copy).entrySet(), LabelledEdge::new));
  }

  @Override
  public String getName() {
    return name == null ? String.format("Automaton for %s", getInitialStates()) : name;
  }

  @Override
  public Set<S> getReachableStates(Collection<? extends S> start) {
    checkArgument(containsStates(start), "Some of the states %s are not in the automaton", start);

    Set<S> reachedStates = Sets.newIdentityHashSet();
    start.forEach(state -> reachedStates.add(makeUnique(state)));
    Queue<S> workQueue = new ArrayDeque<>(reachedStates);

    while (!workQueue.isEmpty()) {
      S state = workQueue.poll();
      Map<Edge<S>, ValuationSet> edges = transitions.get(state);

      ValuationSetMapUtil.viewSuccessors(edges).forEach(successor -> {
        if (reachedStates.add(successor)) {
          workQueue.add(successor);
        }
      });
    }

    return reachedStates;
  }

  @Override
  public Set<S> getStates() {
    return Collections.unmodifiableSet(uniqueStates.keySet());
  }

  @Override
  public Map<S, ValuationSet> getSuccessorMap(S state) {
    Map<Edge<S>, ValuationSet> edges = getEdgeMap(state);
    Map<S, ValuationSet> successorMap = new HashMap<>();
    edges.forEach((edge, valuations) -> ValuationSetMapUtil.add(successorMap, edge.getSuccessor(),
      valuations.copy()));
    return successorMap;
  }

  private S makeUnique(S state) {
    // TODO Maybe we shouldn't always put?
    S uniqueState = uniqueStates.putIfAbsent(state, state);
    if (uniqueState == null) {
      // This state was added to the mapping
      return state;
    }

    assert transitions.containsKey(uniqueState) :
      String.format("Inconsistent mapping for %s", uniqueState);
    return uniqueState;
  }

  @SuppressWarnings("unchecked")
  private Edge<S> makeUnique(Edge<? extends S> edge) {
    S successor = edge.getSuccessor();
    S uniqueSuccessor = makeUnique(successor);

    Edge<S> castedEdge = (Edge<S>) edge;
    return successor == uniqueSuccessor // NOPMD
      ? castedEdge
      : castedEdge.withSuccessor(uniqueSuccessor);
  }

  @Override
  public void remapEdges(Set<? extends S> states, BiFunction<? super S, Edge<S>, Edge<S>> f) {
    assert containsStates(states);

    for (S state : states) {
      ValuationSetMapUtil.update(getEdgeMap(state), edge -> {
        Edge<S> newEdge = f.apply(state, edge);

        if (newEdge == null) {
          return null;
        }

        if (newEdge == edge) {
          return edge;
        }

        assert containsState(edge.getSuccessor());
        return makeUnique(newEdge);
      });
    }
  }

  @Override
  public void removeEdge(S source, BitSet valuation, S destination) {
    ValuationSet valuationSet = valuationSetFactory.createValuationSet(valuation);
    removeEdge(source, valuationSet, destination);
    valuationSet.free();
  }

  @Override
  public void removeEdge(S source, ValuationSet valuations, S destination) {
    ValuationSetMapUtil.remove(getEdgeMap(source), destination, valuations);
  }

  @Override
  public boolean removeStates(Predicate<? super S> filter) {
    initialStates.removeIf(filter);
    uniqueStates.keySet().removeIf(filter);
    return removeTransitionsIf(filter);
  }

  private boolean removeTransitionsIf(Predicate<? super S> filter) {
    return transitions.entrySet().removeIf(entry -> {
      boolean removeState = filter.test(entry.getKey());

      Map<Edge<S>, ValuationSet> edges = entry.getValue();

      if (removeState) {
        ValuationSetMapUtil.clear(edges);
      } else {
        ValuationSetMapUtil.remove(edges, filter);
      }

      return removeState;
    });
  }

  @Override
  public void removeUnreachableStates(Collection<? extends S> start,
    Consumer<? super S> removedStatesConsumer) {
    Set<S> reachableStates = getReachableStates(start);
    assert containsStates(reachableStates) : "Internal inconsistency";

    if (!retainStates(reachableStates)) {
      return;
    }

    for (S state : transitions.keySet()) {
      if (!reachableStates.contains(state)) {
        removedStatesConsumer.accept(state);
      }
    }
  }

  private boolean retainStates(Collection<? extends S> states) {
    initialStates.retainAll(states);
    uniqueStates.keySet().retainAll(states);
    return removeTransitionsIf(state -> !states.contains(state));
  }

  @Override
  public void setInitialStates(Collection<? extends S> states) {
    initialStates.clear();
    addInitialStates(states);
  }

  @Override
  public void setName(String name) {
    this.name = name;
  }

  @Override
  public int stateCount() {
    return transitions.size();
  }

  @Override
  public void toHoa(HOAConsumer consumer, EnumSet<HoaOption> options) {
    HoaConsumerExtended<S> hoa = new HoaConsumerExtended<>(consumer, getVariables(),
      acceptance, ImmutableSet.copyOf(initialStates), stateCount(), options,
      isDeterministic(), getName());

    transitions.forEach((state, edges) -> {
      hoa.addState(state);
      edges.forEach(hoa::addEdge);
      hoa.notifyEndOfState();
    });

    hoa.notifyEnd();
  }

  @Override
  public String toString() {
    return name == null ? super.toString() : name;
  }
}

