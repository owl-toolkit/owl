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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.google.common.collect.Sets;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.acceptance.ParityAcceptance;
import owl.game.Game;
import owl.game.GameViews;
import owl.game.GameViews.Node;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.run.DefaultEnvironment;
import owl.translations.ltl2dpa.LTL2DPAFunction;
import owl.translations.ltl2dpa.LTL2DPAFunction.Configuration;

public class ParityGameSolverTest {
  private static final LTL2DPAFunction TRANSLATION = new LTL2DPAFunction(
    DefaultEnvironment.annotated(),
    Sets.union(LTL2DPAFunction.RECOMMENDED_ASYMMETRIC_CONFIG, Set.of(Configuration.COMPLETE)));

  @Test
  public void ltl2zielonkaTest1() {
    LabelledFormula formula = LtlParser.parse("F (a <-> X b)");

    Automaton<Object, ParityAcceptance> automaton = AutomatonUtil.cast(
      TRANSLATION.apply(formula), Object.class, ParityAcceptance.class);

    Game<Node<Object>, ParityAcceptance> game =
      GameViews.split(automaton, List.of("a"));

    assertThat(ParityGameSolver.zielonkaRealizability(game), equalTo(true));
  }

  @Test
  public void ltl2zielonkaTest2() {
    LabelledFormula formula =
      LtlParser.parse("((((G (F (r_0))) && (G (F (r_1)))) <-> "
                      + "(G (F (g)))) && (G ((((r_0) && (r_1)) -> "
                      + "(G (! (g)))) && (true))))");
    Automaton<Object, ParityAcceptance> automaton = AutomatonUtil.cast(
      TRANSLATION.apply(formula), Object.class, ParityAcceptance.class);

    Game<Node<Object>, ParityAcceptance> game =
      GameViews.split(automaton, List.of("r_0", "r_1"));

    assertThat(ParityGameSolver.zielonkaRealizability(game), equalTo(false));
  }

  @Test
  public void ltl2zielonkaTest3() {
    LabelledFormula formula =
      LtlParser.parse("(G ((((req) -> (X ((grant) && (X ((grant) "
                      + "&& (X (grant))))))) && ((grant) -> "
                      + "(X (! (grant))))) && ((cancel) -> "
                      + "(X ((! (grant)) U (go))))))");
    Automaton<Object, ParityAcceptance> automaton = AutomatonUtil.cast(
      TRANSLATION.apply(formula), Object.class, ParityAcceptance.class);

    Game<Node<Object>, ParityAcceptance> game =
      GameViews.split(automaton, List.of("go", "cancel", "req"));

    assertThat(ParityGameSolver.zielonkaRealizability(game), equalTo(false));
  }

  @Test
  public void ltl2zielonkaTest4() {
    LabelledFormula formula =
      LtlParser.parse("(((G (F (r_0))) && (G (F (r_1)))) <-> (G (F (g))))");
    Automaton<Object, ParityAcceptance> automaton = AutomatonUtil.cast(
      TRANSLATION.apply(formula), Object.class, ParityAcceptance.class);

    Game<Node<Object>, ParityAcceptance> game =
      GameViews.split(automaton, List.of("r_0", "r_1"));

    assertThat(ParityGameSolver.zielonkaRealizability(game), equalTo(true));
  }
}
