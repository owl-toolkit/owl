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

package owl.game;

import static owl.util.Assertions.assertThat;

import com.google.common.collect.Sets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.util.AnnotatedState;
import owl.game.Game.Owner;
import owl.game.GameViews.Node;
import owl.game.algorithms.AttractorSolver;
import owl.ltl.EquivalenceClass;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.run.DefaultEnvironment;
import owl.translations.ltl2dpa.LTL2DPAFunction;
import owl.translations.ltl2dpa.LTL2DPAFunction.Configuration;

class GameFactoryTest {
  private static final LTL2DPAFunction TRANSLATION = new LTL2DPAFunction(
    DefaultEnvironment.annotated(),
    Sets.union(LTL2DPAFunction.RECOMMENDED_ASYMMETRIC_CONFIG, Set.of(Configuration.COMPLETE)));

  @Test
  public void testAttractor() {
    LabelledFormula formula = LtlParser.parse("F (a <-> X b)");

    Automaton<AnnotatedState, ParityAcceptance> automaton = AutomatonUtil.cast(
      TRANSLATION.apply(formula), AnnotatedState.class, ParityAcceptance.class);

    Game<AnnotatedState, ParityAcceptance> game = Game.of(automaton, List.of("a"));

    Set<AnnotatedState> winningStates = game.automaton().states().stream().filter(
      x -> ((AnnotatedState<EquivalenceClass>) x.state()).state().isTrue()).collect(Collectors.toSet());
    assertThat(winningStates, x -> !x.isEmpty());

    // Player 2 can win by matching the action of Player 1 one step delayed.
    assertThat(AttractorSolver.compute(game.automaton(), winningStates, false, game.variables(Owner.SYSTEM)),
      x -> x.contains(game.onlyInitialState()));

    // Player 1 can never win...
    assertThat(AttractorSolver.compute(game.automaton(), winningStates, true, game.variables(Owner.ENVIRONMENT)),
      x -> !x.contains(game.onlyInitialState()));
  }
}
