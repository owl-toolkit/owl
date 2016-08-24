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

package omega_automaton.acceptance;

import omega_automaton.output.HOAConsumerExtended;
import org.junit.Test;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(Theories.class)
public class ParityAcceptanceTest {

    @DataPoint
    public static final ParityAcceptance ACCEPTANCE = new ParityAcceptance(1);

    @DataPoint
    public static final ParityAcceptance ACCEPTANCE_COMPLEMENT = (new ParityAcceptance(1)).complement();

    @Theory
    public void complement(ParityAcceptance acceptance) throws Exception {
        ParityAcceptance complement = acceptance.complement();
        assertNotEquals(acceptance, complement);
        assertEquals(acceptance, complement.complement());
    }

    @Theory
    public void getName(OmegaAcceptance acceptance) throws Exception {
        assertEquals("parity", acceptance.getName());
    }

    @Theory
    public void getAcceptanceSets(OmegaAcceptance acceptance) throws Exception {
        assertEquals(1, acceptance.getAcceptanceSets());
    }

    @Test
    public void getBooleanExpression() throws Exception {
        assertEquals(HOAConsumerExtended.mkFin(0), ACCEPTANCE.getBooleanExpression());
        assertEquals(HOAConsumerExtended.mkInf(0), ACCEPTANCE_COMPLEMENT.getBooleanExpression());
    }

}
