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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static owl.automaton.AutomatonUtil.cast;
import static owl.game.algorithms.AttractorSolver.solveSafety;

import com.google.common.collect.Sets;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.game.GameViews;
import owl.ltl.parser.LtlParser;
import owl.run.DefaultEnvironment;
import owl.translations.LTL2DAFunction;
import owl.translations.ltl2dpa.LTL2DPAFunction;
import owl.translations.ltl2dpa.LTL2DPAFunction.Configuration;

public class AttractorSolverTest {
  private static final LTL2DAFunction ltl2da = new LTL2DAFunction(DefaultEnvironment.annotated(),
    true, EnumSet.of(LTL2DAFunction.Constructions.SAFETY));

  @Test
  public void attractorTest() {
    var formula = LtlParser.parse("G (a <-> X b)");
    var automaton = cast(ltl2da.apply(formula), Object.class, AllAcceptance.class);
    assertTrue(
      solveSafety(automaton, List.of("a")).contains(automaton.onlyInitialState()));
    assertFalse(
      solveSafety(automaton, List.of("b")).contains(automaton.onlyInitialState()));
  }

  @Test
  public void attractorTest2() {
    var formula = LtlParser.parse("a W b | G c");
    var automaton = cast(ltl2da.apply(formula), Object.class, AllAcceptance.class);
    assertTrue(
      solveSafety(automaton, List.of()).contains(automaton.onlyInitialState()));
    assertTrue(
      solveSafety(automaton, List.of("a")).contains(automaton.onlyInitialState()));
    assertTrue(
      solveSafety(automaton, List.of("c")).contains(automaton.onlyInitialState()));
    assertTrue(
      solveSafety(automaton, List.of("a", "b")).contains(automaton.onlyInitialState()));
    assertTrue(
      solveSafety(automaton, List.of("b", "c")).contains(automaton.onlyInitialState()));
    assertFalse(
      solveSafety(automaton, List.of("a", "b", "c")).contains(automaton.onlyInitialState()));
  }
}
