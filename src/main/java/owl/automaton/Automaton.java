/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
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

import com.google.common.collect.Iterables;
import de.tum.in.naturals.bitset.BitSets;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import javax.annotation.Nullable;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.OmegaAcceptanceCast;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.bdd.ValuationSet;
import owl.bdd.ValuationSetFactory;
import owl.collections.ValuationTree;

/**
 * The base interface providing read access to an automaton. If mutation is required either the
 * {@link MutableAutomaton} interface or an on-the-fly view from {@link Views} can be used.
 *
 * <p>The methods of the interface are always referring to the set of states reachable from
 * the initial states, i.e., {@link Automaton#states()} and {@link Automaton#predecessors(Object)}
 * only refer to the from the initial states reachable set.</p>
 *
 * <p>All methods throw an {@link IllegalArgumentException} on a best-effort basis if they detect
 * that a state given as an argument is not reachable from the initial states. Note that this
 * behavior cannot be guaranteed, as it is, generally speaking, expensive to check this
 * for on-the-fly constructed automata. Therefore, it would be wrong to write a program that
 * depends on this exception for its correctness: this should be only used to detect bugs.</p>
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

  /**
   * Returns the backing engine for the symbolic representation of edges. Only this engine might be
   * used for the access to edges.
   *
   * @return The symbolic engine used to generate ValuationSets.
   */
  ValuationSetFactory factory();

  // States

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
  Set<Edge<S>> edges(S state, BitSet valuation);

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
   * @see #edgeMap(Object)
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
    switch (preferredEdgeAccess().get(0)) {
      case EDGES:
        var edges = new HashSet<Edge<S>>();

        for (BitSet valuation : BitSets.powerSet(factory().atomicPropositions().size())) {
          edges.addAll(edges(state, valuation));
        }

        return edges;

      case EDGE_TREE:
        return edgeTree(state).flatValues();

      case EDGE_MAP:
        return edgeMap(state).keySet();

      default:
        throw new AssertionError("Unreachable.");
    }
  }

  // Transition function - Symbolic versions

  /**
   * Returns a mapping from all outgoing edges to their valuations of the specified {@code state}.
   *
   * @param state
   *     The state.
   *
   * @return All labelled edges of the state.
   */
  Map<Edge<S>, ValuationSet> edgeMap(S state);

  /**
   * Returns a decision-tree with nodes labelled by literals and sets of edges as leaves.
   *
   * @param state
   *    The state.
   * @return A tree.
   */
  ValuationTree<Edge<S>> edgeTree(S state);

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
    return Edges.successors(edges(state, valuation));
  }

  /**
   * Returns the successor of the specified {@code state} under the given {@code valuation}.
   * Returns some state if there is a non-deterministic choice in this state for the specified
   * valuation.
   *
   * <p>If you want to check if this is the unique edge use the successors() method.</p>
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
  default Set<S> successors(S state) {
    return Edges.successors(edges(state));
  }

  /**
   * Returns the predecessors of the specified {@code successor}.
   *
   * @param successor
   *     The successor for which the predecessor set needs to be computed.
   *
   * @return The predecessor set.
   */
  default Set<S> predecessors(S successor) {
    Set<S> predecessors = new HashSet<>();

    for (S state : states()) {
      if (successors(state).contains(successor)) {
        predecessors.add(state);
      }
    }

    return predecessors;
  }

  // Properties

  /**
   * Indicate if the automaton implements a fast (e.g. symbolic) computation of edges. Returns a
   * {@code List} containing all supported {@code PreferredEdgeAccess} ordered by their preference.
   * Meaning the element at first position (index 0) is the most preferred. Accordingly algorithms
   * can change the use of {@link Automaton#edges(Object, BitSet)},
   * {@link Automaton#edgeMap(Object)}, or {@link Automaton#edgeTree(Object)} for accessing all
   * outgoing edges of a state. This information is also used to dispatch to the right visitor
   * style.
   *
   * @return An ordered list of the traversal methods. The returned list is always complete.
   */
  List<PreferredEdgeAccess> preferredEdgeAccess();

  enum PreferredEdgeAccess {
    EDGES, EDGE_MAP, EDGE_TREE;
  }

  default boolean is(Property property) {
    switch (property) {
      case COMPLETE:
        return !initialStates().isEmpty() && AutomatonUtil.getIncompleteStates(this).isEmpty();

      case DETERMINISTIC:
        return initialStates().size() <= 1 && is(Property.SEMI_DETERMINISTIC);

      case SEMI_DETERMINISTIC:
        return AutomatonUtil.getNondeterministicStates(this).isEmpty();

      case LIMIT_DETERMINISTIC:
        if (acceptance() instanceof GeneralizedBuchiAcceptance) {
          return AutomatonUtil.ldbaSplit(
            OmegaAcceptanceCast.cast(this, GeneralizedBuchiAcceptance.class)).isPresent();
        }

        return false;

      default:
        throw new UnsupportedOperationException("Property detection for " + property
          + " is not implemented");
    }
  }

  enum Property {

    /**
     * An automaton is complete if there is at least on initial state and every state has at least
     * one successor for each valuation.
     */
    COMPLETE,

    /**
     * An automaton is deterministic if there is at most one initial state and every state has at
     * most one successor for each valuation.
     */
    DETERMINISTIC,

    /**
     * An automaton is semi-deterministic if there is at most one initial state and every state has
     * at most one successor for each valuation.
     */
    SEMI_DETERMINISTIC,

    LIMIT_DETERMINISTIC
  }
}
