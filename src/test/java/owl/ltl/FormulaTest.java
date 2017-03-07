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
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import java.util.BitSet;
import java.util.List;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;
import owl.ltl.parser.LtlParser;
import owl.ltl.parser.ParseException;

@RunWith(Theories.class)
public class FormulaTest {
  @DataPoints
  public static final List<Formula> FORMULAS;
  @DataPoint
  public static final BitSet ONE = new BitSet();
  @DataPoint
  public static final BitSet THREE = new BitSet();
  @DataPoint
  public static final BitSet TWO = new BitSet();
  @DataPoint
  public static final BitSet ZERO = new BitSet();

  static {
    LtlParser parser = new LtlParser();
    try {
      FORMULAS = ImmutableList.of(
        parser.parseLtl("true"),
        parser.parseLtl("false"),
        parser.parseLtl("a"),
        parser.parseLtl("F a"),
        parser.parseLtl("G a"),
        parser.parseLtl("X a"),
        parser.parseLtl("a U b"),
        parser.parseLtl("a R b")
      );
    } catch (ParseException ex) {
      throw new AssertionError(ex);
    }
  }

  static {
    ONE.set(0);
    TWO.set(1);
    THREE.set(0, 2);
  }

  @Theory
  public void isSuspendable(Formula formula) {
    assertTrue(!formula.isSuspendable() || formula.isPureUniversal());
    assertTrue(!formula.isSuspendable() || formula.isPureEventual());
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
