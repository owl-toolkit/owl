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

package owl.arena.algorithms;

import static owl.arena.Arena.Owner.PLAYER_1;
import static owl.arena.Arena.Owner.PLAYER_2;

import com.google.common.collect.Sets;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import owl.arena.Arena;
import owl.arena.Views;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.edge.Edge;
import owl.run.Transformer;
import owl.run.Transformers;

public final class ParityGameSolver {

  // TODO: should be returning a winning region or strategy
  public static final Transformer zielonkaSolver =
    Transformers.fromFunction(Arena.class, x -> {
      WinningRegions winning = recursiveZielonka(x);
      System.out.println("Is the initial state winning? "
        + winning.player2.contains(x.getInitialState()));
      return x;
    });

  private ParityGameSolver() {}

  // The convention here is that player 2 wants to satisfy the parity condition
  // that is, get a minimal colour appearing infinitely often to be accepting.
  // Also, player 1 chooses actions BEFORE player 2 does
  private static <S> WinningRegions<S> recursiveZielonka(Arena<S, ParityAcceptance> arena) {
    ParityAcceptance acceptance = arena.getAcceptance();

    // get the minimal colour in the arena
    int minimalColour = acceptance.getAcceptanceSets();

    for (S state : arena.getStates()) {
      for (Edge<S> edge : arena.getEdges(state)) {
        minimalColour = Math.min(minimalColour, edge.smallestAcceptanceSet());
      }
    }

    // if the min did not change, we have a winner
    Arena.Owner ourHorse = acceptance.isAccepting(minimalColour) ? PLAYER_2 : PLAYER_1;

    if (minimalColour == acceptance.getAcceptanceSets()) {
      return new WinningRegions<>(arena.getStates(), ourHorse);
    }

    // lets get the set of all target states, this will depend on
    // whether the minimal colour is winning for player 1 and on
    // which states have one (or all) successors of the minimal
    // colour
    int finMinColour = minimalColour;
    Predicate<Edge<S>> hasMinCol = y -> y.smallestAcceptanceSet() == finMinColour;

    Set<S> winningStates = Sets.filter(arena.getStates(), x -> {
      if (arena.getOwner(x) != PLAYER_2) {
        return false;
      }

      if (PLAYER_2 == ourHorse) {
        return arena.getEdges(x).stream().anyMatch(hasMinCol);
      } else {
        return arena.getEdges(x).stream().allMatch(hasMinCol);
      }
    });

    // NOTE: winningStates may be empty! this is because it is actually
    // the second layer of the attractor fixpoint, with the coloured edges
    // being the first layer
    assert winningStates.stream().allMatch(x -> arena.getOwner(x) == PLAYER_2);

    // we now compute the attractor of the winning states and get a filtered
    // arena without the attractor states
    Set<S> losing = Sets.difference(arena.getStates(),
      arena.getAttractorFixpoint(winningStates, ourHorse));

    Arena<S, ParityAcceptance> subarena = Views.filter(arena, losing, hasMinCol.negate());
    WinningRegions<S> subWinning = recursiveZielonka(subarena);

    // if in the subgame our horse wins everywhere, then he's the winner
    if (subWinning.winningRegion(ourHorse).containsAll(subarena.getStates())) {
      return new WinningRegions<>(arena.getStates(), ourHorse);
    }

    // otherwise, we have to test a different subgame
    Set<S> opponentAttr = arena.getAttractorFixpoint(
      subWinning.winningRegion(ourHorse.flip()), ourHorse.flip());

    losing = Sets.difference(arena.getStates(), opponentAttr);
    subWinning = recursiveZielonka(Views.filter(arena, losing));
    subWinning.addAll(opponentAttr, ourHorse.flip());

    return subWinning;
  }

  public static <S> boolean zielonkaRealizability(Arena<S, ParityAcceptance> arena) {
    return recursiveZielonka(arena).player2.contains(arena.getInitialState());
  }

  private static final class WinningRegions<S> {
    private final Set<S> player1;
    private final Set<S> player2;

    WinningRegions(Set<S> s, Arena.Owner o) {
      if (PLAYER_1 == o) {
        this.player1 = new HashSet<>(s);
        this.player2 = new HashSet<>();
      } else {
        this.player1 = new HashSet<>();
        this.player2 = new HashSet<>(s);
      }
    }

    void addAll(Set<S> s, Arena.Owner o) {
      if (PLAYER_1 == o) {
        this.player1.addAll(s);
      } else {
        this.player2.addAll(s);
      }
    }

    Set<S> winningRegion(Arena.Owner o) {
      return PLAYER_1 == o ? this.player1 : this.player2;
    }
  }
}
