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


import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static owl.automaton.AutomatonUtil.cast;

import com.google.common.collect.Sets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import owl.automaton.Automaton;
import owl.automaton.acceptance.ParityAcceptance;
import owl.game.Game;
import owl.ltl.parser.LtlParser;
import owl.run.DefaultEnvironment;
import owl.translations.ltl2dpa.LTL2DPAFunction;
import owl.translations.ltl2dpa.LTL2DPAFunction.Configuration;

class ZielonkaSolverTest {
  private static final LTL2DPAFunction ltl2dpa = new LTL2DPAFunction(DefaultEnvironment.annotated(),
    Sets.union(LTL2DPAFunction.RECOMMENDED_ASYMMETRIC_CONFIG, Set.of(Configuration.COMPLETE)));

  @Test
  void ltl2zielonkaTest1() {
    var formula = LtlParser.parse("F (a <-> X b)");
    var automaton = cast(ltl2dpa.apply(formula), Object.class, ParityAcceptance.class);
    var game = Game.of(automaton, List.of("a"));

    assertTrue(ZielonkaSolver.zielonkaRealizability(game));
  }

  @Test
  void ltl2zielonkaTest2() {
    var formula = LtlParser.parse("((((GF r_0) && (GF r_1)) <-> (GF g)) "
      + "&& (G (((r_0 && r_1) -> (G !g)))))");
    var automaton = cast(ltl2dpa.apply(formula), Object.class, ParityAcceptance.class);
    var game = Game.of(automaton, List.of("r_0", "r_1"));

    assertFalse(ZielonkaSolver.zielonkaRealizability(game));
  }

  @Test
  void ltl2zielonkaTest3() {
    var formula = LtlParser.parse("(G ((((req) -> (X ((grant) && (X ((grant) "
                      + "&& (X (grant))))))) && ((grant) -> "
                      + "(X (! (grant))))) && ((cancel) -> "
                      + "(X ((! (grant)) U (go))))))", List.of("go", "cancel", "req", "grant"));
    var automaton = cast(ltl2dpa.apply(formula), Object.class, ParityAcceptance.class);
    var game = Game.of(automaton, List.of("go", "cancel", "req"));

    assertFalse(ZielonkaSolver.zielonkaRealizability(game));
  }

  @Test
  void ltl2zielonkaTest4() {
    var formula = LtlParser.parse("(((G (F (r_0))) && (G (F (r_1)))) <-> (G (F (g))))");
    var automaton = cast(ltl2dpa.apply(formula), Object.class, ParityAcceptance.class);
    var game = Game.of(automaton, List.of("r_0", "r_1"));

    assertTrue(ZielonkaSolver.zielonkaRealizability(game));
  }
}
