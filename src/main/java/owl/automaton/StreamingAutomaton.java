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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
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
import owl.collections.ints.BitSets;
import owl.factories.ValuationSetFactory;

class StreamingAutomaton<S, A extends OmegaAcceptance> implements Automaton<S, A> {

  private final A acceptance;
  private final BiFunction<S, BitSet, Edge<S>> computeDeterministicSuccessors;
  private final ValuationSetFactory factory;
  private final ImmutableSet<S> initialStates;
  private ImmutableList<String> variables;

  StreamingAutomaton(A acceptance,
    ValuationSetFactory factory,
    Collection<S> initialStates,
    BiFunction<S, BitSet, Edge<S>> computeDeterministicSuccessors) {
    this.acceptance = acceptance;
    this.factory = factory;
    this.initialStates = ImmutableSet.copyOf(initialStates);
    this.computeDeterministicSuccessors = computeDeterministicSuccessors;
    this.variables = ImmutableList.of();
  }

  private Set<S> exploreReachableStates(Collection<S> start,
    @Nullable Consumer<S> enterStateCallback,
    @Nullable Consumer<S> exitStateCallback,
    @Nullable BiConsumer<Edge<S>, BitSet> visitEdge) {
    Set<S> exploredStates = Sets.newHashSet(start);
    Queue<S> workQueue = new ArrayDeque<>(exploredStates);

    while (!workQueue.isEmpty()) {
      S state = workQueue.poll();

      if (enterStateCallback != null) {
        enterStateCallback.accept(state);
      }

      getLabelledEdges(state, (valuation, edge) -> {
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

    return exploredStates;
  }

  @Override
  public A getAcceptance() {
    return acceptance;
  }

  @Override
  public ValuationSetFactory getFactory() {
    return factory;
  }

  @Override
  public Map<S, ValuationSet> getIncompleteStates() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Set<S> getInitialStates() {
    return initialStates;
  }

  private void getLabelledEdges(S state, BiConsumer<BitSet, Edge<S>> consumer) {
    BitSet alphabet = new BitSet();
    alphabet.set(0, factory.getSize());

    for (BitSet valuation : BitSets.powerSet(alphabet)) {
      Edge<S> edge = computeDeterministicSuccessors.apply(state, valuation);

      if (edge == null) {
        continue;
      }

      consumer.accept(valuation, edge);
    }
  }

  @Override
  public Collection<LabelledEdge<S>> getLabelledEdges(S state) {
    Set<LabelledEdge<S>> edges = new HashSet<>();

    getLabelledEdges(state, (valuation, edge) ->
      edges.add(new LabelledEdge<>(edge, factory.createValuationSet(valuation))));

    return edges;
  }

  @Override
  public Set<S> getReachableStates(Collection<S> start) {
    return exploreReachableStates(start, null, null, null);
  }

  @Override
  public Set<S> getStates() {
    return getReachableStates(initialStates);
  }

  @Override
  public List<String> getVariables() {
    return variables;
  }

  @Override
  public void setVariables(List<String> variables) {
    this.variables = ImmutableList.copyOf(variables);
  }

  @Override
  public void toHoa(HOAConsumer consumer, EnumSet<Option> options) {
    HoaConsumerExtended<S> hoa = new HoaConsumerExtended<>(consumer, getVariables(),
      acceptance, initialStates, -1, options, true);

    exploreReachableStates(initialStates, hoa::addState, (x) -> hoa.notifyEndOfState(),
      hoa::addEdge);

    hoa.notifyEnd();
  }
}
