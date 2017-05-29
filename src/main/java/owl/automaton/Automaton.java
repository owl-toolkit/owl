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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.BitSet;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;

/**
 * Note: Every implementation should support concurrent read-access.
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
    return containsStates(ImmutableList.of(state));
  }

  /**
   * Determines whether the automaton contains all the given {@code states}.
   *
   * @param states
   *     The states to be checked.
   *
   * @return Whether all of the states are in the automaton.
   */
  default boolean containsStates(Collection<S> states) {
    return getStates().containsAll(states);
  }

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
   * Returns all successor edges of the specified {@code state} under any valuation.
   *
   * @param state
   *     The starting state of the edges.
   *
   * @return The set of edges originating from {@code state}
   */
  default Collection<Edge<S>> getEdges(S state) {
    return Collections2.transform(getLabelledEdges(state), LabelledEdge::getEdge);
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
    //noinspection ConstantConditions
    return Collections2.transform(Collections2.filter(getLabelledEdges(state),
      labelledEdge -> labelledEdge.valuations.contains(valuation)),
      LabelledEdge::getEdge);
  }

  default Set<Edge<S>> getEdges(Set<S> states, BitSet valuation) {
    Set<Edge<S>> edges = new HashSet<>();
    states.forEach(x -> edges.addAll(getEdges(x, valuation)));
    return edges;
  }

  ValuationSetFactory getFactory();

  /**
   * Determines all states which are incomplete, i.e. there are valuations for which the state has
   * no successor. The valuations sets have to be free'd after use.
   *
   * @return The set of incomplete states and the missing valuations.
   */
  Map<S, ValuationSet> getIncompleteStates();

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
  Set<S> getReachableStates(Collection<S> start);

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
    //noinspection StaticPseudoFunctionalStyleMethod,ConstantConditions
    return Sets.newHashSet(Iterables.transform(getLabelledEdges(state),
      labelledEdge -> labelledEdge.edge.getSuccessor()));
  }

  default Set<S> getSuccessors(S state, BitSet valuation) {
    return StreamSupport.stream(getLabelledEdges(state).spliterator(), false)
      .filter(labelledEdge -> labelledEdge.valuations.contains(valuation))
      .map(labelledEdge -> labelledEdge.edge.getSuccessor())
      .collect(Collectors.toSet());
  }

  default Set<S> getSuccessors(Set<S> states, BitSet valuation) {
    Set<S> successors = new HashSet<>();
    states.forEach(x -> successors.addAll(getSuccessors(x, valuation)));
    return successors;
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
      getAcceptance(), getInitialStates(), stateCount(), options, isDeterministic());

    for (S state : getStates()) {
      hoa.addState(state);
      getLabelledEdges(state).forEach(hoa::addEdge);
      hoa.notifyEndOfState();
    }

    hoa.notifyEnd();
  }
}
