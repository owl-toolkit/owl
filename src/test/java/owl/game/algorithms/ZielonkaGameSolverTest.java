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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import owl.game.GameFactoryTest;
import owl.game.GameViews;
import owl.ltl.parser.LtlParser;

class ZielonkaGameSolverTest {

  @Test
  void ltl2zielonkaTest1() {
    var formula = LtlParser.parse("F (a <-> X b)");
    var automaton = GameFactoryTest.translate(formula);
    var game = GameViews.split(automaton, List.of("a"));
    assertTrue(ZielonkaGameSolver.zielonkaRealizability(game));
  }

  @Test
  void ltl2zielonkaTest2() {
    var formula = LtlParser.parse("((((G (F (r_0))) && (G (F (r_1)))) <-> "
        + "(G (F (g)))) && (G ((((r_0) && (r_1)) -> "
        + "(G (! (g)))) && (true))))");
    var automaton = GameFactoryTest.translate(formula);
    var game = GameViews.split(automaton, List.of("r_0", "r_1"));
    assertFalse(ZielonkaGameSolver.zielonkaRealizability(game));
  }

  @Test
  void ltl2zielonkaTest3() {
    var formula = LtlParser.parse("(G ((((req) -> (X ((grant) && (X ((grant) "
                      + "&& (X (grant))))))) && ((grant) -> "
                      + "(X (! (grant))))) && ((cancel) -> "
                      + "(X ((! (grant)) U (go))))))");
    var automaton = GameFactoryTest.translate(formula);
    var game = GameViews.split(automaton, List.of("go", "cancel", "req"));
    assertFalse(ZielonkaGameSolver.zielonkaRealizability(game));
  }

  @Test
  void ltl2zielonkaTest4() {
    var formula = LtlParser.parse("(((G (F (r_0))) && (G (F (r_1)))) <-> (G (F (g))))");
    var automaton = GameFactoryTest.translate(formula);
    var game = GameViews.split(automaton, List.of("r_0", "r_1"));
    assertTrue(ZielonkaGameSolver.zielonkaRealizability(game));
  }
}
