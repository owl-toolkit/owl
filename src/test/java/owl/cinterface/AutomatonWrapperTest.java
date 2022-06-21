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

package owl.cinterface;

import static org.junit.jupiter.api.Assertions.assertTimeout;
import static owl.cinterface.CAutomaton.AutomatonWrapper.EDGE_GROUP_DELIMITER;
import static owl.translations.LtlTranslationRepository.LtlToDpaTranslation.SLM21;

import java.time.Duration;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import owl.automaton.acceptance.ParityAcceptance;
import owl.cinterface.CAutomaton.AutomatonWrapper;
import owl.ltl.parser.LtlParser;
import owl.translations.LtlTranslationRepository.Option;

class AutomatonWrapperTest {

  @SuppressWarnings("PMD.UnusedFormalParameter")
  private static void assertEquals(
      AutomatonWrapper<?, ?> automaton,
      int state,
      int[] expectedTreeBuffer,
      int[] expectedEdgeBuffer,
      double[] expectedScoreBuffer) {

    var edgeTree = automaton.edgeTree(state, true);
    Assertions.assertArrayEquals(
        expectedTreeBuffer,
        edgeTree.tree.toArray(),
        Arrays.toString(edgeTree.tree.toArray()));
    Assertions.assertArrayEquals(
        expectedEdgeBuffer,
        edgeTree.edges.toArray(),
        Arrays.toString(edgeTree.edges.toArray()));
    Assertions.assertArrayEquals(
        expectedScoreBuffer,
        edgeTree.scores.toArray());
  }

  @Test
  void testEdges() {
    var translation = SLM21.translation(
        ParityAcceptance.class, EnumSet.noneOf(Option.class), OptionalInt.empty());

    var formula0 = LtlParser.parse("true", List.of());
    var formula1 = LtlParser.parse("false", List.of());
    var formula2 = LtlParser.parse("a & b", List.of("a", "b"));
    var formula3 = LtlParser.parse("a & X b", List.of("a", "b"));
    var formula4 = LtlParser.parse("b & X a", List.of("a", "b"));
    var formula5 = LtlParser.parse("F (b & X (!a & X !a))", List.of("a", "b"));
    var formula6 = LtlParser.parse("G (r -> F g)", List.of("r", "g"));
    var formula7 = LtlParser.parse("(G F a) | (G F b)", List.of("a", "b"));

    var automaton0 = AutomatonWrapper.of(translation.apply(formula0), -1);
    var automaton1 = AutomatonWrapper.of(translation.apply(formula1), -1);
    var automaton2 = AutomatonWrapper.of(translation.apply(formula2), -1);
    var automaton3 = AutomatonWrapper.of(translation.apply(formula3), -1);
    var automaton4 = AutomatonWrapper.of(translation.apply(formula4), -1);
    var automaton5 = AutomatonWrapper.of(translation.apply(formula5), -1);
    var automaton6 = AutomatonWrapper.of(translation.apply(formula6), -1);
    var automaton7 = AutomatonWrapper.of(translation.apply(formula7), -1);

    assertEquals(automaton0, 0,
        new int[]{},
        new int[]{0, 0, EDGE_GROUP_DELIMITER},
        new double[]{1.0d});
    assertEquals(automaton1, 0,
        new int[]{},
        new int[]{EDGE_GROUP_DELIMITER},
        new double[]{});
    assertEquals(automaton2, 0,
        new int[]{0, -1, 3, 1, -1, -2},
        new int[]{EDGE_GROUP_DELIMITER, 1, EDGE_GROUP_DELIMITER},
        new double[]{1.0d});
    assertEquals(automaton3, 0,
        new int[]{0, -1, -2},
        new int[]{EDGE_GROUP_DELIMITER, 1, EDGE_GROUP_DELIMITER},
        new double[]{0.0d}); // was 0.0, 0.5
    assertEquals(automaton4, 0,
        new int[]{1, -1, -2},
        new int[]{EDGE_GROUP_DELIMITER, 1, EDGE_GROUP_DELIMITER},
        new double[]{0.0d}); // was 0.0, 0.5
    assertEquals(automaton5, 0,
        new int[]{1, -1, -4},
        new int[]{0, 1, EDGE_GROUP_DELIMITER, 1, 1, EDGE_GROUP_DELIMITER},
        new double[]{0.25d, 0.437_5d});
    // was 0.625d, 0.718_75d
    assertEquals(automaton5, 1,
        new int[]{0, -1, 3, 1, -4, -7},
        new int[]{
            2, 1, EDGE_GROUP_DELIMITER, 0, 1, EDGE_GROUP_DELIMITER, 1, 1, EDGE_GROUP_DELIMITER
        },
        new double[]{0.625d, 0.25d, 0.437_5d});
    // was 0.8125d, 0.625d, 0.718_75d

    assertEquals(automaton6, 0,
        new int[]{0, -1, 3, 1, -4, -1},
        new int[]{0, 0, EDGE_GROUP_DELIMITER, 1, 0, EDGE_GROUP_DELIMITER},
        new double[]{0.125d, 0.375d});
    // was 1.0d, 0.75d
    assertEquals(automaton6, 1,
        new int[]{1, -1, -4},
        new int[]{1, 1, EDGE_GROUP_DELIMITER, 0, 0, EDGE_GROUP_DELIMITER},
        new double[]{0.375d, 0.125d});
    // was 0.75d, 1.0d
    assertEquals(automaton7, 0,
        new int[]{0, 3, -4, 1, -1, -4},
        new int[]{0, 1, EDGE_GROUP_DELIMITER, 0, 0, EDGE_GROUP_DELIMITER},
        new double[]{0.468_75d, 0.468_75d});
    // was 0.9375d, 1.0d
  }

  @Tag("performance")
  @Test
  void testEdgesPerformance() {
    var translation = SLM21.translation(
        ParityAcceptance.class, EnumSet.noneOf(Option.class), OptionalInt.empty());

    assertTimeout(Duration.ofMillis(100), () -> {
      var formula = LtlParser.parse("(a|b|c|d|e|f|g|h|i|j|k|l|m|n|o|p|q|r|s|t|u|v)"
          + "& X G (w | x | y)");
      Assertions.assertEquals(25, formula.atomicPropositions().size());

      var instance1 = AutomatonWrapper.of(translation.apply(formula), -1);
      var instance2 = AutomatonWrapper.of(translation.apply(formula), -1);

      var edgeTree = instance1.edgeTree(0, true);
      Assertions.assertEquals(66, edgeTree.tree.size());

      edgeTree = instance2.edgeTree(0, true);
      Assertions.assertEquals(66, edgeTree.tree.size());
    });
  }
}
