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

package owl.automaton.symbolic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static owl.automaton.symbolic.SymbolicAutomaton.VariableType.COLOUR;
import static owl.automaton.symbolic.SymbolicAutomaton.VariableType.STATE;

import com.google.common.collect.Iterators;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import owl.automaton.Automaton;
import owl.automaton.Views;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.algorithm.SccDecomposition;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.translations.LtlTranslationRepository;

public class SymbolicSccDecompositionTest {

  private static final Function<LabelledFormula, Automaton<?, ? extends RabinAcceptance>> LTL_TO_DRA
    = LtlTranslationRepository.LtlToDraTranslation.EKS20.translation(RabinAcceptance.class);

  private static final Function<LabelledFormula, Automaton<?, ? extends BuchiAcceptance>> LTL_TO_NBA
    = LtlTranslationRepository.LtlToNbaTranslation.EKS20.translation(BuchiAcceptance.class);

  private final List<LabelledFormula> tests = Stream.of(
    "0",
    "1",
    "a",
    "a & b",
    "a | b",
    "a -> b",
    "a <-> b",
    "a xor b",
    "F a",
    "G a",
    "X a",
    "a M b",
    "a R b",
    "a U b",
    "a W b",
    "F a & F b",
    "F a & G b",
    "F a & X b",
    "F a | F b",
    "F a | G b",
    "F a | X b",
    "G a & F b",
    "G a & G b",
    "G a & X b",
    "G a | F b",
    "G a | G b",
    "G a | X b",
    "X a & F b",
    "X a & G b",
    "X a & X b",
    "X a | F b",
    "X a | G b",
    "X a | X b",
    "X a | X !a",
    "X X a | X X !a",
    "X X X a | X X X !a",
    "F a & X F !a",
    "F a | X F !a",
    "G a & X G !a",
    "G a | X G !a",
    "X F a",
    "X X F a",
    "X G a",
    "X X G a",
    "F G a",
    "G F a",
    "F G a & F G b",
    "G F a & G F b",
    "F G a & G F b",
    "G F a & F G b",
    "F G a | F G b",
    "G F a | G F b",
    "F G a | G F b",
    "G F a | F G b",
    "F G (a | X b)",
    "F G (a & X b)",
    "F G (a | X b | X X c)",
    "F G (a & X b & X X c)",
    "F (a & G b)",
    "F (a | G b)",
    "G (a & F b)",
    "G (a | F b)",
    "F (a M b)",
    "F (a R b)",
    "F (a U b)",
    "F (a W b)",
    "F X (a M b)",
    "F X (a R b)",
    "F X (a U b)",
    "F X (a W b)",
    "F ((X a) M b)",
    "F ((X a) R b)",
    "F ((X a) U b)",
    "F ((X a) W b)",
    "F (a M (X b))",
    "F (a R (X b))",
    "F (a U (X b))",
    "F (a W (X b))",
    "F ((a M b) & c)",
    "F ((a R b) & c)",
    "F ((a U b) & c)",
    "F ((a W b) & c)",
    "F ((a M b) | c)",
    "F ((a R b) | c)",
    "F ((a U b) | c)",
    "F ((a W b) | c)",
    "F ((a M b) & G c)",
    "F ((a R b) & G c)",
    "F ((a U b) & G c)",
    "F ((a W b) & G c)",
    "F ((a M b) | G c)",
    "F ((a R b) | G c)",
    "F ((a U b) | G c)",
    "F ((a W b) | G c)",
    "G (a M b)",
    "G (a R b)",
    "G (a U b)",
    "G (a W b)",
    "G X (a M b)",
    "G X (a R b)",
    "G X (a U b)",
    "G X (a W b)",
    "G ((X a) M b)",
    "G ((X a) R b)",
    "G ((X a) U b)",
    "G ((X a) W b)",
    "G (a M (X b))",
    "G (a R (X b))",
    "G (a U (X b))",
    "G (a W (X b))",
    "G ((a M b) & c)",
    "G ((a R b) & c)",
    "G ((a U b) & c)",
    "G ((a W b) & c)",
    "G ((a M b) | c)",
    "G ((a R b) | c)",
    "G ((a U b) | c)",
    "G ((a W b) | c)",
    "G ((a M b) & F c)",
    "G ((a R b) & F c)",
    "G ((a U b) & F c)",
    "G ((a W b) & F c)",
    "G ((a M b) | F c)",
    "G ((a R b) | F c)",
    "G ((a U b) | F c)",
    "G ((a W b) | F c)",
    "G ( a |  b) & GF ( b &  c)",
    "G ( a | Xb) & GF ( b &  c)",
    "G ( a |  b) & GF (Xb &  c)",
    "G ( a | Xb) & GF (Xb &  c)",
    "G ( a |  b) & GF ( b & Xc)",
    "G ( a | Xb) & GF ( b & Xc)",
    "G (Xa |  b) & GF (Xb &  c)",
    "G (Xa | Xb) & GF (Xb &  c)"
  ).map(LtlParser::parse).toList();

  @Test
  void sccDecompositionTest() {
    for (var formula : tests) {
      var dra = Views.complete(LTL_TO_DRA.apply(formula));
      var nba = Views.complete(LTL_TO_NBA.apply(formula));
      assertSccs(Views.stateAcceptanceAutomaton(dra), SymbolicAutomaton.of(dra));
      assertSccs(Views.stateAcceptanceAutomaton(nba), SymbolicAutomaton.of(nba));
    }
  }

  private static void assertSccs(Automaton<?, ?> expected, SymbolicAutomaton<?> actual) {
    // Since there is no relation between the explicit and the symbolic states,
    // we check whether the sizes of the SCCs are the same.
    List<Integer> expectedSccs = SccDecomposition
      .of(Views.stateAcceptanceAutomaton(expected))
      .sccs()
      .stream()
      .map(Set::size)
      .sorted()
      .toList();
    List<Integer> actualSccs = SymbolicSccDecomposition
      .of(actual)
      .sccs()
      .stream()
      .map(bddSet ->
        bddSet.iterator(actual.variableAllocation().variables(STATE, COLOUR)))
      .map(Iterators::size)
      .sorted()
      .toList();
    assertEquals(expectedSccs, actualSccs);
  }
}
