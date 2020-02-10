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

package owl.translations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.common.collect.Maps;
import java.util.BitSet;
import java.util.Map;
import org.junit.jupiter.api.Test;
import owl.automaton.Automaton;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.algorithm.LanguageEmptiness;
import owl.automaton.edge.Edge;
import owl.ltl.BooleanConstant;
import owl.ltl.EquivalenceClass;
import owl.ltl.parser.LtlParser;
import owl.run.Environment;

class LTL2DAModuleFunctionTest {
  private static final String LARGE = "(a|b|c|d|e|f|g|h|i|j|k|l|m|n|o|p|q|r|s|t|u|v|w|x|y|z)"
      + "& X G (x1 | x2 | x3)";

  private static final LTL2DAFunction TRANSLATOR
    = new LTL2DAFunction(ParityAcceptance.class, Environment.standard());

  @Test
  void construct() {
    var formula = LtlParser.parse("a | b R X c");

    var automaton = TRANSLATOR.apply(formula);
    var complementAutomaton = TRANSLATOR.apply(formula.not());

    assertEquals(automaton.size(), complementAutomaton.size());
    assertFalse(LanguageEmptiness.isEmpty(automaton));
    assertFalse(LanguageEmptiness.isEmpty(complementAutomaton));
  }

  @Test
  void performanceSafety() {
    var formula = LtlParser.parse(LARGE);
    assertEquals(29, formula.atomicPropositions().size());

    var automaton = (Automaton<Object, ?>) TRANSLATOR.apply(formula);
    var state = automaton.onlyInitialState();

    // Check null successor.
    BitSet empty = new BitSet();
    assertNull(automaton.edge(state, empty));
    assertEquals(Map.of(), Maps.filterValues(automaton.edgeMap(state), x -> x.contains(empty)));
  }

  @Test
  void performanceCosafety() {
    var formula = LtlParser.parse(LARGE).not();
    assertEquals(29, formula.atomicPropositions().size());

    var automaton = (Automaton<EquivalenceClass, ?>) TRANSLATOR.apply(formula);
    var state = automaton.onlyInitialState().factory().of(BooleanConstant.TRUE);
    var edge = Edge.of(state, 0);

    // Check true sink.
    BitSet empty = new BitSet();
    assertEquals(edge, automaton.edge(state, empty));
    assertEquals(Map.of(edge, automaton.factory().universe()), automaton.edgeMap(state));
  }
}