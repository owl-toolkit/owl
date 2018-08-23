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

package owl.game.algorithms;

import static owl.game.Game.Owner.ENVIRONMENT;
import static owl.game.Game.Owner.SYSTEM;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.edge.Edge;
import owl.game.Game;
import owl.game.GameViews;
import owl.run.modules.Transformer;
import owl.run.modules.Transformers;

public final class ZielonkaSolver {
  // TODO: should be returning a winning region or strategy
  public static final Transformer ZIELONKA_SOLVER =
    Transformers.fromFunction(Game.class, x -> {
      WinningRegions<?> winning = recursiveZielonka(x);

      return winning.player2.contains(x.automaton().onlyInitialState())
        ? "The specification is REALISABLE"
        : "The specification is UNREALISABLE";
    });

  private ZielonkaSolver() {}

  public static <S> WinningRegions<S> solveBuchi(Game<S, BuchiAcceptance> game) {
    return recursiveZielonka(GameViews.viewAs(game, ParityAcceptance.class));
  }

  public static <S> WinningRegions<S> solveRabinPair(Game<S, RabinAcceptance> game) {
    Preconditions.checkArgument(game.automaton().acceptance().pairs().size() == 1);
    return recursiveZielonka(GameViews.viewAs(game, ParityAcceptance.class));
  }

  // The convention here is that player 2 wants to satisfy the parity condition
  // that is, get a minimal colour appearing infinitely often to be accepting.
  // Also, player 1 chooses actions BEFORE player 2 does
  private static <S> WinningRegions<S> recursiveZielonka(Game<S, ParityAcceptance> game) {
    var automaton = game.automaton();
    Set<S> states = automaton.states();
    ParityAcceptance acceptance = automaton.acceptance();

    // get the minimal colour in the game
    AtomicInteger minimalColour = new AtomicInteger(acceptance.acceptanceSets());

    for (S state : states) {
      automaton.edges(state).forEach(edge ->
          minimalColour.getAndUpdate(c -> Math.min(c, edge.smallestAcceptanceSet())));
    }

    int theMinimalColour = minimalColour.get();

    // if the min did not change, we have a winner
    Game.Owner ourHorse = acceptance.isAccepting(theMinimalColour) ? SYSTEM : ENVIRONMENT;

    if (theMinimalColour == acceptance.acceptanceSets()) {
      return new WinningRegions<>(states, ourHorse);
    }

    // lets get the set of all target states, this will depend on
    // whether the minimal colour is winning for player 1 and on
    // which states have one (or all) successors of the minimal
    // colour
    Predicate<Edge<S>> hasMinCol = y -> y.smallestAcceptanceSet() == theMinimalColour;

    Set<S> winningStates = Sets.filter(states, x -> {
      if (game.owner(x) != SYSTEM) {
        return false;
      }

      if (SYSTEM == ourHorse) {
        return automaton.edges(x).stream().anyMatch(hasMinCol);
      }

      return automaton.edges(x).stream().allMatch(hasMinCol);
    });

    // we now compute the attractor of the winning states and get a filtered
    // game without the attractor states
    Set<S> losingSet = Sets.difference(states, AttractorSolver
      .compute(automaton, winningStates, ourHorse == ENVIRONMENT, game.variables(ourHorse)));

    var subGame = GameViews.filter(game, losingSet, hasMinCol.negate());
    WinningRegions<S> subWinning = recursiveZielonka(subGame);

    // if in the sub-game our horse wins everywhere, then he's the winner
    if (subWinning.winningRegion(ourHorse).containsAll(subGame.automaton().states())) {
      return new WinningRegions<>(states, ourHorse);
    }

    // otherwise, we have to test a different subgame
    Set<S> opponentAttractor = AttractorSolver
      .compute(automaton, subWinning.winningRegion(ourHorse.opponent()), ENVIRONMENT == ourHorse.opponent(),
        game.variables(ourHorse.opponent()));

    Set<S> difference = Sets.difference(states, opponentAttractor);
    WinningRegions<S> newSubWinning = recursiveZielonka(GameViews.filter(game, difference));
    newSubWinning.addAll(opponentAttractor, ourHorse.opponent());

    return newSubWinning;
  }

  public static <S> boolean zielonkaRealizability(Game<S, ParityAcceptance> game) {
    var game2 = GameViews.replaceInitialStates(game, game.automaton().states());
    return recursiveZielonka(game2).player2.contains(game.automaton().onlyInitialState());
  }
}
