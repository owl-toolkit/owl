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

package owl.game;

import static owl.translations.LtlTranslationRepository.LtlToDpaTranslation;
import static owl.translations.LtlTranslationRepository.Option;
import static owl.util.Assertions.assertThat;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import owl.automaton.AnnotatedState;
import owl.automaton.Automaton;
import owl.automaton.Views;
import owl.automaton.acceptance.OmegaAcceptanceCast;
import owl.automaton.acceptance.ParityAcceptance;
import owl.game.Game.Owner;
import owl.game.GameViews.Node;
import owl.ltl.EquivalenceClass;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;

public class GameFactoryTest {

  @Test
  void testTransform() {
    var formula = LtlParser.parse("G (a <-> X b) & G F (!a | b | c)");
    var game = GameFactory.copyOf(
      GameViews.split(translate(formula), List.of("a", "c")));

    for (Node<?> state : game.states()) {
      for (Node<?> predecessor : game.predecessors((Node<Optional<?>>) state)) {
        assertThat(state, game.successors((Node<Optional<?>>) predecessor)::contains);
      }

      for (Node<?> successors : game.successors((Node<Optional<?>>) state)) {
        assertThat(state, game.predecessors((Node<Optional<?>>) successors)::contains);
      }
    }
  }

  @Test
  void testAttractor() {
    var formula = LtlParser.parse("F (a <-> X b)");
    var game = GameFactory.copyOf(
      GameViews.split(translate(formula), List.of("a")));

    var winningStates = game.states().stream()
      .filter(x -> {
        var state = (AnnotatedState) x.state().orElseThrow();
        return ((EquivalenceClass) state.state()).isTrue();
      }).collect(Collectors.toSet());
    assertThat(winningStates, x -> !x.isEmpty());

    // Player 2 can win by matching the action of Player 1 one step delayed.
    assertThat(game.getAttractorFixpoint(winningStates, Owner.PLAYER_2),
      x -> x.contains(game.initialState()));

    // Player 1 can never win...
    assertThat(game.getAttractorFixpoint(winningStates, Owner.PLAYER_1),
      x -> !x.contains(game.initialState()));
  }

  public static Automaton<Optional<?>, ? extends ParityAcceptance> translate(LabelledFormula x) {
    var dpa =
      LtlToDpaTranslation.SEJK16_EKRS17.translation(EnumSet.noneOf(Option.class)).apply(x);
    return OmegaAcceptanceCast.cast((Automaton) Views.complete(dpa), ParityAcceptance.class);
  }
}
