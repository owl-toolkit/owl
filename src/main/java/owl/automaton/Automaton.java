/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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

import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;

/**
 * The base interface providing read access to an automaton. If mutation is required either the
 * {@link MutableAutomaton} interface or an on-the-fly view from {@link Views} can be used.
 *
 * <p>The methods of the interface are always referring to the set of states reachable from
 * the initial states, especially {@link Automaton#size}, {@link Automaton#states()},
 * {@link Automaton#accept(EdgeVisitor)}, {@link Automaton#accept(HybridVisitor)},
 * {@link Automaton#accept(HybridVisitor)}, {@link Automaton#predecessors(Object)} only refer to
 * the from the initial states reachable set.</p>
 *
 * <p>All methods throw an {@link IllegalArgumentException} on a best-effort basis if they detect
 * that a state given as an argument is not reachable from the initial states. Note that this
 * behavior cannot be guaranteed, as it is, generally speaking, extremely expensive to check this
 * for on-the-fly constructed automata. Therefore, it would be wrong to write a program that
 * depended on this exception for its correctness: this should be only used to detect bugs.</p>
 *
 * <p>Further, every state-related operation (e.g., {@link #successors(Object)}) should be unique,
 * while edge-related operations may yield duplicates. For example,
 * {@link #forEachEdge(Object, Consumer)} may yield the same state-edge pair multiple times.</p>
 *
 * @param <S> the type of the states of the automaton
 * @param <A> the type of the omega-acceptance condition of the automaton
 */
public interface Automaton<S, A extends OmegaAcceptance> {

  // Parameters

  default String name() {
    return this.getClass() + " for " + this.initialStates();
  }

  /**
   * Returns the acceptance condition of this automaton.
   *
   * @return The acceptance.
   */
  A acceptance();

  ValuationSetFactory factory();


  // Initial states

  /**
   * Returns the initial state. Throws an {@link NoSuchElementException} if there is no and
   * {@link IllegalStateException} if there are multiple initial states.
   *
   * @return The unique initial state.
   *
   * @throws NoSuchElementException
   *     If there is no initial state.
   * @throws IllegalStateException
   *     If there are multiple initial states.
   *
   * @see #initialStates()
   */
  default S onlyInitialState() {
    Iterator<S> iterator = initialStates().iterator();
    S first = iterator.next();

    if (!iterator.hasNext()) {
      return first;
    }

    throw new IllegalStateException("Multiple initial states: " + initialStates().toString());
  }

  /**
   * Returns the set of initial states, which can potentially be empty.
   *
   * @return The set of initial states.
   */
  Set<S> initialStates();


  // State-set properties

  /**
   * Returns the number of reachable states of this automaton (its cardinality). If this
   * set contains more than {@code Integer.MAX_VALUE} elements, it returns
   * {@code Integer.MAX_VALUE}.
   *
   * @return the number of elements in this set (its cardinality)
   *
   * @see #states()
   */
  default int size() {
    return states().size();
  }

  /**
   * The set of all from the initial states reachable states in this automaton.
   *
   * @return All reachable states
   */
  Set<S> states();

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
  Collection<Edge<S>> edges(S state, BitSet valuation);

  /**
   * Returns the successor edge of the specified {@code state} under the given {@code valuation}.
   * Returns some edge if there is a non-deterministic choice in this state for the specified
   * valuation.
   *
   * <p>If you want to check if this is the unique edge use the {@link #edges(Object, BitSet)}
   * method.</p>
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
  Collection<Edge<S>> edges(S state);

  /**
   * This call is semantically equivalent to {@code edges(state).forEach(action)}.
   *
   * @param state state
   * @param action action
   */
  default void forEachEdge(S state, Consumer<Edge<S>> action) {
    edges(state).forEach(action);
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
  Map<Edge<S>, ValuationSet> labelledEdges(S state);

  /**
   * This call is semantically equivalent to {@code labelledEdges(state).forEach(action)}.
   *
   * @param state state
   * @param action action
   */
  default void forEachLabelledEdge(S state, BiConsumer<Edge<S>, ValuationSet> action) {
    labelledEdges(state).forEach(action);
  }


  // Derived state functions

  /**
   * Returns the successors of the specified {@code state} under the given {@code valuation}.
   *
   * @param state
   *     The starting state of the transition.
   * @param valuation
   *     The valuation.
   *
   * @return The successor set.
   */
  default Set<S> successors(S state, BitSet valuation) {
    return new HashSet<>(Collections2.transform(edges(state, valuation), Edge::successor));
  }

  /**
   * Returns the successor of the specified {@code state} under the given {@code valuation}.
   * Returns some state if there is a non-deterministic choice in this state for the specified
   * valuation.
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

  /**
   * Returns all successors of the specified {@code state}.
   *
   * @param state
   *     The starting state of the transition.
   *
   * @return The successor set.
   */
  Set<S> successors(S state);

  /**
   * Returns the predecessors of the specified {@code state}.
   *
   * @param state
   *     The starting state of the transition.
   *
   * @return The predecessor set.
   */
  default Set<S> predecessors(S state) {
    Set<S> predecessors = new HashSet<>();

    HybridVisitor<S> visitor = new HybridVisitor<>() {
      boolean isPredecessor = false;

      @Override
      public void enter(S state) {
        isPredecessor = false;
      }

      @Override
      public void exit(S state) {
        if (isPredecessor) {
          predecessors.add(state);
        }
      }

      @Override
      public void visitEdge(Edge<S> edge, BitSet valuation) {
        if (!isPredecessor) {
          isPredecessor = edge.successor().equals(state);
        }
      }

      @Override
      public void visitLabelledEdge(Edge<S> edge, ValuationSet valuationSet) {
        if (!isPredecessor) {
          isPredecessor = edge.successor().equals(state);
        }
      }
    };

    this.accept(visitor);
    return predecessors;
  }

  // Automaton Visitor

  default void accept(EdgeVisitor<S> visitor) {
    DefaultImplementations.visit(this, visitor);
  }

  default void accept(LabelledEdgeVisitor<S> visitor) {
    DefaultImplementations.visit(this, visitor);
  }

  /**
   * Traverse all edges of the automaton using the preferred visitor style. The iteration order is
   * not specified and can be arbitrary.
   *
   * @param visitor the visitor.
   */
  default void accept(HybridVisitor<S> visitor) {
    if (prefersLabelled()) {
      this.accept((LabelledEdgeVisitor<S>) visitor);
    } else {
      this.accept((EdgeVisitor<S>) visitor);
    }
  }

  // Properties

  /**
   * Indicate if the automaton implements a fast computation (e.g. symbolic) of labelled edges.
   * Returns {@code true}, if the automaton advices to use {@link Automaton#labelledEdges(Object)}
   * and {@link Automaton#accept(LabelledEdgeVisitor)} for accessing all outgoing edges of a state.
   *
   * @return The preferred traversal method.
   */
  boolean prefersLabelled();

  default boolean is(Property property) {
    switch (property) {
      case COMPLETE:
        return Properties.isComplete(this);

      case DETERMINISTIC:
        return Properties.isDeterministic(this);

      case SEMI_DETERMINISTIC:
        return Properties.isSemiDeterministic(this);

      case LIMIT_DETERMINISTIC:
        if (acceptance() instanceof GeneralizedBuchiAcceptance) {
          return AutomatonUtil.ldbaSplit(
            AutomatonUtil.cast(this, GeneralizedBuchiAcceptance.class)).isPresent();
        }

        return false;

      default:
        throw new UnsupportedOperationException("Property detection for " + property
          + " is not implemented");
    }
  }

  enum Property {
    COMPLETE, DETERMINISTIC, SEMI_DETERMINISTIC, LIMIT_DETERMINISTIC
  }

  interface Visitor<S> {
    void enter(S state);

    void exit(S state);
  }

  interface LabelledEdgeVisitor<S> extends Visitor<S> {
    void visitLabelledEdge(Edge<S> edge, ValuationSet valuationSet);
  }

  interface EdgeVisitor<S> extends Visitor<S> {
    void visitEdge(Edge<S> edge, BitSet valuation);
  }

  interface HybridVisitor<S> extends EdgeVisitor<S>, LabelledEdgeVisitor<S> {
  }
}
