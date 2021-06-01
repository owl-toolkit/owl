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

package owl.ltl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import owl.ltl.parser.LtlParser;

public class FormulaTest {

  private static final List<Formula> FORMULAS = List.of(
      LtlParser.parse("false").formula(),
      LtlParser.parse("true").formula(),
      LtlParser.parse("a").formula(),

      LtlParser.parse("! a").formula(),
      LtlParser.parse("a & b").formula(),
      LtlParser.parse("a | b").formula(),
      LtlParser.parse("a -> b").formula(),
      LtlParser.parse("a <-> b").formula(),
      LtlParser.parse("a xor b").formula(),

      LtlParser.parse("F a").formula(),
      LtlParser.parse("G a").formula(),
      LtlParser.parse("X a").formula(),

      LtlParser.parse("a M b").formula(),
      LtlParser.parse("a R b").formula(),
      LtlParser.parse("a U b").formula(),
      LtlParser.parse("a W b").formula(),

      LtlParser.parse("(a <-> b) xor (c <-> d)").formula(),

      LtlParser.parse("F ((a R b) & c)").formula(),
      LtlParser.parse("F ((a W b) & c)").formula(),
      LtlParser.parse("G ((a M b) | c)").formula(),
      LtlParser.parse("G ((a U b) | c)").formula(),
      LtlParser.parse("G (X (a <-> b))").formula(),
      LtlParser.parse("G (X (a xor b))").formula());

  public static List<Formula> formulaProvider() {
    return FORMULAS;
  }

  public static Stream<Arguments> formulaPairProvider() {
    return Lists.cartesianProduct(FORMULAS, FORMULAS)
      .stream().map(x -> Arguments.of(x.get(0), x.get(1)));
  }

  @ParameterizedTest
  @MethodSource("formulaProvider")
  void allMatch(Formula formula) {
    Set<Formula.TemporalOperator> subformulas = formula.subformulas(Formula.TemporalOperator.class);
    assertTrue(formula.allMatch(
      x -> x instanceof Formula.PropositionalOperator || subformulas.contains(x)));
  }

  @ParameterizedTest
  @MethodSource("formulaProvider")
  void anyMatch(Formula formula) {
    for (Formula x : formula.subformulas(Formula.TemporalOperator.class)) {
      assertTrue(formula.anyMatch(x::equals));
    }
  }

  @ParameterizedTest
  @MethodSource("formulaPairProvider")
  void compareToEquals(Formula formula1, Formula formula2) {
    if (formula1.equals(formula2)) {
      assertEquals(0, formula1.compareTo(formula2));
      assertEquals(0, formula2.compareTo(formula1));
    } else {
      int comparison = formula1.compareTo(formula2);
      assertNotEquals(0, comparison);
      assertEquals(-comparison, formula2.compareTo(formula1));
    }
  }

  @Test
  void compareToSort() {
    Formula[] formulas = FORMULAS.toArray(Formula[]::new);
    Arrays.sort(formulas, Comparator.reverseOrder());
    assertEquals(Lists.reverse(FORMULAS), Arrays.asList(formulas));
  }

  @ParameterizedTest
  @MethodSource("formulaProvider")
  void isSuspendable(Formula formula) {
    assertTrue(!formula.isSuspendable() || formula.isPureUniversal());
    assertTrue(!formula.isSuspendable() || formula.isPureEventual());
  }

  @ParameterizedTest
  @MethodSource("formulaProvider")
  void nnf(Formula formula) {
    if (SyntacticFragment.NNF.contains(formula)) {
      assertEquals(formula, formula.nnf());
    } else {
      assertNotEquals(formula, formula.nnf());
      assertEquals(formula.nnf(), formula.nnf().nnf());
    }
  }

  @ParameterizedTest
  @MethodSource("formulaProvider")
  void not(Formula formula) {
    assertEquals(formula, formula.not().not());
    assertEquals(formula.not(), formula.not().not().not());
  }
}
