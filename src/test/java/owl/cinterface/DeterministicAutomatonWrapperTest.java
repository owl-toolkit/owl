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

package owl.cinterface;

import static org.junit.jupiter.api.Assertions.assertTimeout;

import java.time.Duration;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import owl.ltl.parser.LtlParser;

class DeterministicAutomatonWrapperTest {

  @SuppressWarnings("PMD.UnusedFormalParameter")
  private static void assertEquals(
    CAutomaton.DeterministicAutomatonWrapper<?, ?> automaton,
    int state,
    @Nullable int[] filter,
    int[] expectedTreeBuffer,
    int[] expectedEdgeBuffer,
    double[] expectedScoreBuffer) {

    var edgeTree = automaton.edgeTree(state, true);
    Assertions.assertArrayEquals(expectedTreeBuffer, edgeTree.tree.toArray());
    Assertions.assertArrayEquals(expectedEdgeBuffer, edgeTree.edges.toArray());
    Assertions.assertArrayEquals(expectedScoreBuffer, edgeTree.scores.toArray());
  }

  @Test
  void testEdges() {
    var formula0 = LtlParser.parse("true", List.of());
    var formula1 = LtlParser.parse("false", List.of());
    var formula2 = LtlParser.parse("a & b", List.of("a", "b"));
    var formula3 = LtlParser.parse("a & X b", List.of("a", "b"));
    var formula4 = LtlParser.parse("b & X a", List.of("a", "b"));
    var formula5 = LtlParser.parse("F (b & X (!a & X !a))", List.of("a", "b"));
    var formula6 = LtlParser.parse("G (r -> F g)", List.of("r", "g"));
    var formula7 = LtlParser.parse("(G F a) | (G F b)", List.of("a", "b"));

    var automaton0 = CAutomaton.DeterministicAutomatonWrapper.of(formula0);
    var automaton1 = CAutomaton.DeterministicAutomatonWrapper.of(formula1);
    var automaton2 = CAutomaton.DeterministicAutomatonWrapper.of(formula2);
    var automaton3 = CAutomaton.DeterministicAutomatonWrapper.of(formula3);
    var automaton4 = CAutomaton.DeterministicAutomatonWrapper.of(formula4);
    var automaton5 = CAutomaton.DeterministicAutomatonWrapper.of(formula5);
    var automaton6 = CAutomaton.DeterministicAutomatonWrapper.of(formula6);
    var automaton7 = CAutomaton.DeterministicAutomatonWrapper.of(formula7);

    assertEquals(automaton0, 0, null,
      new int[]{}, new int[]{-2, -1}, new double[]{1.0d});
    assertEquals(automaton1, 0, null,
      new int[]{}, new int[]{-1, -1}, new double[]{0.0d});
    assertEquals(automaton2, 0, null,
      new int[]{0, -1, 3, 1, -1, -2}, new int[]{-1, -1, -2, -1}, new double[]{0.0d, 1.0d});
    assertEquals(automaton3, 0, null,
      new int[]{0, -1, -2}, new int[]{-1, -1, 1, -1}, new double[]{0.0d, 0.5d});
    assertEquals(automaton4, 0, null,
      new int[]{1, -1, -2}, new int[]{-1, -1, 1, -1}, new double[]{0.0d, 0.5d});

    assertEquals(automaton5, 0, null,
      new int[]{1, -1, -2},
      new int[]{0, -1, 1, -1}, new double[]{0.625d, 0.718_75d});
    assertEquals(automaton5, 1, null,
      new int[]{0, -1, 3, 1, -2, -3},
      new int[]{2, -1, 0, -1, 1, -1}, new double[]{0.8125d, 0.625d, 0.718_75d});

    assertEquals(automaton6, 0, null,
      new int[]{0, -1, 3, 1, -2, -1}, new int[]{0, 0, 1, -1}, new double[]{1.0d, 0.75d});
    assertEquals(automaton6, 1, null,
      new int[]{1, -1, -2}, new int[]{1, -1, 0, 0}, new double[]{0.75d, 1.0d});
    assertEquals(automaton7, 0, null,
      new int[]{0, 3, -2, 1, -1, -2}, new int[]{0, -1, 0, 0}, new double[]{0.9375d, 1.0d});
  }

  @Disabled
  @Test
  void testMaskedEdges() {
    var automaton = CAutomaton.DeterministicAutomatonWrapper
      .of(LtlParser.parse("a | b", List.of("a", "b")));

    assertEquals(automaton, 0, new int[]{0, -1, -2},
      new int[]{0, -1, -2}, new int[]{-1, -1, -2, -1}, new double[]{0.0d, 1.0d});
    assertEquals(automaton, 0, new int[]{0, -2, -1},
      new int[]{0, 3, -1, 1, -1, -2}, new int[]{-1, -1, -2, -1}, new double[]{0.0d, 1.0d});
    assertEquals(automaton, 0, new int[]{0, -1, -1},
      new int[]{}, new int[]{-1, -1}, new double[]{0.0d});
    assertEquals(automaton, 0, new int[]{0, -2, -2},
      new int[]{0, 3, -2, 1, -1, -2}, new int[]{-1, -1, -2, -1}, new double[]{0.0d, 1.0d});

    assertEquals(automaton, 0, new int[]{1, -1, -2},
      new int[]{1, -1, -2}, new int[]{-1, -1, -2, -1}, new double[]{0.0d, 1.0d});
    assertEquals(automaton, 0, new int[]{1, -2, -1},
      new int[]{0, -1, 3, 1, -2, -1}, new int[]{-1, -1, -2, -1}, new double[]{0.0d, 1.0d});
    assertEquals(automaton, 0, new int[]{1, -1, -1},
      new int[]{}, new int[]{-1, -1}, new double[]{0.0d});
    assertEquals(automaton, 0, new int[]{1, -2, -2},
      new int[]{0, 3, -2, 1, -1, -2}, new int[]{-1, -1, -2, -1}, new double[]{0.0d, 1.0d});

    assertEquals(automaton, 0, new int[]{0, -1, 3, 1, -1, -2},
      new int[]{0, -1, 3, 1, -1, -2}, new int[]{-1, -1, -2, -1}, new double[]{0.0d, 1.0d});
  }

  @Test
  void testEdgesPerformance() {
    assertTimeout(Duration.ofMillis(100), () -> {
      var formula = LtlParser.parse("(a|b|c|d|e|f|g|h|i|j|k|l|m|n|o|p|q|r|s|t|u|v)"
        + "& X G (w | x | y)");
      Assertions.assertEquals(25, formula.atomicPropositions().size());

      var instance1 = CAutomaton.DeterministicAutomatonWrapper.of(formula);
      var instance2 = CAutomaton.DeterministicAutomatonWrapper.of(formula.not());

      var edgeTree = instance1.edgeTree(0, true);
      Assertions.assertEquals(66, edgeTree.tree.size());

      edgeTree = instance2.edgeTree(0, true);
      Assertions.assertEquals(66, edgeTree.tree.size());
    });
  }
}