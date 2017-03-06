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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.util.BitSet;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import jhoafparser.consumer.HOAConsumer;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.LabelledEdge;
import owl.automaton.output.HoaConsumerExtended;
import owl.automaton.output.HoaPrintable;
import owl.factories.ValuationSetFactory;

/**
 * Note: Every implementation should support concurrent read-access.
 */
public interface Automaton<S, A extends OmegaAcceptance> extends HoaPrintable {
  /**
   * Returns the acceptance condition of this automaton.
   *
   * @return The acceptance.
   */
  A getAcceptance();

  /**
   * Returns the successor edge of the specified {@code state} under the given {@code valuation}.
   * Throws an {@link IllegalArgumentException} if there is a non-deterministic choice or no
   * successor in this state for the specified valuation.
   *
   * @param state
   *     The starting state of the transition.
   * @param valuation
   *     The valuation.
   *
   * @return The unique successor edge.
   *
   * @throws IllegalArgumentException
   *     If the edge has multiple successor edges.
   * @see #getLabelledEdges(S)
   */
  @Nullable
  default Edge<S> getEdge(S state, BitSet valuation) {
    return Iterables.getOnlyElement(getEdges(state, valuation), null);
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
  default Collection<Edge<S>> getEdges(S state, BitSet valuation) {
    return StreamSupport.stream(getLabelledEdges(state).spliterator(), false)
      .filter(labelledEdge -> labelledEdge.valuations.contains(valuation))
      .map(labelledEdge -> labelledEdge.edge)
      .collect(Collectors.toList());
  }

  ValuationSetFactory getFactory();

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
  ImmutableSet<S> getInitialStates();

  /**
   * Returns all successors of the specified {@code state}.
   *
   * @param state
   *     The state.
   *
   * @return All successors of the state.
   */
  Iterable<LabelledEdge<S>> getLabelledEdges(S state);

  /**
   * Returns all states reachable from the initial states.
   *
   * @return All reachable states.
   *
   * @see #getReachableStates(Iterable)
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
  Set<S> getReachableStates(Iterable<S> start);

  /**
   * Returns all states in this automaton.
   *
   * @return All states of the automaton
   */
  Set<S> getStates();

  @Nullable
  default S getSuccessor(S state, BitSet valuation) {
    Edge<S> edge = getEdge(state, valuation);

    if (edge == null) {
      return null;
    }

    return edge.getSuccessor();
  }

  default Set<S> getSuccessors(S state) {
    Set<S> successors = new HashSet<>();
    getLabelledEdges(state)
      .forEach(labelledEdge -> successors.add(labelledEdge.edge.getSuccessor()));
    return successors;
  }

  default Collection<S> getSuccessors(S state, BitSet valuation) {
    return StreamSupport.stream(getLabelledEdges(state).spliterator(), false)
      .filter(labelledEdge -> labelledEdge.valuations.contains(valuation))
      .map(labelledEdge -> labelledEdge.edge.getSuccessor())
      .collect(Collectors.toList());
  }

  List<String> getVariables();

  /**
   * Determines whether the automaton is complete, i.e. every state has at least one successor for
   * each valuation.
   *
   * @return Whether the automaton is complete.
   *
   * @see AutomatonUtil#isComplete(Iterable)
   */
  default boolean isComplete() {
    return getStates().stream().allMatch(s -> AutomatonUtil.isComplete(getLabelledEdges(s)));
  }

  /**
   * Determines whether the automaton is deterministic, i.e. there is exactly one initial state and
   * every state has at most one successor under each valuation.
   *
   * @return Whether the automaton is deterministic.
   *
   * @see AutomatonUtil#isDeterministic(Iterable)
   */
  default boolean isDeterministic() {
    return getInitialStates().size() == 1
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
  default void toHoa(HOAConsumer consumer, EnumSet<Option> options) {
    HoaConsumerExtended<S> hoa = new HoaConsumerExtended<>(consumer, getVariables(),
      getAcceptance(), getInitialStates(), stateCount(), options);

    for (S state : getStates()) {
      hoa.addState(state);
      getLabelledEdges(state).forEach(hoa::addEdge);
      hoa.notifyEndOfState();
    }

    hoa.notifyEnd();
  }
}
