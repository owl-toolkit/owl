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

package owl.translations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static owl.util.Assertions.assertThat;

import com.google.common.collect.Maps;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.Map;
import org.junit.jupiter.api.Test;
import owl.automaton.Automaton;
import owl.automaton.algorithms.EmptinessCheck;
import owl.automaton.edge.Edge;
import owl.ltl.EquivalenceClass;
import owl.ltl.parser.LtlParser;
import owl.run.DefaultEnvironment;

class LTL2DAModuleFunctionTest {
  private static final String LARGE = "(a|b|c|d|e|f|g|h|i|j|k|l|m|n|o|p|q|r|s|t|u|v|w|x|y|z)"
      + "& X G (x1 | x2 | x3)";

  private static final LTL2DAFunction translator = new LTL2DAFunction(DefaultEnvironment.standard(),
    true, EnumSet.of(
      LTL2DAFunction.Constructions.SAFETY,
      LTL2DAFunction.Constructions.CO_SAFETY,
      LTL2DAFunction.Constructions.BUCHI,
      LTL2DAFunction.Constructions.CO_BUCHI,
      LTL2DAFunction.Constructions.PARITY));

  @Test
  void construct() {
    var formula = LtlParser.parse("a | b R X c");

    var automaton = translator.apply(formula);
    var complementAutomaton = translator.apply(formula.not());

    assertEquals(automaton.size(), complementAutomaton.size());
    assertFalse(EmptinessCheck.isEmpty(automaton));
    assertFalse(EmptinessCheck.isEmpty(complementAutomaton));
  }

  @Test
  void performanceSafety() {
    var formula = LtlParser.parse(LARGE);
    assertEquals(29, formula.variables().size());

    var automaton = (Automaton<Object, ?>) translator.apply(formula);
    var state = automaton.onlyInitialState();

    // Check null successor.
    BitSet empty = new BitSet();
    assertNull(automaton.edge(state, empty));
    assertThat(Maps.filterValues(automaton.edgeMap(state), x -> x.contains(empty)), Map::isEmpty);
  }

  @Test
  void performanceCosafety() {
    var formula = LtlParser.parse(LARGE).not();
    assertEquals(29, formula.variables().size());

    var automaton = (Automaton<EquivalenceClass, ?>) translator.apply(formula);
    var state = automaton.onlyInitialState().factory().getTrue();
    var edge = Edge.of(state, 0);

    // Check true sink.
    BitSet empty = new BitSet();
    assertEquals(edge, automaton.edge(state, empty));
    assertEquals(Map.of(edge, automaton.factory().universe()), automaton.edgeMap(state));
  }
}