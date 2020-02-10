/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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

import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import owl.automaton.AnnotatedState;
import owl.automaton.Automaton;
import owl.automaton.MutableAutomatonUtil;
import owl.automaton.Views;
import owl.automaton.acceptance.OmegaAcceptanceCast;
import owl.automaton.acceptance.ParityAcceptance;
import owl.game.Game.Owner;
import owl.game.GameViews.Node;
import owl.ltl.EquivalenceClass;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.run.Environment;
import owl.translations.ltl2dpa.LTL2DPAFunction;

public class GameFactoryTest {

  @Test
  void testTransform() {
    var formula = LtlParser.parse("G (a <-> X b) & G F (!a | b | c)");
    var automaton = translate(formula);
    var game = GameFactory.copyOf(GameViews.split(automaton, List.of("a", "c")));

    for (Node<Object> state : game.states()) {
      for (Node<Object> predecessor : game.predecessors(state)) {
        assertThat(state, game.successors(predecessor)::contains);
      }

      for (Node<Object> successors : game.successors(state)) {
        assertThat(state, game.predecessors(successors)::contains);
      }
    }
  }

  @Test
  void testAttractor() {
    var formula = LtlParser.parse("F (a <-> X b)");
    var automaton = translate(formula);
    var game = GameFactory.copyOf(GameViews.split(automaton, List.of("a")));

    var winningStates = game.states().stream()
      .filter(x -> {
        var state = (AnnotatedState) x.state();
        return ((EquivalenceClass) state.state()).isTrue();
      }).collect(Collectors.toSet());
    assertThat(winningStates, x -> !x.isEmpty());

    // Player 2 can win by matching the action of Player 1 one step delayed.
    assertThat(game.getAttractorFixpoint(winningStates, Owner.PLAYER_2),
      x -> x.contains(game.onlyInitialState()));

    // Player 1 can never win...
    assertThat(game.getAttractorFixpoint(winningStates, Owner.PLAYER_1),
      x -> !x.contains(game.onlyInitialState()));
  }

  public static Automaton<Object, ParityAcceptance> translate(LabelledFormula x) {
    var dpa = new LTL2DPAFunction(
      Environment.annotated(), LTL2DPAFunction.RECOMMENDED_ASYMMETRIC_CONFIG).apply(x);
    var complete = Views.complete(
      OmegaAcceptanceCast.cast((Automaton<Object, ?>) dpa, ParityAcceptance.class),
      new MutableAutomatonUtil.Sink());
    return OmegaAcceptanceCast.cast(complete, ParityAcceptance.class);
  }
}
