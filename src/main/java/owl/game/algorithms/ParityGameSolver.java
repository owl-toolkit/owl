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

import java.util.HashSet;
import java.util.Set;
import owl.automaton.acceptance.ParityAcceptance;
import owl.game.Game;

public interface ParityGameSolver {
  <S> boolean realizable(Game<S, ? extends ParityAcceptance> game);

  <S> WinningRegions<S> solve(Game<S, ? extends ParityAcceptance> game);

  final class WinningRegions<S> {
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

    public Set<S> playerEven() {
      return this.player2;
    }

    public Set<S> playerOdd() {
      return this.player1;
    }

    public static <S> WinningRegions<S> of(Set<S> region, Game.Owner owner) {
      return new WinningRegions<>(region, owner);
    }
  }
}