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

import java.util.BitSet;
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

    // CODE HERE
    //
    // Useful methods:
    // * automaton.states()
    // * automaton.initialStates()

    return null;
  }

  private static <S> Set<LevelRankingState<S>> successors(Automaton<S, BuchiAcceptance> automaton,
    LevelRankingState<S> levelRankingState, BitSet letter) {

    // CODE HERE
    //
    // Useful methods:
    // * automaton.successors(state, letter)
    // * isAcceptingState(automaton, successor))
    // * LevelRankingState.create(levelRanking, owingStates));

    return null;
  }

  private static <S> boolean isAcceptingState(LevelRankingState<S> state) {
    // CODE HERE

    return false;
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

