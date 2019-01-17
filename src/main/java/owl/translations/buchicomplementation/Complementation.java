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

package owl.translations.buchicomplementation;

import com.google.common.collect.Maps;
import com.google.common.primitives.ImmutableIntArray;
import de.tum.in.naturals.set.NatCartesianProductSet;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.immutables.value.Value;
import owl.automaton.Automaton;
import owl.automaton.ImplicitNonDeterministicEdgesAutomaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.Collections3;
import owl.util.annotation.HashedTuple;

public final class Complementation {

  private Complementation() {}

  public static <S> Automaton<LevelRankingState<S>, BuchiAcceptance> complement(
    Automaton<S, BuchiAcceptance> automaton) {
    return new ImplicitNonDeterministicEdgesAutomaton<>(
      automaton.factory(),
      Set.of(initialState(automaton)),
      BuchiAcceptance.INSTANCE,
    (LevelRankingState<S> state, BitSet letter) -> {
      Set<LevelRankingState<S>> successors = successors(automaton, state, letter);
      return isAcceptingState(state)
        ? Collections3.transformSet(successors, z -> Edge.of(z, 0))
        : Collections3.transformSet(successors, Edge::of);
    });
  }

  private static <S> LevelRankingState<S> initialState(Automaton<S, BuchiAcceptance> automaton) {
    int n = automaton.states().size();
    return LevelRankingState.create(
      Maps.asMap(automaton.initialStates(), x -> 2 * n), automaton.initialStates());
  }

  private static <S> Set<LevelRankingState<S>> successors(Automaton<S, BuchiAcceptance> automaton,
    LevelRankingState<S> levelRankingState,
    BitSet letter) {
    List<S> stateOrder = new ArrayList<>();
    List<Integer> largestRanking = new ArrayList<>();

    // Compute upper bounds for ranking.
    for (Map.Entry<S, Integer> stateRank : levelRankingState.levelRanking().entrySet()) {
      Set<S> successors = automaton.successors(stateRank.getKey(), letter);

      for (S successor : successors) {
        int index = stateOrder.indexOf(successor);

        if (index >= 0) {
          int rank = Math.min(stateRank.getValue(), largestRanking.get(index));
          largestRanking.set(index, rank);
        } else {
          stateOrder.add(successor);
          largestRanking.add(stateRank.getValue());
        }
      }
    }

    Set<LevelRankingState<S>> successors = new HashSet<>();

    // Iterate over all rankings.
    outerloop:
    for (int[] ranking : new NatCartesianProductSet(
      ImmutableIntArray.copyOf(largestRanking).toArray())) {

      Map<S, Integer> successorLevelRanking = new HashMap<>();

      // Check if accepting states are even and build map.
      for (int i = 0; i < ranking.length; i++) {
        int rank = ranking[i];
        S successor = stateOrder.get(i);

        if (rank % 2 == 1 && isAcceptingState(automaton, successor)) {
          continue outerloop;
        }

        successorLevelRanking.put(successor, rank);
      }

      // Update owing states.

      Set<S> successorOwingStates = new HashSet<>();

      if (levelRankingState.owingStates().isEmpty()) {
        successorLevelRanking.forEach((state, rank) -> {
          if (rank % 2 == 0) {
            successorOwingStates.add(state);
          }
        });
      } else {
        levelRankingState.owingStates().forEach((state) -> {
          automaton.successors(state, letter).forEach((successor) -> {
            if (successorLevelRanking.get(successor) % 2 == 0) {
              successorOwingStates.add(successor);
            }
          });
        });
      }

      successors.add(LevelRankingState.create(successorLevelRanking, successorOwingStates));
    }

    return successors;
  }

  private static <S> boolean isAcceptingState(LevelRankingState<S> state) {
    return state.owingStates().isEmpty();
  }

  @Value.Immutable
  @HashedTuple
  public abstract static class LevelRankingState<S> {
    abstract Map<S, Integer> levelRanking();

    abstract Set<S> owingStates();

    static <S> LevelRankingState<S> create(Map<S, Integer> levelRanking, Set<S> owingStates) {
      return LevelRankingStateTuple.create(levelRanking, owingStates);
    }
  }

  // Automaton<?,?> is defined in terms of transition-acceptance. We simulate this by marking all
  // outgoing transitions of an accepting state accepting. This utility method checks this.
  private static <S> boolean isAcceptingState(Automaton<S, BuchiAcceptance> automaton, S state) {
    var edges = automaton.edges(state);

    if (edges.stream().allMatch(Edge::hasAcceptanceSets)) {
      return true;
    }

    if (edges.stream().noneMatch(Edge::hasAcceptanceSets)) {
      return false;
    }

    throw new IllegalArgumentException("Not state-acceptance");
  }
}