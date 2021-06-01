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

import java.util.BitSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import owl.automaton.Automaton;
import owl.automaton.acceptance.BuchiAcceptance;

public final class SimulationUtil {
  private SimulationUtil() {
  }

  /**
   * Computes all possible sets with k elements drawn from a given input set.
   *
   * @param candidates The set of possible elements in each resulting set.
   * @param k          The maximum cardinality of the output sets.
   * @param <S>        The type of element in each output set.
   * @return The set of possible k-sets.
   */
  public static <S> Set<Set<S>> possibleKSets(Set<S> candidates, int k) {
    // ensure that there actually are candidates and that k is positive
    // assert candidates.size() > 0;
    if (candidates.isEmpty()) {
      return Set.of();
    }
    assert k > 0;

    int i = 1;
    Set<Set<S>> output = new HashSet<>();

    // create first iteration
    candidates.forEach((candidate) -> {
      output.add(Set.of(candidate));
    });

    // iterate up to the maximum set size
    while (i < k) {
      Set<Set<S>> toAdd = new HashSet<>();
      output.forEach((element) -> {
        candidates.forEach((candidate) -> {
          var temp = new HashSet<>(element);
          temp.add(candidate);
          toAdd.add(temp);
        });
      });
      output.addAll(toAdd);
      i++;
    }

    return output;
  }

  /**
   * Computes all successor states in an automaton for a given valuation.
   *
   * @param aut       The automaton from which the states come
   * @param states    The origin states from which we want to obtain the successors
   * @param valuation The valuation for the transition
   * @param <S>       The type for the automaton states
   * @param <A>       The type of acceptance condition of the automaton
   * @return A set containing all automaton successors reachable with a labelled transition from a
   *         state in the given set
   */
  public static <S, A extends BuchiAcceptance> Set<S> allSuccessors(Automaton<S, A> aut,
                                                                    Set<S> states,
                                                                    BitSet valuation) {
    return states.stream()
      .map(state -> aut.successors(state, valuation))
      .flatMap(Collection::stream)
      .collect(Collectors.toSet());
  }
}
