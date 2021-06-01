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

import static owl.automaton.algorithm.simulations.CommonAutomata.buildAutomatonOne;

import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import owl.game.algorithms.OinkGameSolver;

public class LookaheadSimulationTest {
  @Test
  void simpleDirectTest() {
    var aut = buildAutomatonOne();

    var game = new SimulationGame<>(
        new ForwardDirectLookaheadSimulation<>(
            aut, aut, 1, 5, 1, Set.of())
    );

    Assumptions.assumeTrue(OinkGameSolver.checkOinkExecutable());
    Assertions.assertTrue(new OinkGameSolver().solve(game)
      .playerEven()
      .contains(game.initialState()));
  }
}
