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

package owl.game.algorithms;

import static owl.game.Game.Owner.PLAYER_1;
import static owl.game.Game.Owner.PLAYER_2;

import com.google.common.collect.Sets;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.edge.Edge;
import owl.game.Game;
import owl.game.GameViews;
import owl.run.modules.Transformer;
import owl.run.modules.Transformers;

public final class ParityGameSolver {
  // TODO: should be returning a winning region or strategy
  public static final Transformer ZIELONKA_SOLVER =
    Transformers.fromFunction(Game.class, x -> {
      WinningRegions<?> winning = recursiveZielonka(x);

      return winning.player2.contains(x.getInitialState())
        ? "The specification is REALISABLE"
        : "The specification is UNREALISABLE";
    });

  private ParityGameSolver() {}

  // The convention here is that player 2 wants to satisfy the parity condition
  // that is, get a minimal colour appearing infinitely often to be accepting.
  // Also, player 1 chooses actions BEFORE player 2 does
  private static <S> WinningRegions<S> recursiveZielonka(Game<S, ParityAcceptance> game) {
    ParityAcceptance acceptance = game.getAcceptance();

    // get the minimal colour in the game
    int minimalColour = acceptance.getAcceptanceSets();

    for (S state : game.getStates()) {
      for (Edge<S> edge : game.getEdges(state)) {
        minimalColour = Math.min(minimalColour, edge.smallestAcceptanceSet());
      }
    }

    // if the min did not change, we have a winner
    Game.Owner ourHorse = acceptance.isAccepting(minimalColour) ? PLAYER_2 : PLAYER_1;

    if (minimalColour == acceptance.getAcceptanceSets()) {
      return new WinningRegions<>(game.getStates(), ourHorse);
    }

    // lets get the set of all target states, this will depend on
    // whether the minimal colour is winning for player 1 and on
    // which states have one (or all) successors of the minimal
    // colour
    int finMinColour = minimalColour;
    Predicate<Edge<S>> hasMinCol = y -> y.smallestAcceptanceSet() == finMinColour;

    Set<S> winningStates = Sets.filter(game.getStates(), x -> {
      if (game.getOwner(x) != PLAYER_2) {
        return false;
      }

      if (PLAYER_2 == ourHorse) {
        return game.getEdges(x).stream().anyMatch(hasMinCol);
      } else {
        return game.getEdges(x).stream().allMatch(hasMinCol);
      }
    });

    // NOTE: winningStates may be empty! this is because it is actually
    // the second layer of the attractor fixpoint, with the coloured edges
    // being the first layer
    assert winningStates.stream().allMatch(x -> game.getOwner(x) == PLAYER_2);

    // we now compute the attractor of the winning states and get a filtered
    // game without the attractor states
    Set<S> losingSet = Sets.difference(game.getStates(),
      game.getAttractorFixpoint(winningStates, ourHorse));

    Game<S, ParityAcceptance> subGame = GameViews.filter(game, losingSet, hasMinCol.negate());
    WinningRegions<S> subWinning = recursiveZielonka(subGame);

    // if in the subgame our horse wins everywhere, then he's the winner
    if (subWinning.winningRegion(ourHorse).containsAll(subGame.getStates())) {
      return new WinningRegions<>(game.getStates(), ourHorse);
    }

    // otherwise, we have to test a different subgame
    Set<S> opponentAttractor =
      game.getAttractorFixpoint(subWinning.winningRegion(ourHorse.opponent()), ourHorse.opponent());

    Set<S> difference = Sets.difference(game.getStates(), opponentAttractor);
    WinningRegions<S> newSubWinning = recursiveZielonka(GameViews.filter(game, difference));
    newSubWinning.addAll(opponentAttractor, ourHorse.opponent());

    return newSubWinning;
  }

  public static <S> boolean zielonkaRealizability(Game<S, ParityAcceptance> game) {
    return recursiveZielonka(game).player2.contains(game.getInitialState());
  }

  private static final class WinningRegions<S> {
    final Set<S> player1;
    final Set<S> player2;

    WinningRegions(Set<S> s, Game.Owner o) {
      if (PLAYER_1 == o) {
        this.player1 = new HashSet<>(s);
        this.player2 = new HashSet<>();
      } else {
        this.player1 = new HashSet<>();
        this.player2 = new HashSet<>(s);
      }
    }

    void addAll(Set<S> s, Game.Owner o) {
      if (PLAYER_1 == o) {
        this.player1.addAll(s);
      } else {
        this.player2.addAll(s);
      }
    }

    Set<S> winningRegion(Game.Owner o) {
      return PLAYER_1 == o ? this.player1 : this.player2;
    }
  }
}
