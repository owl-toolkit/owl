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

package owl.cinterface;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import owl.ltl.parser.LtlParser;

class DeterministicAutomatonTest {
  @Test
  void testTreeSerialisation() {
    var formula0 = LtlParser.parse("true", List.of());
    var formula1 = LtlParser.parse("false", List.of());
    var formula2 = LtlParser.parse("a & b", List.of("a", "b"));
    var formula3 = LtlParser.parse("a & X b", List.of("a", "b"));
    var formula4 = LtlParser.parse("b & X a", List.of("a", "b"));
    var formula5 = LtlParser.parse("F (b & X (!a & X !a))", List.of("a", "b"));
    var formula6 = LtlParser.parse("G (r -> F g)", List.of("r", "g"));
    var formula7 = LtlParser.parse("(G F a) | (G F b)", List.of("a", "b"));

    var automaton0 = DeterministicAutomaton.of(formula0);
    var automaton1 = DeterministicAutomaton.of(formula1);
    var automaton2 = DeterministicAutomaton.of(formula2);
    var automaton3 = DeterministicAutomaton.of(formula3);
    var automaton4 = DeterministicAutomaton.of(formula4);
    var automaton5 = DeterministicAutomaton.of(formula5);
    var automaton6 = DeterministicAutomaton.of(formula6);
    var automaton7 = DeterministicAutomaton.of(formula7);

    assertArrayEquals(new int[]{1, -2, -1},
      automaton0.edges(0));
    assertArrayEquals(new int[]{1, -1, -1},
      automaton1.edges(0));
    assertArrayEquals(new int[]{7, 0, 0, 4, 1, 0, -2, -1, -1, -2, -1},
      automaton2.edges(0));
    assertArrayEquals(new int[]{4, 0, 0, -2, -1, -1, 1, -1},
      automaton3.edges(0));
    assertArrayEquals(new int[]{4, 1, 0, -2, -1, -1, 1, -1},
      automaton4.edges(0));
    assertArrayEquals(new int[]{4, 1, 0, -2, 0, -1, 1, -1},
      automaton5.edges(0));
    assertArrayEquals(new int[]{7, 0, 0, 4, 1, -2, -4, 2, -1, 0, -1, 1, -1},
      automaton5.edges(1));
    assertArrayEquals(new int[]{7, 0, 0, 4, 1, -2, 0, 0, 0, 1, -1},
      automaton6.edges(0));
    assertArrayEquals(new int[]{4, 1, 0, -2, 1, -1, 0, 0},
      automaton6.edges(1));
    assertArrayEquals(new int[]{7, 0, 4, -2, 1, 0, -2, 0, -1, 0, 0},
      automaton7.edges(0));
  }

  @Test
  void testTreeSerialisationPerformance() {
    assertTimeout(Duration.ofMillis(100), () -> {
      var formula = LtlParser.parse("(a|b|c|d|e|f|g|h|i|j|k|l|m|n|o|p|q|r|s|t|u|v)"
        + "& X G (w | x | y)");
      assertEquals(25, formula.atomicPropositions().size());

      var instance1 = DeterministicAutomaton.of(formula);
      var instance2 = DeterministicAutomaton.of(formula.not());

      assertEquals(71, instance1.edges(0).length);
      assertEquals(71, instance2.edges(0).length);
    });
  }
}
