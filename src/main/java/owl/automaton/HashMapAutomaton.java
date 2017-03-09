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
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
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

// TODO: use Cofoja to ensure invariants.
@SuppressWarnings("PMD.GodClass")
public final class HashMapAutomaton<S, A extends OmegaAcceptance>
  implements MutableAutomaton<S, A> {
  private final A acceptance;
  private final Set<S> initialStates = new HashSet<>();
  private final Map<S, Map<Edge<S>, ValuationSet>> transitions;
  private final ValuationSetFactory valuationSetFactory;
  private ImmutableList<String> variables;

  HashMapAutomaton(ValuationSetFactory valuationSetFactory, A acceptance) {
    this.valuationSetFactory = valuationSetFactory;
    this.acceptance = acceptance;
    transitions = new HashMap<>();
    variables = IntStream.range(0, valuationSetFactory.getSize()).mapToObj(i -> "p" + i).collect(
      ImmutableList.toImmutableList());
  }

  public <B extends OmegaAcceptance> HashMapAutomaton(HashMapAutomaton<S, B> copy2,
    A newAcceptance) {
    this.valuationSetFactory = copy2.valuationSetFactory;
    this.acceptance = newAcceptance;
    transitions = copy2.transitions;
    variables = copy2.variables;
    initialStates.addAll(copy2.initialStates);
    addStates(initialStates);
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
  public void addInitialStates(Iterable<S> states) {
    Iterables.addAll(initialStates, states);
    addStates(states);
  }

  @Override
  public void addStates(Iterable<S> states) {
    states.forEach(state -> transitions.computeIfAbsent(state, x -> new LinkedHashMap<>()));
  }

  @VisibleForTesting
  boolean checkConsistency() {
    checkState(transitions.keySet().containsAll(initialStates));

    Multimap<S, S> successors = HashMultimap.create();
    Multimap<S, S> predecessors = HashMultimap.create();

    // No "outgoing" edges
    transitions.forEach((state, edges) -> {
      final Set<S> successorStates = Sets.newHashSet(ValuationSetMapUtil.viewSuccessors(edges));
      checkState(transitions.keySet().containsAll(successorStates));
      successors.putAll(state, successorStates);
      for (S successor : successorStates) {
        predecessors.put(successor, state);
      }
    });

    return true;
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
    checkArgument(edges != null);
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

      successors.forEach((edge, valuation) -> {
        unionSet.addAll(valuation);
      });

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
  public Iterable<LabelledEdge<S>> getLabelledEdges(S state) {
    checkArgument(transitions.containsKey(state), "Unknown state: " + state);
    return Collections2.transform(transitions.get(state).entrySet(), LabelledEdge::new);
  }

  @Override
  public Set<S> getReachableStates(Iterable<S> start) {
    Set<S> reachedStates = Sets.newHashSet(start);
    Queue<S> workQueue = new ArrayDeque<>(reachedStates);

    while (!workQueue.isEmpty()) {
      S state = workQueue.poll();
      Map<Edge<S>, ValuationSet> successors = transitions.get(state);

      if (successors == null) {
        continue;
      }

      ValuationSetMapUtil.viewSuccessors(successors).forEach(successor -> {
        if (!reachedStates.contains(successor)) {
          reachedStates.add(successor);
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
    Function<Edge<S>, Edge<S>> edgeUpdater = edge -> Edges.create(edge.getSuccessor(),
      new IntIteratorTransformer(edge.acceptanceSetIterator(), transformer));
    transitions.forEach((state, edges) -> ValuationSetMapUtil.update(edges, edgeUpdater));
  }

  @Override
  public void remapAcceptance(BiFunction<S, Edge<S>, BitSet> f) {
    transitions.forEach((state, edges) -> {
      ValuationSetMapUtil.update(edges, edge -> {
        BitSet resultAcceptance = f.apply(state, edge);

        if (resultAcceptance == null) {
          return edge;
        }

        return Edges.create(edge.getSuccessor(), resultAcceptance);
      });
    });
  }

  @Override
  public void removeEdge(S source, BitSet valuation, S destination) {
    ValuationSet valuationSet = valuationSetFactory.createValuationSet(valuation);
    removeEdge(source, valuationSet, destination);
    valuationSet.free();
  }

  @Override
  public void removeEdge(S source, ValuationSet valuations, S destination) {
    Map<Edge<S>, ValuationSet> successorSet = transitions.get(source);

    if (successorSet != null) {
      ValuationSetMapUtil.remove(successorSet, destination, valuations);
    }
  }

  @Override
  public void removeEdges(S source, S destination) {
    removeEdge(source, valuationSetFactory.createUniverseValuationSet(), destination);
  }

  @Override
  public boolean removeStates(Iterable<S> states) {
    if (states instanceof Set) {
      return removeStates(((Set<S>) states)::contains);
    }

    return removeStates(state -> Iterables.contains(states, state));
  }

  @Override
  public boolean removeStates(Predicate<S> predicate) {
    initialStates.removeIf(predicate);

    return transitions.entrySet().removeIf(entry -> {
      boolean removeState = predicate.test(entry.getKey());

      Map<Edge<S>, ValuationSet> edges = entry.getValue();

      if (removeState) {
        ValuationSetMapUtil.clear(edges);
      } else {
        ValuationSetMapUtil.remove(edges, predicate);
      }

      return removeState;
    });
  }

  @Override
  public void removeUnreachableStates(Iterable<S> start, Consumer<S> removedStatesConsumer) {
    Set<S> reachableStates = getReachableStates(start);
    List<S> statesToRemove = new ArrayList<>();

    for (S state : transitions.keySet()) {
      if (!reachableStates.contains(state)) {
        statesToRemove.add(state);
      }
    }

    removeStates(statesToRemove);
    statesToRemove.forEach(removedStatesConsumer);
  }

  @Override
  public void setInitialStates(Iterable<S> states) {
    initialStates.clear();
    addInitialStates(states);
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
      getAcceptance(), ImmutableSet.copyOf(initialStates), stateCount(), options);

    transitions.forEach((state, edges) -> {
      hoa.addState(state);
      edges.forEach(hoa::addEdge);
      hoa.notifyEndOfState();
    });

    hoa.notifyEnd();
  }
}

