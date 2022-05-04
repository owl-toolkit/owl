/*
 * Copyright (C) 2016, 2022  (Salomon Sickert, Tobias Meggendorfer)
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.BitSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class LiteralTest {

  @Test
  void getAtom() {
    for (int atom = 0; atom < 42; atom++) {
      Literal literal = Literal.of(atom, false);
      Literal notLiteral = Literal.of(atom, true);
      assertEquals(atom, literal.getAtom());
      assertEquals(atom, notLiteral.getAtom());
    }
  }

  @Test
  void isNegated() {
    Literal literal = Literal.of(1);
    Literal notLiteral = Literal.of(1, true);

    assertFalse(literal.isNegated());
    assertTrue(notLiteral.isNegated());
  }

  @Test
  void isPureEventual() {
    Literal literal = Literal.of(0);
    assertFalse(literal.isPureEventual());
  }

  @Test
  void isPureUniversal() {
    Literal literal = Literal.of(0);
    assertFalse(literal.isPureUniversal());
  }

  @Test
  void isSuspendable() {
    Literal literal = Literal.of(0);
    assertFalse(literal.isSuspendable());
  }

  @Test
  void not() {
    Literal literal = Literal.of(1);
    Literal notLiteral = Literal.of(1, true);

    assertEquals(notLiteral, literal.not());
    assertEquals(literal, literal.not().not());

    assertNotEquals(literal, notLiteral);
  }

  @Test
  void temporalStep() {
    Literal literal = Literal.of(1);
    BitSet valuation = new BitSet();

    assertEquals(BooleanConstant.FALSE, literal.temporalStep(valuation));
    assertEquals(BooleanConstant.TRUE, literal.not().temporalStep(valuation));

    valuation.set(1);

    assertEquals(BooleanConstant.TRUE, literal.temporalStep(valuation));
    assertEquals(BooleanConstant.FALSE, literal.not().temporalStep(valuation));
  }

  @Test
  void testConstructor() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> Literal.of(-1));
  }

  @Test
  void unfold() {
    Literal literal = Literal.of(1);
    assertEquals(literal, literal.unfold());
  }
}
