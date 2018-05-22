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

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import jhoafparser.consumer.HOAConsumer;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.LabelledEdge;
import owl.automaton.output.HoaConsumerExtended;
import owl.automaton.output.HoaPrintable;
import owl.collections.Collections3;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;
import owl.util.TriConsumer;

/**
 * Note: Every implementation should support concurrent read-access. Further, every state-related
 * operation (e.g., {@link #successors(Object)}) should be unique, while edge-related operations
 * may yield duplicates. For example, {@link #forEachState(Consumer)} should pass a state to the
 * action exactly once, while {@link #forEachEdge(BiConsumer)} may yield the same state-edge pair
 * multiple times.
 */
public interface Automaton<S, A extends OmegaAcceptance> extends HoaPrintable {

  // Parameters

  /**
   * Returns the acceptance condition of this automaton.
   *
   * @return The acceptance.
   */
  A acceptance();

  ValuationSetFactory factory();

  @Override
  default List<String> variables() {
    return factory().alphabet();
  }


  // Initial states

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
   * @see #initialStates()
   */
  default S onlyInitialState() {
    return Iterables.getOnlyElement(initialStates());
  }

  /**
   * Returns the set of initial states, which can potentially be empty.
   *
   * @return The set of initial states.
   */
  Set<S> initialStates();


  // State-set properties

  /**
   * Returns the number of states of this automaton (its cardinality). If this
   * set contains more than {@code Integer.MAX_VALUE} elements, returns
   * {@code Integer.MAX_VALUE}.
   *
   * @return the number of elements in this set (its cardinality)
   */
  default int size() {
    return states().size();
  }

  /**
   * Returns all states in this automaton.
   *
   * @return All states of the automaton
   */
  Set<S> states();

  default void forEachState(Consumer<S> action) {
    states().forEach(action);
  }

  // Transition function - Single

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
  default Set<Edge<S>> edges(S state, BitSet valuation) {
    return Collections3.transformUnique(labelledEdges(state),
      x -> x.valuations.contains(valuation) ? x.edge : null);
  }

  /**
   * Returns the successor edge of the specified {@code state} under the given {@code valuation}.
   * Returns some edge if there is a non-deterministic choice in this state for the specified
   * valuation.
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
   * @see #labelledEdges(Object)
   */
  @Nullable
  default Edge<S> edge(S state, BitSet valuation) {
    return Iterables.getFirst(edges(state, valuation), null);
  }

  /**
   * Returns all successor edges of the specified {@code state} under any valuation.
   *
   * @param state
   *     The starting state of the edges.
   *
   * @return The set of edges originating from {@code state}
   */
  default Set<Edge<S>> edges(S state) {
    return Collections3.transformUnique(labelledEdges(state), x -> x.edge);
  }

  default void forEachEdge(BiConsumer<S, Edge<S>> action) {
    forEachState(state -> forEachEdge(state, edge -> action.accept(state, edge)));
  }

  default void forEachEdge(S state, Consumer<Edge<S>> action) {
    factory().forEach(valuation -> edges(state, valuation).forEach(action));
  }

  // Transition function - Bulk

  /**
   * Returns all labelled edges of the specified {@code state}.
   *
   * @param state
   *     The state.
   *
   * @return All labelled edges of the state.
   */
  Collection<LabelledEdge<S>> labelledEdges(S state);

  default void forEachLabelledEdge(S state, BiConsumer<Edge<S>, ValuationSet> action) {
    labelledEdges(state).forEach(x -> action.accept(x.edge, x.valuations));
  }

  default void forEachLabelledEdge(TriConsumer<S, Edge<S>, ValuationSet> action) {
    forEachState(state ->
      forEachLabelledEdge(state, (edge, valuations) -> action.accept(state, edge, valuations)));
  }


  // Derived state functions

  default Set<S> successors(S state, BitSet valuation) {
    return Collections3.transformUnique(edges(state, valuation), Edge::successor);
  }

  /**
   * <p>Returns the successor of the specified {@code state} under the given {@code valuation}.
   * Returns some state if there is a non-deterministic choice in this state for the specified
   * valuation.</p>
   *
   * <p>If you want to check if this is the unique edge use the getSuccessors() method.</p>
   *
   * @param state
   *     The starting state of the transition.
   * @param valuation
   *     The valuation.
   *
   * @return A successor or {@code null} if none.
   */
  @Nullable
  default S successor(S state, BitSet valuation) {
    return Iterables.getFirst(successors(state, valuation), null);
  }

  default Set<S> successors(S state) {
    return Collections3.transformUnique(edges(state), Edge::successor);
  }


  default Set<S> predecessors(S state) {
    Set<S> predecessors = new HashSet<>();

    forEachState((predecessor) -> {
      if (successors(predecessor).contains(state)) {
        predecessors.add(predecessor);
      }
    });

    return predecessors;
  }


  // Properties

  default boolean is(Property property) {
    switch (property) {
      case COMPLETE:
        return Properties.isComplete(this);

      case DETERMINISTIC:
        return Properties.isDeterministic(this);

      case NON_DETERMINISTIC:
        return !Properties.isDeterministic(this);

      default:
        throw new UnsupportedOperationException("Property detection for " + property
          + " is not implemented");
    }
  }


  @Override
  default void toHoa(HOAConsumer consumer, EnumSet<HoaOption> options) {
    HoaConsumerExtended<S> hoa = new HoaConsumerExtended<>(consumer, variables(),
      acceptance(), initialStates(), options, is(Property.DETERMINISTIC), name());

    if (this instanceof BulkOperationAutomaton) {
      forEachState(state -> {
        hoa.addState(state);
        forEachLabelledEdge(state, hoa::addEdge);
        hoa.notifyEndOfState();
      });
    } else {
      Set<S> exploredStates = Sets.newHashSet(initialStates());
      Queue<S> workQueue = new ArrayDeque<>(exploredStates);

      while (!workQueue.isEmpty()) {
        S state = workQueue.poll();
        hoa.addState(state);

        factory().forEach(valuation -> {
          Edge<S> edge = this.edge(state, valuation);

          if (edge == null) {
            return;
          }

          S successorState = edge.successor();

          if (exploredStates.add(successorState)) {
            workQueue.add(successorState);
          }

          hoa.addEdge(edge, valuation);
        });

        hoa.notifyEndOfState();
      }
    }

    hoa.notifyEnd();
  }

  enum Property {
    COMPLETE, DETERMINISTIC, LIMIT_DETERMINISTIC, NON_DETERMINISTIC, TERMINAL, WEAK, COLOURED
  }
}
