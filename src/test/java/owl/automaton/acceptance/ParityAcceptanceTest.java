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

package owl.automaton.acceptance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;
import owl.automaton.output.HoaConsumerExtended;

@RunWith(Theories.class)
public class ParityAcceptanceTest {

  @DataPoint
  public static final ParityAcceptance ACCEPTANCE = new ParityAcceptance(1);

  @DataPoint
  public static final ParityAcceptance ACCEPTANCE_COMPLEMENT = new ParityAcceptance(1).complement();

  @Theory
  public void complement(ParityAcceptance acceptance) {
    ParityAcceptance complement = acceptance.complement();
    assertNotEquals(acceptance, complement);
    assertEquals(acceptance, complement.complement());
  }

  @Theory
  public void getAcceptanceSets(OmegaAcceptance acceptance) {
    assertEquals(1, acceptance.getAcceptanceSets());
  }

  @Test
  public void getBooleanExpression() {
    assertEquals(HoaConsumerExtended.mkFin(0), ACCEPTANCE.getBooleanExpression());
    assertEquals(HoaConsumerExtended.mkInf(0), ACCEPTANCE_COMPLEMENT.getBooleanExpression());
  }

  @Theory
  public void getName(OmegaAcceptance acceptance) {
    assertEquals("parity", acceptance.getName());
  }

  @Theory
  public void getNameExtra(OmegaAcceptance acceptance) {
    List<Object> extra = acceptance.getNameExtra();

    // Check types
    assertTrue(extra.get(0) instanceof String);
    assertTrue(extra.get(1) instanceof String);
    assertTrue(extra.get(2) instanceof Integer);
  }
}
