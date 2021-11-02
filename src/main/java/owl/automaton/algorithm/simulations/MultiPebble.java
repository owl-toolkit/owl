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
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import owl.automaton.Automaton;
import owl.automaton.acceptance.BuchiAcceptance;

/**
 * Abstracts multiple pebbles controlled by Duplicator in a multipebble simulation game.
 *
 * @param <S> The type of state of the underlying automaton.
 */
@AutoValue
public abstract class MultiPebble<S> {
  /**
   * Construction method for a multipebble.
   *
   * @param pebbles     A list of pebbles that forms the multipebble.
   * @param pebbleCount The maximum allowed number of pebbles.
   * @param <S>         The type of state of the underlying automaton.
   * @return Returns a multipebble consisting of the given pebbles with the given limit size.
   */
  static <S> MultiPebble<S> of(List<Pebble<S>> pebbles, int pebbleCount) {
    return new AutoValue_MultiPebble<>(pebbleCount, pebbles);
  }

  static <S> MultiPebble<S> of(S state, boolean flag, int pebbleCount) {
    return new AutoValue_MultiPebble<>(pebbleCount, List.of(Pebble.of(state, flag)));
  }

  private static <S> List<List<Pebble<S>>> kMultiplex(Set<Pebble<S>> succ, int k) {
    // Use google implementation of cartesian product to compute all possible combinations of
    // pebbles taken from succ with size at most k
    List<List<Pebble<S>>> basis = new ArrayList<>();
    for (int i = 1; i <= k; i++) {
      basis.add(List.copyOf(succ));
    }
    return Lists.cartesianProduct(basis);
  }

  /**
   * Builds all possible multi pebbles for the given input arguments.
   *
   * @param possibleValues The state space of the underlying automaton.
   * @param k              The maximum size of each multipebble.
   * @param <S>            The type of state of the underlying automaton.
   * @return The set of all possible k-pebbles for the given state space.
   */
  public static <S> Set<MultiPebble<S>> universe(Set<S> possibleValues, int k) {
    var pV = possibleValues
      .stream()
      .map(Pebble::universe)
      .flatMap(Collection::stream)
      .collect(Collectors.toSet());

    // todo: test and decide if this is the better version
    return kMultiplex(pV, k)
      .stream()
      .map(peb -> MultiPebble.of(peb, k))
      .collect(Collectors.toSet());
  }

  public static <S> Set<MultiPebble<S>> universe(Automaton<S, BuchiAcceptance> aut, int k) {
    return universe(aut.states(), k);
  }

  public abstract int size();

  public abstract List<Pebble<S>> pebbles();

  /**
   * Sets the final flag of all contained subpebbles.
   *
   * @param b The value to set all flags to.
   * @return A multipebble where all subpebbles have flag set to b.
   */
  public MultiPebble<S> setFlag(boolean b) {
    return MultiPebble.of(
      pebbles()
        .stream()
        .map(p -> p.withFlag(b))
        .toList(),
      size()
    );
  }

  @Override
  public String toString() {
    return pebbles().toString();
  }

  /**
   * Computes the 'combined' flag of a multipebble.
   *
   * @return true if all subpebbles have their flag set to true
   */
  public boolean flag() {
    return pebbles()
      .stream()
      .allMatch(Pebble::flag);
  }

  /**
   * Computes the set of possible successor multipebbles for a given valuation and automaton.
   *
   * @param aut Automaton to use as basis.
   * @param val One valuation along which the multipebble should be advanced by.
   * @return Successor multipebble for the given valuation.
   */
  public Set<MultiPebble<S>> successors(Automaton<S, ? extends BuchiAcceptance> aut, BitSet val) {
    // first we collect the set of possible successors for each of the contained pebbles
    Set<Pebble<S>> successors = pebbles()
      .stream()
      .map(p -> p.successors(aut, val))
      .flatMap(Collection::stream)
      .collect(Collectors.toSet());

    // then a helper function is used to compute all possible k-combinations
    return kMultiplex(successors, size())
      .stream()
      .map(peb -> MultiPebble.of(peb, size()))
      .collect(Collectors.toSet());
  }

  public Set<MultiPebble<S>> predecessors(Automaton<S, ? extends BuchiAcceptance> aut, BitSet val) {
    Set<Pebble<S>> predecessors = pebbles()
      .parallelStream()
      .flatMap(p -> p.predecessors(aut, val).stream())
      .collect(Collectors.toSet());

    return kMultiplex(predecessors, size())
      .stream()
      .map(peb -> MultiPebble.of(peb, size()))
      .collect(Collectors.toSet());
  }

  /**
   * Counts how many actual pebbles are contained.
   *
   * @return The actual number of pebbles contained, this is different from size(), which returns
   *         the maximum size of a multipebble and its successors
   */
  public int count() {
    return pebbles().size();
  }

  /**
   * Utility function that just grabs the only contained state.
   *
   * @return The first state contained, useful for singleton multipebbles.
   */
  public S onlyState() {
    return pebbles().iterator().next().state();
  }

  public boolean isSingleton() {
    return Set.of(pebbles().stream().map(Pebble::state)).size() == 1;
  }
}
