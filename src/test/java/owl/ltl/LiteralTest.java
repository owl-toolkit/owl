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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.BitSet;
import org.junit.Test;

public class LiteralTest {

  @Test
  public void getAtom() {
    for (int atom = 0; atom < 42; atom++) {
      Literal literal = new Literal(atom, false);
      Literal notLiteral = new Literal(atom, true);
      assertEquals(atom, literal.getAtom());
      assertEquals(atom, notLiteral.getAtom());
    }
  }

  @Test
  public void isNegated() {
    Literal literal = new Literal(1);
    Literal notLiteral = new Literal(1, true);

    assertFalse(literal.isNegated());
    assertTrue(notLiteral.isNegated());
  }

  @Test
  public void isPureEventual() {
    Literal literal = new Literal(0);
    assertFalse(literal.isPureEventual());
  }

  @Test
  public void isPureUniversal() {
    Literal literal = new Literal(0);
    assertFalse(literal.isPureUniversal());
  }

  @Test
  public void isSuspendable() {
    Literal literal = new Literal(0);
    assertFalse(literal.isSuspendable());
  }

  @Test
  public void not() {
    Literal literal = new Literal(1);
    Literal notLiteral = new Literal(1, true);

    assertEquals(notLiteral, literal.not());
    assertEquals(literal, literal.not().not());

    assertNotEquals(literal, notLiteral);
  }

  @Test
  public void temporalStep() {
    Literal literal = new Literal(1);
    BitSet valuation = new BitSet();

    assertEquals(BooleanConstant.FALSE, literal.temporalStep(valuation));
    assertEquals(BooleanConstant.TRUE, literal.not().temporalStep(valuation));

    valuation.set(1);

    assertEquals(BooleanConstant.TRUE, literal.temporalStep(valuation));
    assertEquals(BooleanConstant.FALSE, literal.not().temporalStep(valuation));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testConstructor() {
    new Literal(-1);
  }

  @Test
  public void unfold() {
    Literal literal = new Literal(1);
    assertEquals(literal, literal.unfold());
  }
}