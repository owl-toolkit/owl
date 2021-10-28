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

package owl.automaton.algorithm.simulations;

import com.google.auto.value.AutoValue;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import owl.automaton.Automaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.bdd.BddSet;

/**
 * Abstraction of a single pebble in a multipebble simulation game. This holds a state on an
 * automaton as well as a flag indicating whether the pebble has seen a final state.
 *
 * @param <S> The type of state for the underlying automaton.
 */
@AutoValue
public abstract class Pebble<S> {
  /**
   * Constructs a pebble on the given state with the given flag.
   */
  static <S> Pebble<S> of(S state, boolean flag) {
    return new AutoValue_Pebble<>(state, flag);
  }

  /**
   * Computes the set of all possible pebbles for a given state.
   *
   * @param state A state in an automaton
   * @param <S>   The type of the state.
   * @return Set of possible Pebbles on this state, i.e. with flag set to true and set to false.
   */
  public static <S> Set<Pebble<S>> universe(S state) {
    return Set.of(Pebble.of(state, false), Pebble.of(state, true));
  }

  /**
   * Computes the set of all possible pebbles for an automaton.
   *
   * @param aut Automaton the pebbles should be on.
   * @param <S> Type of state of the automaton.
   * @return A set of all possible pebbles on all states with all possible flag values.
   */
  public static <S> Set<Pebble<S>> universe(Automaton<S, BuchiAcceptance> aut) {
    return aut.states()
      .stream()
      .map(Pebble::universe)
      .flatMap(Collection::stream)
      .collect(Collectors.toSet());
  }

  public abstract S state();

  public abstract boolean flag();

  /**
   * Sets the flag of a pebble.
   *
   * @param b Value the flag should be set to.
   * @return A pebble on the same state with its flag set to the argument.
   */
  public Pebble<S> withFlag(boolean b) {
    return Pebble.of(state(), b);
  }

  @Override
  public String toString() {
    return (flag() ? "T" : "F") + state();
  }

  /**
   * Computes the set of successor pebbles for a set of valuations.
   *
   * @param aut    Automaton the pebble is placed on.
   * @param valSet Set of valuations to advance the pebble by.
   * @return A set of possible successor pebbles for the given valuation set.
   */
  public Set<Pebble<S>> successors(Automaton<S, ? extends BuchiAcceptance> aut, BddSet valSet) {
    Set<Pebble<S>> out = new HashSet<>();
    valSet.iterator(aut.atomicPropositions().size()).forEachRemaining(
      val -> out.addAll(successors(aut, val)));
    return out;
  }

  public Set<Pebble<S>> predecessors(Automaton<S, ? extends BuchiAcceptance> aut, BddSet valSet) {
    Set<Pebble<S>> out = new HashSet<>();
    valSet.iterator(aut.atomicPropositions().size()).forEachRemaining(
      val -> out.addAll(predecessors(aut, val)));
    return out;
  }

  /**
   * Computes set of successor pebbles for a single valuation.
   *
   * @param aut Automaton to advance the pebble in.
   * @param val Valuation to advance the pebble by.
   * @return Set of possible successor pebbles.
   */
  public Set<Pebble<S>> successors(Automaton<S, ? extends BuchiAcceptance> aut, BitSet val) {
    return aut.edges(state(), val)
      .stream()
      .map(s -> Pebble.of(s.successor(), aut.acceptance().isAcceptingEdge(s) || flag()))
      .collect(Collectors.toSet());
  }

  public Set<Pebble<S>> predecessors(Automaton<S, ? extends BuchiAcceptance> aut, BitSet val) {
    return aut.states()
      .stream()
      // we are only interested in states that are predecessors
      .filter(s -> aut.successors(s, val).contains(state()))
      .map(s -> Pebble.of(s, aut.acceptance().isAcceptingEdge(aut
        .edges(s, val)
        .stream()
        .filter(e -> e.successor().equals(state()))
        .findFirst()
        .orElseThrow())
      )).collect(Collectors.toSet());
  }
}
