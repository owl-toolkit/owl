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
import java.util.BitSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.collections.ValuationSet;

public interface MutableAutomaton<S, A extends OmegaAcceptance> extends Automaton<S, A> {

  /**
   * Adds a transition from {@code source} to {@code edge} under all valuations.
   *
   * @param source
   *     The source state. If is not already present, it gets added to the transition table.
   * @param edge
   *     The respective edge, containing destination and acceptance information.
   */
  void addEdge(S source, Edge<S> edge);

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
  void addEdge(S source, BitSet valuation, Edge<S> edge);

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
  void addEdge(S source, ValuationSet valuations, Edge<S> edge);

  /**
   * Adds a {@code state} to the set of initial states. Requires this state to be present in the
   * automaton.
   *
   * @param state
   *     The state to be added.
   *
   * @throws IllegalArgumentException
   *     If the specified state is not part of the automaton.
   * @see #addInitialStates(Set)
   */
  default void addInitialState(S state) {
    addInitialStates(ImmutableSet.of(state));
  }

  /**
   * Adds given {@code states} to the set of initial states. Requires the states to be present in
   * the automaton.
   *
   * @param states
   *     The states to be added.
   *
   * @throws IllegalArgumentException
   *     If any of the specified states is not part of the automaton.
   */
  void addInitialStates(Set<S> states);

  /**
   * Adds a {@code state} without outgoing edges to the set of states. If the state is already
   * present, nothing is changed.
   *
   * @param state
   *     The state to be added.
   *
   * @see #addStates(Set)
   */
  default void addState(S state) {
    addStates(ImmutableSet.of(state));
  }

  /**
   * Adds {@code states} without outgoing edges to the set of states. For already present states,
   * nothing is changed.
   *
   * @param states
   *     The states to be added.
   */
  void addStates(Set<S> states);

  /**
   * Completes the automaton by adding a sink state obtained from the {@code sinkSupplier} if
   * necessary. The sink state will be obtained, i.e. {@link Supplier#get()} called exactly once,
   * if and only if a sink is added. This state will be returned wrapped in an {@link Optional},
   * if instead no state was added {@link Optional#empty()} is returned. After adding the sink
   * state, the {@code rejectingAcceptanceSupplier} is called to construct a rejecting self-loop.
   * </p>
   * Note: The completion process considers unreachable states.
   *
   * @param sinkSupplier
   *     Supplier of a sink state. Will be called once iff a sink needs to be added.
   * @param rejectingAcceptanceSupplier
   *     Supplier of a rejecting acceptance, called iff a sink state was added.
   *
   * @return The added state or {@code empty} if none was added.
   */
  default Optional<S> complete(Supplier<S> sinkSupplier,
    Supplier<BitSet> rejectingAcceptanceSupplier) {
    Map<S, ValuationSet> incompleteStates = getIncompleteStates();

    if (incompleteStates.isEmpty()) {
      return Optional.empty();
    }

    S sinkState = sinkSupplier.get();
    BitSet rejectingAcceptance = rejectingAcceptanceSupplier.get();
    Edge<S> rejectingEdge = Edges.create(sinkState, rejectingAcceptance);
    addEdge(sinkState, rejectingEdge);
    return Optional.of(sinkState);
  }

  // void explore(S state, Function<S, Iterable<ValuationSet, Edge<S>>> explorationFunction);

  /**
   * Adds the given states and all states transitively reachable through {@code explorationFunction}
   * to the automaton.</p>
   * Note that if some reachable state is already present, the specified transitions still get
   * added, potentially introducing non-determinism. If two states of the given {@code states} can
   * reach a particular state, the resulting transitions only get added once.
   *
   * @param states
   *     The starting states of the exploration.
   * @param explorationFunction
   *     The function describing the transition relation.
   *
   * @see #explore(Iterable, BiFunction, Function)
   */
  default void explore(Iterable<S> states, BiFunction<S, BitSet, Edge<S>> explorationFunction) {
    explore(states, explorationFunction, s -> null);
  }

  /**
   * Adds the given states and all states transitively reachable through {@code explorationFunction}
   * to the automaton. The {@code sensitiveAlphabetOracle} is used to obtain the sensitive
   * alphabet of a particular state, which reduces the number of calls to the exploration function.
   * The oracle is allowed to return {@code null} values, indicating that no alphabet restriction
   * can be obtained.</p>
   * Note that if some reachable state is already present, the specified transitions still get
   * added, potentially introducing non-determinism. If two states of the given {@code states} can
   * reach a particular state, the resulting transitions only get added once.
   *
   * @param states
   *     The starting states of the exploration.
   * @param explorationFunction
   *     The function describing the transition relation.
   */
  void explore(Iterable<S> states, BiFunction<S, BitSet, Edge<S>> explorationFunction,
    Function<S, BitSet> sensitiveAlphabetOracle);

  /**
   * Determines all states which are incomplete, i.e. there are valuations for which the state has
   * no successor.
   *
   * @return The set of incomplete states and the missing valuations.
   */
  Map<S, ValuationSet> getIncompleteStates();

  /**
   * Remaps the acceptance sets of each outgoing edge of the specified {@code states} according to
   * {@code transformer}. Requires all {@code states} to be present in the automaton.
   *
   * @param states
   *     The states whose outgoing edges are to be remapped.
   * @param transformer
   *     The remapping function.
   *
   * @throws IllegalArgumentException
   *     If any state specified is not present in the automaton.
   */
  void remapAcceptance(Set<S> states, IntFunction<Integer> transformer);

  /**
   * Remaps the acceptance sets of each edge in the automaton as specified by {@code f}.
   *
   * @param f
   *     The remapping function.
   */
  void remapAcceptance(BiFunction<S, Edge<S>, BitSet> f);

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
  void removeEdges(S source, S destination);

  /**
   * Removes a state and all transitions involving it from the automaton. If the automaton does not
   * contain the specified state, nothing is changed.
   *
   * @param state
   *     The state to be removed.
   *
   * @see #removeStates(Iterable)
   */
  default void removeState(S state) {
    removeStates(ImmutableSet.of(state));
  }

  /**
   * Removes the specified {@code states} and all transitions involving them from the automaton.
   *
   * @param states
   *     The states to be removed.
   */
  void removeStates(Iterable<S> states);

  /**
   * Removes all states which are not reachable from the initial states and returns all removed
   * states.
   *
   * @return All unreachable, removed states.
   *
   * @see #removeUnreachableStates(Iterable, Consumer)
   */
  default Set<S> removeUnreachableStates() {
    return removeUnreachableStates(getInitialStates());
  }

  /**
   * Removes all states which are not reachable from the initial states, passing each removed state
   * to {@code removedStatesConsumer}, used for e.g. freeing of BDD nodes or collecting them in a
   * set.
   *
   * @see #removeUnreachableStates(Iterable, Consumer)
   */
  default void removeUnreachableStates(Consumer<S> removedStatesConsumer) {
    removeUnreachableStates(getInitialStates(), removedStatesConsumer);
  }

  /**
   * Removes all states which are not reachable from the specified {@code start} set and returns all
   * removed states.
   *
   * @return All unreachable, removed states.
   *
   * @see #removeUnreachableStates(Iterable, Consumer)
   */
  default Set<S> removeUnreachableStates(Iterable<S> start) {
    ImmutableSet.Builder<S> builder = ImmutableSet.builder();
    removeUnreachableStates(start, builder::add);
    return builder.build();
  }

  /**
   * Removes all states which are not reachable from the specified {@code start} set, passing each
   * removed state to {@code removedStatesConsumer}, used for e.g. freeing of BDD nodes or
   * collecting them in a set.
   *
   * @see #removeUnreachableStates(Iterable)
   */
  void removeUnreachableStates(Iterable<S> start, Consumer<S> removedStatesConsumer);

  /**
   * Set the initial state of the automaton. Requires the specified state to be present.
   *
   * @param state
   *     The new initial state.
   *
   * @throws IllegalArgumentException
   *     If the {@code state} is not part of the automaton.
   */
  default void setInitialState(S state) {
    setInitialStates(ImmutableSet.of(state));
  }

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
  void setInitialStates(Iterable<S> states);
}
