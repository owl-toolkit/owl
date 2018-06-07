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

package owl.ltl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.Lists;
import java.util.BitSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import owl.ltl.parser.LtlParser;
import owl.ltl.visitors.Collector;

@SuppressWarnings("PMD.UnusedPrivateMethod")
public class FormulaTest {

  private static final List<Formula> FORMULAS = List.of(
    LtlParser.syntax("true"),
    LtlParser.syntax("false"),
    LtlParser.syntax("a"),

    LtlParser.syntax("! a"),
    LtlParser.syntax("a & b"),
    LtlParser.syntax("a | b"),
    LtlParser.syntax("a -> b"),
    LtlParser.syntax("a <-> b"),
    LtlParser.syntax("a xor b"),

    LtlParser.syntax("F a"),
    LtlParser.syntax("G a"),
    LtlParser.syntax("X a"),

    LtlParser.syntax("a U b"),
    LtlParser.syntax("a R b"),
    LtlParser.syntax("a W b"),
    LtlParser.syntax("a M b"),

    LtlParser.syntax("F ((a W b) & c)"),
    LtlParser.syntax("F ((a R b) & c)"),
    LtlParser.syntax("G ((a M b) | c)"),
    LtlParser.syntax("G ((a U b) | c)"),

    LtlParser.syntax("G (X (a <-> b))"),
    LtlParser.syntax("G (X (a xor b))"),
    LtlParser.syntax("(a <-> b) xor (c <-> d)"));


  private static final BitSet ONE = new BitSet();

  private static final BitSet THREE = new BitSet();

  private static final BitSet TWO = new BitSet();

  private static final BitSet ZERO = new BitSet();

  static {
    ONE.set(0);
    TWO.set(1);
    THREE.set(0, 2);
  }

  public static Stream<Arguments> formulaProvider() {
    return FORMULAS.stream().map(Arguments::of);
  }

  private static Stream<Arguments> temporalStepCartesianProductProvider() {
    return Lists.cartesianProduct(FORMULAS, List.of(ZERO, ONE, TWO, THREE))
      .stream().map(x -> Arguments.of(x.toArray()));
  }

  @ParameterizedTest
  @MethodSource("formulaProvider")
  void allMatch(Formula formula) {
    Set<Formula> subformulas = Collector.collect((Predicate<Formula>) x -> Boolean.TRUE, formula);
    assertTrue(formula.allMatch(x -> x instanceof Biconditional || x instanceof BooleanConstant
      || x instanceof PropositionalFormula || subformulas.contains(x)));
  }

  @ParameterizedTest
  @MethodSource("formulaProvider")
  void anyMatch(Formula formula) {
    Set<Formula> subformulas = Collector.collect((Predicate<Formula>) x -> Boolean.TRUE, formula);

    for (Formula x : subformulas) {
      assertTrue(formula.anyMatch(x::equals));
    }
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

  @ParameterizedTest
  @MethodSource("temporalStepCartesianProductProvider")
  void temporalStepUnfold(Formula formula, BitSet bitSet) {
    assertEquals(formula.temporalStep(bitSet).unfold(), formula.temporalStepUnfold(bitSet));
  }

  @ParameterizedTest
  @MethodSource("temporalStepCartesianProductProvider")
  void unfoldTemporalStep(Formula formula, BitSet bitSet) {
    assertEquals(formula.unfold().temporalStep(bitSet), formula.unfoldTemporalStep(bitSet));
  }
}
