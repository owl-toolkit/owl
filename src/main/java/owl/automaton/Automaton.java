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
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import jhoafparser.consumer.HOAConsumer;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.LabelledEdge;
import owl.automaton.output.HoaConsumerExtended;
import owl.automaton.output.HoaPrintable;
import owl.collections.Collections3;
import owl.collections.ValuationSet;
import owl.collections.ValuationSetMapUtil;
import owl.factories.ValuationSetFactory;
import owl.util.TriConsumer;

/**
 * Note: Every implementation should support concurrent read-access. Note: Default implementation
 * should only call methods from one layer below: Successors -&gt; Edges -&gt; LabelledEdges
 */
public interface Automaton<S, A extends OmegaAcceptance> extends HoaPrintable {
  /**
   * Determines whether the automaton contains the given {@code state}.
   *
   * @param state
   *     The state to be checked.
   *
   * @return Whether the state is in the automaton.
   */
  default boolean containsState(S state) {
    return containsStates(Set.of(state));
  }

  /**
   * Determines whether the automaton contains all the given {@code states}.
   *
   * @param states
   *     The states to be checked.
   *
   * @return Whether all of the states are in the automaton.
   */
  default boolean containsStates(Collection<? extends S> states) {
    return getStates().containsAll(states);
  }

  default void forEachLabelledEdge(S state, BiConsumer<Edge<S>, ValuationSet> action) {
    getLabelledEdges(state).forEach(y -> action.accept(y.edge, y.valuations));
  }

  default void forEachLabelledEdge(TriConsumer<S, Edge<S>, ValuationSet> action) {
    getStates().forEach(x ->
      getLabelledEdges(x).forEach(y -> action.accept(x, y.edge, y.valuations)));
  }

  default void free() {
    // Only override if needed
  }

  /**
   * Returns the acceptance condition of this automaton.
   *
   * @return The acceptance.
   */
  A getAcceptance();

  /**
   * Returns any successor edge of the specified {@code state} under the given {@code valuation}.
   * This is a faster replacement for {@link #getEdge(Object, BitSet)} if the automaton is known to
   * be deterministic.
   */
  @Nullable
  default Edge<S> getAnyEdge(S state, BitSet valuation) {
    return getEdges(state, valuation).iterator().next();
  }

  /**
   * Returns the successor edge of the specified {@code state} under the given {@code valuation}.
   * Throws an {@link IllegalArgumentException} if there is a non-deterministic choice in this state
   * for the specified valuation.
   *
   * @param state
   *     The starting state of the transition.
   * @param valuation
   *     The valuation.
   *
   * @return The unique successor edge or {@code null} if none.
   *
   * @throws IllegalArgumentException
   *     If the edge has multiple successor edges.
   * @see #getLabelledEdges(Object)
   */
  @Nullable
  default Edge<S> getEdge(S state, BitSet valuation) {
    return Iterables.getOnlyElement(getEdges(state, valuation), null);
  }

  /**
   * Returns all successor edges of the specified {@code state} under any valuation.
   *
   * @param state
   *     The starting state of the edges.
   *
   * @return The set of edges originating from {@code state}
   */
  default Set<Edge<S>> getEdges(S state) {
    return Collections3.transform(getLabelledEdges(state), x -> x.edge);
  }

  /**
   * Returns the successor edges of the specified {@code state} under the given {@code valuation}.
   *
   * @param state
   *     The starting state of the transition.
   * @param valuation
   *     The valuation.
   *
   * @return The successor edges, possibly empty.
   */
  default Set<Edge<S>> getEdges(S state, BitSet valuation) {
    return Collections3.transform(getLabelledEdges(state),
      x -> x.valuations.contains(valuation) ? x.edge : null);
  }

  ValuationSetFactory getFactory();

  /**
   * Determines all states which are incomplete, i.e. there are valuations for which the state has
   * no successor. The valuations sets have to be free'd after use.
   *
   * @return The set of incomplete states and the missing valuations.
   */
  default Map<S, ValuationSet> getIncompleteStates() {
    Map<S, ValuationSet> incompleteStates = new HashMap<>();
    ValuationSetFactory vsFactory = getFactory();

    getStates().forEach(state -> {
      ValuationSet unionSet = vsFactory.createEmptyValuationSet();
      forEachLabelledEdge(state, (edge, valuations) -> unionSet.addAll(valuations));

      // State is incomplete; complement() creates a new, referenced node.
      if (!unionSet.isUniverse()) {
        incompleteStates.put(state, unionSet.complement());
      }

      unionSet.free();
    });

    return incompleteStates;
  }

  /**
   * Returns the initial state. Throws an {@link IllegalStateException} if there a no or multiple
   * initial states.
   *
   * @return The unique initial state.
   *
   * @throws NoSuchElementException
   *     If there is no initial state.
   * @throws IllegalArgumentException
   *     If there is no unique initial state.
   * @see #getInitialStates()
   */
  default S getInitialState() {
    return Iterables.getOnlyElement(getInitialStates());
  }

  /**
   * Returns the set of initial states, which can potentially be empty.
   *
   * @return The set of initial states.
   */
  Set<S> getInitialStates();

  /**
   * Returns all successors of the specified {@code state}.
   *
   * @param state
   *     The state.
   *
   * @return All successors of the state.
   */
  Collection<LabelledEdge<S>> getLabelledEdges(S state);

  default Set<S> getPredecessors(S state) {
    Set<S> predecessors = new HashSet<>();

    forEachLabelledEdge((predecessor, edge, valuationSet) -> {
      if (state.equals(edge.getSuccessor())) {
        predecessors.add(predecessor);
      }
    });

    return predecessors;
  }

  /**
   * Returns all states reachable from the initial states.
   *
   * @return All reachable states.
   *
   * @see #getReachableStates(Collection)
   */
  default Set<S> getReachableStates() {
    return getReachableStates(getInitialStates());
  }

  /**
   * Returns all states reachable from the given set of states.
   *
   * @param start
   *     Starting states for the reachable states search.
   */
  default Set<S> getReachableStates(Collection<? extends S> start) {
    Set<S> exploredStates = Sets.newHashSet(start);
    Queue<S> workQueue = new ArrayDeque<>(exploredStates);

    while (!workQueue.isEmpty()) {
      getSuccessors(workQueue.poll()).forEach(successor -> {
        if (exploredStates.add(successor)) {
          workQueue.add(successor);
        }
      });
    }

    return exploredStates;
  }

  /**
   * Returns all states in this automaton.
   *
   * @return All states of the automaton
   */
  Set<S> getStates();

  @Nullable
  default S getSuccessor(S state, BitSet valuation) {
    return Iterables.getOnlyElement(getSuccessors(state, valuation), null);
  }

  default Map<S, ValuationSet> getSuccessorMap(S state) {
    Collection<LabelledEdge<S>> labelledEdges = getLabelledEdges(state);
    Map<S, ValuationSet> successors = new HashMap<>(labelledEdges.size());
    for (LabelledEdge<S> edge : labelledEdges) {
      ValuationSetMapUtil.add(successors, edge);
    }
    return Collections.unmodifiableMap(successors);
  }

  default Set<S> getSuccessors(S state) {
    return Collections3.transform(getEdges(state), Edge::getSuccessor);
  }

  default Set<S> getSuccessors(S state, BitSet valuation) {
    return Collections3.transform(getEdges(state, valuation), Edge::getSuccessor);
  }

  @Override
  default ImmutableList<String> getVariables() {
    return getFactory().getAlphabet();
  }

  /**
   * Determines whether the automaton is complete, i.e. every state has at least one successor for
   * each valuation.
   *
   * @return Whether the automaton is complete.
   *
   * @see AutomatonUtil#isComplete(Iterable)
   */
  default boolean isComplete() {
    Set<S> states = getStates();
    return !states.isEmpty()
      && states.stream().allMatch(s -> AutomatonUtil.isComplete(getLabelledEdges(s)));
  }

  /**
   * Determines whether the automaton is deterministic, i.e. there is at most one initial state and
   * every state has at most one successor under each valuation.
   *
   * @return Whether the automaton is deterministic.
   *
   * @see AutomatonUtil#isDeterministic(Iterable)
   */
  default boolean isDeterministic() {
    return getInitialStates().size() <= 1
      && getStates().stream().allMatch(s -> AutomatonUtil.isDeterministic(getLabelledEdges(s)));
  }

  /**
   * Returns the amount of states in this automaton.
   *
   * @return Number of states.
   *
   * @see #getStates()
   */
  default int stateCount() {
    return getStates().size();
  }

  @Override
  default void toHoa(HOAConsumer consumer, EnumSet<HoaOption> options) {
    HoaConsumerExtended<S> hoa = new HoaConsumerExtended<>(consumer, getVariables(),
      getAcceptance(), getInitialStates(), stateCount(), options, isDeterministic(), getName());

    getStates().forEach(state -> {
      hoa.addState(state);
      forEachLabelledEdge(state, hoa::addEdge);
      hoa.notifyEndOfState();
    });

    hoa.notifyEnd();
  }
}
