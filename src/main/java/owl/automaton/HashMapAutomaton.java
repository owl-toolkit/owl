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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import jhoafparser.consumer.HOAConsumer;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.automaton.edge.LabelledEdge;
import owl.automaton.output.HoaConsumerExtended;
import owl.collections.ValuationSet;
import owl.collections.ValuationSetMapUtil;
import owl.factories.ValuationSetFactory;

// TODO: use Cofoja to ensure invariants.
@SuppressWarnings("ObjectEquality") // We use identity hash maps
final class HashMapAutomaton<S, A extends OmegaAcceptance> implements MutableAutomaton<S, A> {
  private final A acceptance;
  private final Set<S> initialStates;
  private final Function<S, Map<Edge<S>, ValuationSet>> mapSupplier = x -> new LinkedHashMap<>();
  private final Map<S, Map<Edge<S>, ValuationSet>> transitions;
  private final Map<S, S> uniqueStates;
  private final ValuationSetFactory valuationSetFactory;
  @Nullable
  private String name = null;
  private ImmutableList<String> variables;

  HashMapAutomaton(ValuationSetFactory valuationSetFactory, A acceptance) {
    this.valuationSetFactory = valuationSetFactory;
    this.acceptance = acceptance;

    // Warning: Before doing ANY operation on transitions one needs to make the key unique!
    transitions = new IdentityHashMap<>();
    uniqueStates = new HashMap<>();
    initialStates = new HashSet<>();
    variables = IntStream.range(0, valuationSetFactory.getSize()).mapToObj(i -> "p" + i).collect(
      ImmutableList.toImmutableList());
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
  public void forEachSuccessor(S state, BiConsumer<Edge<S>, ValuationSet> action) {
    transitions.get(makeUnique(state)).forEach(action);
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
  public Edge<S> getEdge(S state, BitSet valuation) {
    Map<Edge<S>, ValuationSet> edges = transitions.get(makeUnique(state));
    checkArgument(edges != null, "State %s not in automaton", state);
    return ValuationSetMapUtil.findFirst(edges, valuation);
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
    Map<Edge<S>, ValuationSet> transitionMap = transitions.get(makeUnique(state));
    checkArgument(transitionMap != null, "State %s not in automaton", state);

    return Collections2.transform(Collections.unmodifiableCollection(transitionMap.entrySet()),
      LabelledEdge::new);
  }

  @Override
  public String getName() {
    if (name == null) {
      return String.format("Automaton for %s", getInitialStates());
    }

    return name;
  }

  @Override
  public Set<S> getReachableStates(Collection<? extends S> start) {
    assert containsStates(start) :
      String.format("Some of the states %s are not in the automaton", start);

    Set<S> reachedStates = Sets.newHashSet(Collections2.transform(start, this::makeUnique));
    Queue<S> workQueue = new ArrayDeque<>(reachedStates);

    while (!workQueue.isEmpty()) {
      S state = workQueue.poll();
      Map<Edge<S>, ValuationSet> edges = transitions.get(state);
      assert edges != null : String.format("State %s not in automaton", state);

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
    return Collections.unmodifiableSet(transitions.keySet());
  }

  @Override
  public ImmutableList<String> getVariables() {
    return variables;
  }

  private S makeUnique(S state) {
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
    if (successor == uniqueSuccessor) { // NOPMD We use identity maps!
      return (Edge<S>) edge;
    }
    return Edges.create(uniqueSuccessor, edge.acceptanceSetIterator());
  }

  @Override
  public void remapAcceptance(Set<? extends S> states, IntUnaryOperator transformer) {
    // assert instead of checkArgument for performance
    assert containsStates(states) :
      String.format("Some of the states %s are not in the automaton", states);

    Function<Edge<S>, Edge<S>> edgeUpdater = edge -> Edges.transformAcceptance(edge, transformer);
    states.forEach(state -> ValuationSetMapUtil.update(transitions.get(makeUnique(state)),
      edgeUpdater));
  }

  @Override
  public void remapAcceptance(BiFunction<S, Edge<S>, BitSet> f) {
    transitions.forEach((state, edges) -> ValuationSetMapUtil.update(edges, edge -> {
      BitSet resultAcceptance = f.apply(state, edge);

      if (resultAcceptance == null) {
        return edge;
      }

      return Edges.create(edge.getSuccessor(), resultAcceptance);
    }));
  }

  @Override
  public void remapAcceptance(Set<? extends S> states, BiFunction<S, Edge<S>, BitSet> f) {
    assert states.stream().allMatch(this::containsState);
    states.forEach(state -> ValuationSetMapUtil.update(transitions.get(state), (Edge<S> edge) -> {
      BitSet resultAcceptance = f.apply(state, edge);

      if (resultAcceptance == null) {
        return edge;
      }

      return Edges.create(edge.getSuccessor(), resultAcceptance);
    }));
  }

  @Override
  public void remapEdges(Set<? extends S> states, BiFunction<S, Edge<S>, Edge<S>> f) {
    // assert instead of checkArgument for performance
    assert containsStates(states) :
      String.format("Some of the states %s are not in the automaton", states);

    for (S state : states) {
      Map<Edge<S>, ValuationSet> valuationSetMap = transitions.get(makeUnique(state));
      ValuationSetMapUtil.update(valuationSetMap, edge -> {
        Edge<S> newEdge = f.apply(state, edge);
        if (newEdge == null) {
          return null;
        }
        if (newEdge == edge) {
          return edge;
        }
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
    Map<Edge<S>, ValuationSet> edges = transitions.get(makeUnique(source));
    checkArgument(edges != null, "State %s not in automaton", source);
    ValuationSetMapUtil.remove(edges, destination, valuations);
  }

  @Override
  public boolean removeStates(Predicate<S> filter) {
    initialStates.removeIf(filter);
    uniqueStates.keySet().removeIf(filter);
    return removeTransitionsIf(filter);
  }

  private boolean removeTransitionsIf(Predicate<S> filter) {
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
    Consumer<S> removedStatesConsumer) {
    assert containsStates(start) :
      String.format("Some of the states %s are not in the automaton", start);

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

  @Override
  public boolean retainStates(Collection<? extends S> states) {
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
  public void setVariables(List<String> variables) {
    this.variables = ImmutableList.copyOf(variables);
  }

  @Override
  public int stateCount() {
    return transitions.size();
  }

  @Override
  public void toHoa(HOAConsumer consumer, EnumSet<Option> options) {
    HoaConsumerExtended<S> hoa = new HoaConsumerExtended<>(consumer, getVariables(),
      getAcceptance(), ImmutableSet.copyOf(initialStates), stateCount(), options,
      isDeterministic(), getName());

    transitions.forEach((state, edges) -> {
      hoa.addState(state);
      edges.forEach(hoa::addEdge);
      hoa.notifyEndOfState();
    });

    hoa.notifyEnd();
  }
}

