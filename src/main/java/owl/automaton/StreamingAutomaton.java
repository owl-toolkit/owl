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

import com.google.common.collect.Collections2;
import com.google.common.collect.Sets;
import de.tum.in.naturals.bitset.BitSets;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import jhoafparser.consumer.HOAConsumer;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.LabelledEdge;
import owl.automaton.output.HoaConsumerExtended;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;

public class StreamingAutomaton<S, A extends OmegaAcceptance> implements Automaton<S, A> {
  private final A acceptance;
  private final ValuationSetFactory factory;
  private final S initialState;
  private final BiFunction<S, BitSet, Edge<S>> successors;

  @Nullable
  private Set<S> cachedStates = null;

  StreamingAutomaton(S initialState, BiFunction<S, BitSet, Edge<S>> successorFunction, A acceptance,
    ValuationSetFactory factory) {
    this.acceptance = acceptance;
    this.factory = factory;
    this.initialState = initialState;
    this.successors = successorFunction;
  }

  private void computeEdges(S state, BiConsumer<BitSet, Edge<S>> consumer) {
    for (BitSet valuation : BitSets.powerSet(factory.alphabetSize())) {
      Edge<S> edge = successors.apply(state, valuation);

      if (edge == null) {
        continue;
      }

      consumer.accept(valuation, edge);
    }
  }

  private Map<Edge<S>, ValuationSet> computeEdgeMap(S state) {
    Map<Edge<S>, ValuationSet> edgeMap = new HashMap<>();

    for (BitSet valuation : BitSets.powerSet(factory.alphabetSize())) {
      Edge<S> edge = successors.apply(state, valuation);

      if (edge != null) {
        edgeMap.merge(edge, factory.of(valuation), ValuationSet::union);
      }
    }

    return edgeMap;
  }

  private Set<S> exploreReachableStates(@Nullable Consumer<S> enterStateCallback,
    @Nullable Consumer<S> exitStateCallback,
    @Nullable BiConsumer<Edge<S>, BitSet> visitEdge) {
    Set<S> exploredStates = Sets.newHashSet(initialState);
    Queue<S> workQueue = new ArrayDeque<>(exploredStates);

    while (!workQueue.isEmpty()) {
      S state = workQueue.poll();

      if (enterStateCallback != null) {
        enterStateCallback.accept(state);
      }

      computeEdges(state, (valuation, edge) -> {
        S successorState = edge.getSuccessor();

        if (exploredStates.add(successorState)) {
          workQueue.add(successorState);
        }

        if (visitEdge != null) {
          visitEdge.accept(edge, valuation);
        }
      });

      if (exitStateCallback != null) {
        exitStateCallback.accept(state);
      }
    }

    return Set.copyOf(exploredStates);
  }

  @Override
  public A getAcceptance() {
    return acceptance;
  }

  @Nullable
  @Override
  public S getSuccessor(S state, BitSet valuation) {
    Edge<S> edge = getEdge(state, valuation);
    return edge == null ? null : edge.getSuccessor();
  }

  @Override
  public Set<S> getSuccessors(S state) {
    Set<S> successors = new HashSet<>();
    computeEdges(state, (x, edge) -> successors.add(edge.getSuccessor()));
    return successors;
  }

  @Override
  public Set<S> getSuccessors(S state, BitSet valuation) {
    S successor = getSuccessor(state, valuation);
    return successor == null ? Set.of() : Set.of(successor);
  }

  @Nullable
  @Override
  public Edge<S> getEdge(S state, BitSet valuation) {
    return successors.apply(state, valuation);
  }

  @Override
  public Set<Edge<S>> getEdges(S state) {
    Set<Edge<S>> edges = new HashSet<>();
    computeEdges(state, (x, edge) -> edges.add(edge));
    return edges;
  }

  @Override
  public Set<Edge<S>> getEdges(S state, BitSet valuation) {
    Edge<S> edge = getEdge(state, valuation);
    return edge == null ? Set.of() : Set.of(edge);
  }

  @Override
  public ValuationSetFactory getFactory() {
    return factory;
  }

  @Override
  public Set<S> getInitialStates() {
    return Set.of(initialState);
  }

  @Override
  public Collection<LabelledEdge<S>> getLabelledEdges(S state) {
    return Collections2.transform(computeEdgeMap(state).entrySet(), LabelledEdge::of);
  }

  @Override
  public Set<S> getStates() {
    if (cachedStates != null) {
      return cachedStates;
    }

    return cachedStates = exploreReachableStates(null, null, null);
  }

  @Override
  public boolean is(Property property) {
    return property == Property.DETERMINISTIC || Automaton.super.is(property);
  }

  @Override
  public void toHoa(HOAConsumer consumer, EnumSet<HoaOption> options) {
    HoaConsumerExtended<S> hoa = new HoaConsumerExtended<>(consumer, getVariables(),
      acceptance, Set.of(initialState), options, true, getName());
    exploreReachableStates(hoa::addState, x -> hoa.notifyEndOfState(), hoa::addEdge);
    hoa.notifyEnd();
  }
}
