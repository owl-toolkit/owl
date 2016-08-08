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

package ltl;

import org.junit.Test;

import java.util.BitSet;

import static org.junit.Assert.*;

public class LiteralTest {

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor(){
        Literal literal = new Literal(-1);
    }

    @Test
    public void not() throws Exception {
        Literal literal = new Literal(1);
        Literal notLiteral = new Literal(1, true);

        assertEquals(notLiteral, literal.not());
        assertEquals(literal, literal.not().not());

        assertNotEquals(literal, notLiteral);
    }

    @Test
    public void temporalStep() throws Exception {
        Literal literal = new Literal(1);
        BitSet valuation = new BitSet();

        assertEquals(BooleanConstant.FALSE, literal.temporalStep(valuation));
        assertEquals(BooleanConstant.TRUE, literal.not().temporalStep(valuation));

        valuation.set(1);

        assertEquals(BooleanConstant.TRUE, literal.temporalStep(valuation));
        assertEquals(BooleanConstant.FALSE, literal.not().temporalStep(valuation));
    }

    @Test
    public void isPureEventual() throws Exception {
        Literal literal = new Literal(0);
        assertFalse(literal.isPureEventual());
    }

    @Test
    public void isPureUniversal() throws Exception {
        Literal literal = new Literal(0);
        assertFalse(literal.isPureUniversal());
    }

    @Test
    public void isSuspendable() throws Exception {
        Literal literal = new Literal(0);
        assertFalse(literal.isSuspendable());
    }

    @Test
    public void getAtom() throws Exception {
        for (int atom = 0; atom < 42; atom++) {
            Literal literal = new Literal(atom, false);
            Literal notLiteral = new Literal(atom, true);
            assertEquals(atom, literal.getAtom());
            assertEquals(atom, notLiteral.getAtom());
        }
    }

    @Test
    public void isNegated() throws Exception {
        Literal literal = new Literal(1);
        Literal notLiteral = new Literal(1, true);

        assertFalse(literal.isNegated());
        assertTrue(notLiteral.isNegated());
    }

    @Test
    public void unfold() throws Exception {
        Literal literal = new Literal(1);
        assertEquals(literal, literal.unfold());
    }
}
