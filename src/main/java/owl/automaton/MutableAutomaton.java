/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
 *
 * This file is part of Owl.
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
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.edge.Edge;
import owl.bdd.BddSet;

/**
 * A mutation interface extending {@link Automaton}. As the super interface requires that only from
 * the initial states reachable states are present, after any operation that might introduce
 * unreachable states (a list is given below) it is required to call {@link MutableAutomaton#trim()}
 * before calling any method of the {@link Automaton} interface. For methods that do not change
 * the reachability (a list is given below) calling {@link MutableAutomaton#trim()} has no effect
 * and is not required.
 *
 * <p>Methods that <em>do</em> require calling {@link MutableAutomaton#trim()} afterwards:</p>
 *
 * <ul>
 * <li>{@link MutableAutomaton#initialStates(Collection)}
 * <li>{@link MutableAutomaton#removeInitialState(Object)}
 * <li>{@link MutableAutomaton#addState(Object)}
 * <li>{@link MutableAutomaton#removeState(Object)}
 * <li>{@link MutableAutomaton#removeStateIf(Predicate)}
 * <li>{@link MutableAutomaton#removeEdge(Object, BitSet, Object)}
 * <li>{@link MutableAutomaton#removeEdge(Object, BddSet, Object)}
 * <li>{@link MutableAutomaton#updateEdges(BiFunction)}
 * <li>{@link MutableAutomaton#updateEdges(Set, BiFunction)}
 * </ul>
 *
 * <p>Methods that <em>do not</em> require calling {@link MutableAutomaton#trim()} afterwards:</p>
 *
 * <ul>
 * <li>{@link MutableAutomaton#acceptance(EmersonLeiAcceptance)}
 * <li>{@link MutableAutomaton#updateAcceptance(Function)}
 * <li>{@link MutableAutomaton#addInitialState(Object)}
 * <li>{@link MutableAutomaton#addEdge(Object, BitSet, Edge)}
 * <li>{@link MutableAutomaton#addEdge(Object, BddSet, Edge)}
 * <li>{@link MutableAutomaton#trim()}
 * </ul>
 *
 * @param <S> the type of the states of the automaton
 * @param <A> the type of the omega-acceptance condition of the automaton
 */
public interface MutableAutomaton<S, A extends EmersonLeiAcceptance> extends Automaton<S, A> {

  // Acceptance

  void acceptance(A acceptance);

  default void updateAcceptance(Function<? super A, ? extends A> updater) {
    acceptance(updater.apply(acceptance()));
  }


  // Initial states

  /**
   * Sets the set of initial states of the automaton.
   *
   * @param initialStates
   *     The new set of initial states (potentially empty)
   */
  void initialStates(Collection<? extends S> initialStates);

  /**
   * Add an initial state to the automaton.
   *
   * @param initialState
   *     The added initial state.
   */
  void addInitialState(S initialState);

  void removeInitialState(S state);


  // States

  /**
   * Adds a {@code state} without outgoing edges to the set of states. If the state is already
   * present, nothing is changed.
   *
   * @param state The state to be added.
   *
   */
  void addState(S state);

  /**
   * Removes a state and all transitions involving it from the automaton. If the automaton does not
   * contain the specified state, nothing is changed.
   *
   * @param state The state to be removed.
   */
  default void removeState(S state) {
    removeStateIf(state::equals);
  }

  /**
   * Removes the specified {@code states} and all transitions involving them from the automaton.
   *
   * @param states The states to be removed.
   *
   */
  void removeStateIf(Predicate<? super S> states);


  // Transition function

  /**
   * Adds a transition from the {@code source} state under {@code valuation}.
   *
   * @param source
   *     The source state.
   * @param valuation
   *     The valuation under which this transition is possible.
   * @param edge
   *     The respective edge, containing destination and acceptance information. If the successor
   *     is not already present, it gets added to the transition table.
   * @throws IllegalArgumentException
   *     If {@code source} is not contained in the automaton.
   */
  default void addEdge(S source, BitSet valuation, Edge<? extends S> edge) {
    addEdge(source, factory().of(valuation, atomicPropositions().size()), edge);
  }

  /**
   * Adds transitions from the {@code source} state under {@code valuations}.
   *
   * @param source
   *     The source state.
   * @param valuations
   *     The valuations under which this transition is possible.
   * @param edge
   *     The respective edge, containing destination and acceptance information.
   * @throws IllegalArgumentException
   *     If {@code source} is not contained in the automaton
   */
  void addEdge(S source, BddSet valuations, Edge<? extends S> edge);

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
   *
   * @see #removeEdge(Object, BddSet, Object)
   */
  default void removeEdge(S source, BitSet valuation, S destination) {
    removeEdge(source, factory().of(valuation, atomicPropositions().size()), destination);
  }

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
  void removeEdge(S source, BddSet valuations, S destination);

  /**
   * Remaps each outgoing edge of the specified {@code states} according to {@code updater}.
   *
   * <p> The function is allowed to return {@code null} which indicates that the edge should be
   * removed. Requires all {@code states} to be present in the automaton.</p>
   *
   * @param states
   *     The states whose outgoing edges are to be remapped.
   * @param updater
   *     The remapping function. The updater needs to be stateless, since it might be called with
   *     unreachable states.
   *
   * @throws IllegalArgumentException
   *     If any state of {@code states} is not present in the automaton.
   */
  void updateEdges(Set<? extends S> states, BiFunction<? super S, Edge<S>, Edge<S>> updater);

  /**
   * Remaps each edge of the automaton according to {@code updater}.
   *
   * <p>The function is allowed to return {@code null} which indicates that the edge should be
   * removed.</p>
   *
   * @param updater
   *     The remapping function. The updater needs to be stateless, since it might be called with
   *     unreachable states.
   *
   * @see #updateEdges(Set, BiFunction)
   */
  void updateEdges(BiFunction<S, Edge<S>, Edge<S>> updater);

  /**
   * Removes unreachable states and adjust internal data structures after mutation.
   */
  void trim();
}
