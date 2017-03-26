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
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
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
import owl.util.IntIteratorTransformer;

// TODO: use Cofoja to ensure invariants.
public final class HashMapAutomaton<S, A extends OmegaAcceptance>
  implements MutableAutomaton<S, A> {
  private final A acceptance;
  private final Set<S> initialStates = new HashSet<>();
  private final Map<S, Map<Edge<S>, ValuationSet>> transitions;
  private final ValuationSetFactory valuationSetFactory;
  @Nullable
  private String name = null;
  private ImmutableList<String> variables;

  HashMapAutomaton(ValuationSetFactory valuationSetFactory, A acceptance) {
    this.valuationSetFactory = valuationSetFactory;
    this.acceptance = acceptance;
    transitions = new HashMap<>();
    variables = IntStream.range(0, valuationSetFactory.getSize()).mapToObj(i -> "p" + i).collect(
      ImmutableList.toImmutableList());
  }

  @Override
  public void addEdge(S source, Edge<S> edge) {
    addEdge(source, valuationSetFactory.createUniverseValuationSet(), edge);
  }

  @Override
  public void addEdge(S source, BitSet valuation, Edge<S> edge) {
    addEdge(source, valuationSetFactory.createValuationSet(valuation), edge);
  }

  @Override
  public void addEdge(S source, ValuationSet valuations, Edge<S> edge) {
    if (valuations.isEmpty()) {
      return;
    }

    Map<Edge<S>, ValuationSet> map = transitions.computeIfAbsent(source,
      x -> new LinkedHashMap<>());
    ValuationSetMapUtil.add(map, edge, valuations.copy());
    addState(edge.getSuccessor());
  }

  @Override
  public void addInitialStates(Collection<S> states) {
    addStates(states);
    initialStates.addAll(states);
  }

  @Override
  public void addStates(Collection<S> states) {
    states.forEach(state -> transitions.computeIfAbsent(state, x -> new LinkedHashMap<>()));
  }

  @VisibleForTesting
  boolean checkConsistency() {
    checkState(transitions.keySet().containsAll(initialStates));

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
    return transitions.containsKey(state);
  }

  @Override
  public boolean containsStates(Collection<S> states) {
    return transitions.keySet().containsAll(states);
  }

  void free() {
    transitions.forEach((state, edges) -> edges.values().forEach(ValuationSet::free));
  }

  @Override
  public A getAcceptance() {
    return acceptance;
  }

  @Nullable
  @Override
  public Edge<S> getEdge(S state, BitSet valuation) {
    Map<Edge<S>, ValuationSet> edges = transitions.get(state);
    checkArgument(edges != null, "State %s not in automaton", state);
    return ValuationSetMapUtil.findFirst(edges, valuation);
  }

  @Override
  public ValuationSetFactory getFactory() {
    return valuationSetFactory;
  }

  @Override
  public Map<S, ValuationSet> getIncompleteStates() {
    Map<S, ValuationSet> incompleteStates = new HashMap<>();

    transitions.forEach((state, successors) -> {
      ValuationSet unionSet = valuationSetFactory.createEmptyValuationSet();

      successors.forEach((edge, valuation) -> unionSet.addAll(valuation));

      // State is incomplete; complement() creates a new, referenced node.
      if (!unionSet.isUniverse()) {
        incompleteStates.put(state, unionSet.complement());
      }

      unionSet.free();
    });

    return incompleteStates;
  }

  @Override
  public ImmutableSet<S> getInitialStates() {
    return ImmutableSet.copyOf(initialStates);
  }

  @Override
  public Collection<LabelledEdge<S>> getLabelledEdges(S state) {
    Map<Edge<S>, ValuationSet> transitionMap = transitions.get(state);
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
  public Set<S> getReachableStates(Collection<S> start) {
    assert containsStates(start) :
      String.format("Some of the states %s are not in the automaton", start);

    Set<S> reachedStates = Sets.newHashSet(start);
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

  @Override
  public void remapAcceptance(Set<S> states, IntUnaryOperator transformer) {
    // assert instead of checkArgument for performance
    assert containsStates(states) :
      String.format("Some of the states %s are not in the automaton", states);

    Function<Edge<S>, Edge<S>> edgeUpdater = edge -> Edges.create(edge.getSuccessor(),
      new IntIteratorTransformer(edge.acceptanceSetIterator(), transformer));
    states.forEach(state -> ValuationSetMapUtil.update(transitions.get(state), edgeUpdater));
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
  public void removeEdge(S source, BitSet valuation, S destination) {
    ValuationSet valuationSet = valuationSetFactory.createValuationSet(valuation);
    removeEdge(source, valuationSet, destination);
    valuationSet.free();
  }

  @Override
  public void removeEdge(S source, ValuationSet valuations, S destination) {
    Map<Edge<S>, ValuationSet> edges = transitions.get(source);
    checkArgument(edges != null, "State %s not in automaton", source);
    ValuationSetMapUtil.remove(edges, destination, valuations);
  }

  @Override
  public void removeEdges(S source, S destination) {
    removeEdge(source, valuationSetFactory.createUniverseValuationSet(), destination);
  }

  @Override
  public boolean removeStates(Collection<S> states) {
    // Iterables.contains is smart about data types
    return removeStates(state -> Iterables.contains(states, state));
  }

  @Override
  public boolean removeStates(Predicate<S> states) {
    initialStates.removeIf(states);

    return transitions.entrySet().removeIf(entry -> {
      boolean removeState = states.test(entry.getKey());

      Map<Edge<S>, ValuationSet> edges = entry.getValue();

      if (removeState) {
        ValuationSetMapUtil.clear(edges);
      } else {
        ValuationSetMapUtil.remove(edges, states);
      }

      return removeState;
    });
  }

  @Override
  public void removeUnreachableStates(Collection<S> start, Consumer<S> removedStatesConsumer) {
    assert containsStates(start) :
      String.format("Some of the states %s are not in the automaton", start);

    Set<S> reachableStates = getReachableStates(start);
    assert containsStates(reachableStates) : "Internal inconsistency";
    int expectedSize = transitions.size() - reachableStates.size();
    List<S> statesToRemove = Lists.newArrayListWithCapacity(expectedSize);

    for (S state : transitions.keySet()) {
      if (!reachableStates.contains(state)) {
        statesToRemove.add(state);
      }
    }

    assert statesToRemove.size() == expectedSize;
    removeStates(statesToRemove);
    statesToRemove.forEach(removedStatesConsumer);
  }

  @Override
  public void setInitialStates(Collection<S> states) {
    initialStates.clear();
    addInitialStates(states);
  }

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
      isDeterministic());

    transitions.forEach((state, edges) -> {
      hoa.addState(state);
      edges.forEach(hoa::addEdge);
      hoa.notifyEndOfState();
    });

    hoa.notifyEnd();
  }
}

