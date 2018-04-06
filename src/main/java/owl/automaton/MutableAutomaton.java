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

import java.util.BitSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.ValuationSet;

public interface MutableAutomaton<S, A extends OmegaAcceptance> extends Automaton<S, A> {

  /**
   * Sets the name of the automaton (optional operation).
   *
   * @param name
   *     The new name of the automaton.
   *
   * @throws UnsupportedOperationException
   *     if the automaton does not support setting a name.
   */
  default void name(String name) {
    throw new UnsupportedOperationException();
  }


  // Acceptance

  void acceptance(A acceptance);

  default void updateAcceptance(Function<? super A, ? extends A> updater) {
    acceptance(updater.apply(acceptance()));
  }


  // Initial states

  /**
   * Sets the set of initial states of the automaton. Requires that each state is present in the
   * automaton.
   *
   * @param states
   *     The new set of initial states (potentially empty)
   *
   * @throws IllegalArgumentException
   *     If any of the specified {@code states} is not part of the automaton.
   */
  void initialStates(Collection<? extends S> states);

  /**
   * Set the initial state of the automaton. Requires the specified state to be present.
   *
   * @param state
   *     The new initial state.
   *
   * @throws IllegalArgumentException
   *     If the {@code state} is not part of the automaton.
   */
  default void initialState(S state) {
    initialStates(Set.of(state));
  }


  // States

  /**
   * Adds {@code states} without outgoing edges to the set of states. For already present states,
   * nothing is changed.
   *
   * @param states
   *     The states to be added.
   */
  void addStates(Collection<? extends S> states);

  /**
   * Adds a {@code state} without outgoing edges to the set of states. If the state is already
   * present, nothing is changed.
   *
   * @param state
   *     The state to be added.
   *
   * @see #addStates(Collection)
   */
  default void addState(S state) {
    addStates(Set.of(state));
  }

  /**
   * Removes the specified {@code states} and all transitions involving them from the automaton.
   *
   * @param states
   *     The states to be removed.
   * @return whether some states have been removed.
   */
  boolean removeStates(Predicate<? super S> states);

  /**
   * Removes a state and all transitions involving it from the automaton. If the automaton does not
   * contain the specified state, nothing is changed.
   *
   * @param state
   *     The state to be removed.
   *
   * @see #removeStates(Collection)
   */
  default void removeState(S state) {
    removeStates(Set.of(state));
  }

  /**
   * Removes the specified {@code states} and all transitions involving them from the automaton.
   *
   * @param states
   *     The states to be removed.
   * @return whether some states have been removed.
   * @see #removeStates(Predicate)
   */
  default boolean removeStates(Collection<? extends S> states) {
    return removeStates(states::contains);
  }


  // Transition function

  /**
   * Adds transitions from the {@code source} state under {@code valuations}.
   *
   * @param source
   *     The source state. If is not already present, it gets added to the transition table.
   * @param valuations
   *     The valuations under which this transition is possible.
   * @param edge
   *     The respective edge, containing destination and acceptance information.
   */
  void addEdge(S source, ValuationSet valuations, Edge<? extends S> edge);

  /**
   * Adds a transition from the {@code source} state under any valuation.
   *
   * @param source
   *     The source state. If is not already present, it gets added to the transition table
   * @param edge
   *     The respective edge, containing destination and acceptance information.
   */
  default void addEdge(S source, Edge<? extends S> edge) {
    addEdge(source, factory().universe(), edge);
  }

  /**
   * Adds a transition from the {@code source} state under {@code valuation}.
   *
   * @param source
   *     The source state. If is not already present, it gets added to the transition table
   * @param valuation
   *     The valuation under which this transition is possible.
   * @param edge
   *     The respective edge, containing destination and acceptance information.
   */
  default void addEdge(S source, BitSet valuation, Edge<? extends S> edge) {
    addEdge(source, factory().of(valuation), edge);
  }

  /**
   * Removes all transition from {@code source} under {@code valuation} to {@code destination}.
   * Requires both states to be present in the automaton.
   *
   * @param source
   *     The source state.
   * @param valuation
   *     The valuation.
   * @param destination
   *     The destination state.
   *
   * @throws IllegalArgumentException
   *     If either {@code source} or {@code destination} are not present in the automaton.
   * @see #removeEdge(Object, ValuationSet, Object)
   */
  void removeEdge(S source, BitSet valuation, S destination);

  /**
   * Removes all transition from {@code source} under {@code valuations} to {@code destination}.
   * Requires both states to be present in the automaton.
   *
   * @param source
   *     The source state.
   * @param valuations
   *     The valuations.
   * @param destination
   *     The destination state.
   *
   * @throws IllegalArgumentException
   *     If either {@code source} or {@code destination} are not present in the automaton.
   */
  void removeEdge(S source, ValuationSet valuations, S destination);

  /**
   * Removes all transitions between {@code source} and {@code destination}. Requires both states to
   * be present in the automaton.
   *
   * @param source
   *     The source state.
   * @param destination
   *     The destination state.
   *
   * @throws IllegalArgumentException
   *     If either {@code source} or {@code destination} are not present in the automaton.
   */
  default void removeEdges(S source, S destination) {
    removeEdge(source, factory().universe(), destination);
  }

  /**
   * Remaps each outgoing edge of the specified {@code states} according to {@code updater}.
   *
   * <p> The function is allowed to return {@code null} which indicates that the edge should be
   * removed. Requires all {@code states} to be present in the automaton.</p>
   *
   * @param states
   *     The states whose outgoing edges are to be remapped.
   * @param updater
   *     The remapping function.
   *
   * @throws IllegalArgumentException
   *     If any state specified is not present in the automaton.
   */
  void updateEdges(Set<? extends S> states, BiFunction<? super S, Edge<S>, Edge<S>> updater);

  /**
   * Remaps each edge of the automaton according to {@code updater}.
   *
   * <p>The function is allowed to return {@code null} which indicates that the edge should be
   * removed.</p>
   *
   * @param updater
   *     The remapping function.
   *
   * @see #updateEdges(Set, BiFunction)
   */
  default void updateEdges(BiFunction<? super S, Edge<S>, Edge<S>> updater) {
    updateEdges(states(), updater);
  }


  // Convenience operations

  /**
   * Removes all states which are not reachable from the initial states and returns all removed
   * states.
   *
   * @return All unreachable, removed states.
   *
   * @see #removeUnreachableStates(Collection, Consumer)
   */
  default Set<S> removeUnreachableStates() {
    return removeUnreachableStates(initialStates());
  }

  /**
   * Removes all states which are not reachable from the specified {@code start} set and returns all
   * removed states.
   *
   * @return All unreachable, removed states. The returned set is modifiable.
   *
   * @see #removeUnreachableStates(Collection, Consumer)
   */
  default Set<S> removeUnreachableStates(Collection<? extends S> start) {
    Set<S> unreachableStates = new HashSet<>();
    removeUnreachableStates(start, unreachableStates::add);
    return unreachableStates;
  }

  /**
   * Removes all states which are not reachable from the specified {@code start} set, passing each
   * removed state to {@code removedStatesConsumer}, used for e.g. freeing of BDD nodes or
   * collecting them in a set.
   *
   * @see #removeUnreachableStates(Collection)
   */
  void removeUnreachableStates(Collection<? extends S> start,
    Consumer<? super S> removedStatesConsumer);
}
