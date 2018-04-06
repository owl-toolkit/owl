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
import com.google.common.collect.Multimap;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.LabelledEdge;
import owl.collections.Collections3;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;
import owl.util.TriConsumer;

@SuppressWarnings("ObjectEquality") // We use identity hash maps
final class HashMapAutomaton<S, A extends OmegaAcceptance>
  implements MutableAutomaton<S, A>, BulkOperationAutomaton {
  private A acceptance;
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
    transitions.computeIfAbsent(uniqueEdge.successor(), mapSupplier);
    map.merge(uniqueEdge, valuations, ValuationSet::union);
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
      Set<S> successorStates = Collections3.transformUnique(edges.keySet(), Edge::successor);
      checkState(transitions.keySet().containsAll(successorStates));
      successors.putAll(state, successorStates);
      for (S successor : successorStates) {
        predecessors.put(successor, state);
      }
    });

    return true;
  }

  @Override
  public void forEachLabelledEdge(S state, BiConsumer<Edge<S>, ValuationSet> action) {
    edgeMap(state).forEach(action);
  }

  @Override
  public void forEachLabelledEdge(TriConsumer<S, Edge<S>, ValuationSet> action) {
    transitions.forEach((state, map) ->
      map.forEach((edge, valuationSet) -> action.accept(state, edge, valuationSet)));
  }

  @Override
  public A acceptance() {
    return acceptance;
  }

  @Nullable
  @Override
  public Edge<S> edge(S state, BitSet valuation) {
    for (Map.Entry<Edge<S>, ValuationSet> entry : edgeMap(state).entrySet()) {
      if (entry.getValue().contains(valuation)) {
        return entry.getKey();
      }
    }

    return null;
  }

  private Map<Edge<S>, ValuationSet> edgeMap(S state) {
    Map<Edge<S>, ValuationSet> successors = transitions.get(makeUnique(state));
    checkArgument(successors != null, "State %s not in automaton", state);
    return successors;
  }

  @Override
  public Set<Edge<S>> edges(S state) {
    return Collections.unmodifiableSet(edgeMap(state).keySet());
  }

  @Override
  public Set<Edge<S>> edges(S state, BitSet valuation) {
    Set<Edge<S>> edges = new HashSet<>();

    edgeMap(state).forEach((key, valuations) -> {
      if (valuations.contains(valuation)) {
        edges.add(key);
      }
    });

    return edges;
  }

  @Override
  public ValuationSetFactory factory() {
    return valuationSetFactory;
  }

  @Override
  public Set<S> initialStates() {
    return Set.copyOf(initialStates);
  }

  @Override
  public Collection<LabelledEdge<S>> labelledEdges(S state) {
    return Collections.unmodifiableCollection(
      Collections2.transform(edgeMap(state).entrySet(), LabelledEdge::of));
  }

  @Override
  public String name() {
    return name == null ? String.format("Automaton for %s", initialStates()) : name;
  }

  @Override
  public Set<S> successors(S state) {
    return Collections3.transformUnique(edgeMap(state).keySet(), Edge::successor);
  }

  @Override
  public Set<S> states() {
    return Collections.unmodifiableSet(uniqueStates.keySet());
  }

  private S makeUnique(S state) {
    Objects.requireNonNull(state);

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
    S successor = edge.successor();
    S uniqueSuccessor = makeUnique(successor);

    Edge<S> castedEdge = (Edge<S>) edge;
    return successor == uniqueSuccessor // NOPMD
      ? castedEdge
      : castedEdge.withSuccessor(uniqueSuccessor);
  }

  @Override
  public void updateEdges(Set<? extends S> states, BiFunction<? super S, Edge<S>, Edge<S>> f) {
    assert states().containsAll(states);

    for (S state : states) {
      Map<Edge<S>, ValuationSet> map = edgeMap(state);
      Map<Edge<S>, ValuationSet> secondMap = new HashMap<>();

      map.entrySet().removeIf(entry -> {
        Edge<S> oldEdge = entry.getKey();
        Edge<S> newEdge = f.apply(state, oldEdge);

        if (newEdge == null) {
          return true;
        }

        if (Objects.equals(oldEdge, newEdge)) {
          return false;
        }

        newEdge = makeUnique(newEdge);
        secondMap.merge(newEdge, entry.getValue(), ValuationSet::union);
        return true;
      });

      secondMap.forEach((edge1, valuations) -> map.merge(edge1, valuations, ValuationSet::union));
    }
  }

  @Override
  public void removeEdge(S source, BitSet valuation, S destination) {
    ValuationSet valuationSet = valuationSetFactory.of(valuation);
    removeEdge(source, valuationSet, destination);
  }

  @Override
  public void removeEdge(S source, ValuationSet valuations, S destination) {
    ValuationSet complement = valuations.complement();

    edgeMap(source).entrySet().removeIf(entry -> {
      if (!Objects.equals(destination, entry.getKey().successor())) {
        return false;
      }

      ValuationSet edgeValuation = entry.getValue();
      entry.setValue(edgeValuation.intersection(complement));
      return edgeValuation.isEmpty();
    });
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
        edges.clear();
      } else {
        edges.entrySet().removeIf(e -> filter.test(e.getKey().successor()));
      }

      return removeState;
    });
  }

  @Override
  public void removeUnreachableStates(Collection<? extends S> start,
    Consumer<? super S> removedStatesConsumer) {
    Set<S> reachableStates = AutomatonUtil.getReachableStates(this, start);
    assert states().containsAll(reachableStates) : "Internal inconsistency";

    if (retainStates(reachableStates)) {
      for (S state : transitions.keySet()) {
        if (!reachableStates.contains(state)) {
          removedStatesConsumer.accept(state);
        }
      }
    }
  }

  @Override
  public void acceptance(A acceptance) {
    this.acceptance = acceptance;
  }

  private boolean retainStates(Collection<? extends S> states) {
    initialStates.retainAll(states);
    uniqueStates.keySet().retainAll(states);
    return removeTransitionsIf(state -> !states.contains(state));
  }

  @Override
  public void initialStates(Collection<? extends S> states) {
    addStates(states);
    initialStates.clear();
    states.stream().map(this::makeUnique).forEach(initialStates::add);
  }

  @Override
  public void name(String name) {
    this.name = name;
  }

  @Override
  public String toString() {
    return name == null ? super.toString() : name;
  }
}

