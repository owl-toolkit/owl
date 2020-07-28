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

package owl.game.algorithms;

import static owl.game.Game.Owner.PLAYER_1;
import static owl.game.Game.Owner.PLAYER_2;

import com.google.common.collect.Sets;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.edge.Edge;
import owl.game.Game;
import owl.game.GameViews;

public final class ZielonkaGameSolver implements ParityGameSolver {

  // The convention here is that player 2 wants to satisfy the parity condition
  // that is, get a minimal colour appearing infinitely often to be accepting.
  // Also, player 1 chooses actions BEFORE player 2 does
  private static <S> WinningRegions<S> recursiveZielonka(Game<S, ? extends ParityAcceptance> game) {
    Set<S> states = game.states();
    ParityAcceptance acceptance = game.acceptance();
    boolean max = acceptance.parity().max();

    // get the minimal colour in the game
    int extremalColour = max ? -1 : acceptance.acceptanceSets();

    for (S state : states) {
      for (Edge<S> edge : game.edges(state)) {
        if (max) {
          extremalColour = Math.max(extremalColour, edge.colours().last().orElse(extremalColour));
        } else {
          extremalColour = Math.min(extremalColour, edge.colours().first().orElse(extremalColour));
        }
      }
    }

    int theExtremalColour = extremalColour;

    // if the extremal colour did not change, we have a winner
    Game.Owner ourHorse = acceptance.isAccepting(theExtremalColour) ? PLAYER_2 : PLAYER_1;

    if (max ? theExtremalColour == -1 : theExtremalColour == acceptance.acceptanceSets()) {
      return new WinningRegions<>(states, ourHorse);
    }

    // lets get the set of all target states, this will depend on
    // whether the minimal colour is winning for player 1 and on
    // which states have one (or all) successors of the minimal
    // colour
    Predicate<Edge<S>> hasExtremalColour = y -> max
      ? y.colours().last().orElse(-2) == theExtremalColour
      : y.colours().first().orElse(-2) == theExtremalColour;

    Set<S> winningStates = Sets.filter(states, state -> {
      Objects.requireNonNull(state);

      if (game.owner(state) != PLAYER_2) {
        return false;
      }

      if (PLAYER_2 == ourHorse) {
        return game.edges(state).stream().anyMatch(hasExtremalColour);
      }

      return game.edges(state).stream().allMatch(hasExtremalColour);
    });

    // NOTE: winningStates may be empty! this is because it is actually
    // the second layer of the attractor fixpoint, with the coloured edges
    // being the first layer
    assert winningStates.stream().allMatch(x -> game.owner(x) == PLAYER_2);

    // we now compute the attractor of the winning states and get a filtered
    // game without the attractor states
    Set<S> losingSet = Sets.difference(states,
      game.getAttractorFixpoint(winningStates, ourHorse));

    var subGame = GameViews.filter(game,
      losingSet::contains, hasExtremalColour.negate());
    WinningRegions<S> subWinning = recursiveZielonka(subGame);

    // if in the sub-game our horse wins everywhere, then he's the winner
    if (subWinning.winningRegion(ourHorse).containsAll(subGame.states())) {
      return new WinningRegions<>(states, ourHorse);
    }

    // otherwise, we have to test a different sub-game
    Set<S> opponentAttractor =
      game.getAttractorFixpoint(subWinning.winningRegion(ourHorse.opponent()), ourHorse.opponent());

    Set<S> difference = Sets.difference(states, opponentAttractor);
    WinningRegions<S> newSubWinning =
      recursiveZielonka(GameViews.filter(game, difference::contains));
    newSubWinning.addAll(opponentAttractor, ourHorse.opponent());

    return newSubWinning;
  }

  public static <S> boolean zielonkaRealizability(Game<S, ? extends ParityAcceptance> game) {
    return recursiveZielonka(GameViews.replaceInitialStates(game, game.states()))
      .player2.contains(game.initialState());
  }

  @Override
  public <S> boolean realizable(Game<S, ? extends ParityAcceptance> game) {
    return ZielonkaGameSolver.zielonkaRealizability(game);
  }

  @Override
  public <S> WinningRegions<S> solve(Game<S, ? extends ParityAcceptance> game) {
    return ZielonkaGameSolver.recursiveZielonka(game);
  }
}
