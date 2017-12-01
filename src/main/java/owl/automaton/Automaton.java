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
import java.util.BitSet;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NoSuchElementException;
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
      getLabelledEdges(x).forEach((y) -> action.accept(x, y.edge, y.valuations)));
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
   * <p>Returns the successor edge of the specified {@code state} under the given {@code valuation}.
   * Returns some edge if there is a non-deterministic choice in this state for the specified
   * valuation.</p>
   *
   * <p>If you want to check if this is the unique edge use the getEdges() method.</p>
   *
   * @param state
   *     The starting state of the transition.
   * @param valuation
   *     The valuation.
   *
   * @return A successor edge or {@code null} if none.
   *
   * @see #getLabelledEdges(Object)
   */
  @Nullable
  default Edge<S> getEdge(S state, BitSet valuation) {
    return Iterables.getFirst(getEdges(state, valuation), null);
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
    Map<S, ValuationSet> successorMap = new HashMap<>();
    forEachLabelledEdge(state, (edge, valuations) ->
      ValuationSetMapUtil.add(successorMap, edge.getSuccessor(), valuations.copy()));
    return successorMap;
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

  default boolean is(Property property) {
    switch (property) {
      case COMPLETE:
        return Properties.isComplete(this);

      case DETERMINISTIC:
        return Properties.isDeterministic(this);

      case NON_DETERMINISTIC:
        return !Properties.isDeterministic(this);

      default:
        throw new UnsupportedOperationException("Property Detection for " + property
          + " is not implemented");
    }
  }

  @Override
  default void toHoa(HOAConsumer consumer, EnumSet<HoaOption> options) {
    HoaConsumerExtended<S> hoa = new HoaConsumerExtended<>(consumer, getVariables(),
      getAcceptance(), getInitialStates(), options, is(Property.DETERMINISTIC), getName());

    getStates().forEach(state -> {
      hoa.addState(state);
      forEachLabelledEdge(state, hoa::addEdge);
      hoa.notifyEndOfState();
    });

    hoa.notifyEnd();
  }

  enum Property {
    COMPLETE, DETERMINISTIC, LIMIT_DETERMINISTIC, NON_DETERMINISTIC, TERMINAL, WEAK
  }
}
