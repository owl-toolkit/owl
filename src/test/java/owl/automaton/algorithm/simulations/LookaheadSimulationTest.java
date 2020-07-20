/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
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

import static owl.automaton.algorithm.simulations.CommonAutomata.buildAutomatonOne;

import java.util.Set;
import org.junit.jupiter.api.Test;
import owl.game.algorithms.OinkGameSolver;

public class LookaheadSimulationTest {
  @Test
  void simpleDirectTest() {
    if (!OinkGameSolver.checkOinkExecutable()) {
      return;
    }
    var aut = buildAutomatonOne();

    var game = new SimulationGame<>(
      ForwardDirectLookaheadSimulation.of(
        aut, aut, 1, 5, 1, Set.of()
      )
    );

    assert new OinkGameSolver().solve(game)
      .playerEven()
      .contains(game.onlyInitialState());
  }
}
