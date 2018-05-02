/*
 * Copyright (C) 2016  (See AUTHORS)
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.BitSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;
import owl.ltl.parser.LtlParser;
import owl.ltl.visitors.Collector;

@RunWith(Theories.class)
public class FormulaTest {
  @DataPoints
  public static final List<Formula> FORMULAS = List.of(
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

  @DataPoint
  public static final BitSet ONE = new BitSet();
  @DataPoint
  public static final BitSet THREE = new BitSet();
  @DataPoint
  public static final BitSet TWO = new BitSet();
  @DataPoint
  public static final BitSet ZERO = new BitSet();

  static {
    ONE.set(0);
    TWO.set(1);
    THREE.set(0, 2);
  }

  @Theory
  public void allMatch(Formula formula) {
    Set<Formula> subformulas = Collector.collect((Predicate<Formula>) x -> Boolean.TRUE, formula);
    assertTrue(formula.allMatch(x -> x instanceof Biconditional || x instanceof BooleanConstant
      || x instanceof PropositionalFormula || subformulas.contains(x)));
  }

  @Theory
  public void anyMatch(Formula formula) {
    Set<Formula> subformulas = Collector.collect((Predicate<Formula>) x -> Boolean.TRUE, formula);

    for (Formula x : subformulas) {
      assertTrue(formula.anyMatch(x::equals));
    }
  }

  @Theory
  public void isSuspendable(Formula formula) {
    assertTrue(!formula.isSuspendable() || formula.isPureUniversal());
    assertTrue(!formula.isSuspendable() || formula.isPureEventual());
  }

  @Theory
  public void nnf(Formula formula) {
    if (SyntacticFragment.NNF.contains(formula)) {
      assertEquals(formula, formula.nnf());
    } else {
      assertNotEquals(formula, formula.nnf());
      assertEquals(formula.nnf(), formula.nnf().nnf());
    }
  }

  @Theory
  public void not(Formula formula) {
    assertEquals(formula, formula.not().not());
    assertEquals(formula.not(), formula.not().not().not());
  }

  @Theory
  public void temporalStepUnfold(Formula formula, BitSet bitSet) {
    assertEquals(formula.temporalStep(bitSet).unfold(), formula.temporalStepUnfold(bitSet));
  }

  @Theory
  public void unfoldTemporalStep(Formula formula, BitSet bitSet) {
    assertEquals(formula.unfold().temporalStep(bitSet), formula.unfoldTemporalStep(bitSet));
  }
}
