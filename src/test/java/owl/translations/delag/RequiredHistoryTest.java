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

package owl.translations.delag;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import java.util.BitSet;
import java.util.Collections;
import org.junit.Test;
import owl.ltl.BooleanConstant;
import owl.ltl.Formula;
import owl.ltl.Literal;
import owl.ltl.XOperator;
import owl.ltl.parser.LtlParser;

public class RequiredHistoryTest {

  @Test
  public void getRequiredHistoryConstant() {
    assertEquals(Collections.emptyList(), RequiredHistory.getRequiredHistory(BooleanConstant.TRUE));
  }

  @Test
  public void getRequiredHistoryLarge() {
    LtlParser parser = new LtlParser();
    Formula formula = parser.parseLtl("X (a | (X (b & X c)))");

    BitSet timeStep2 = new BitSet();
    timeStep2.set(0);
    BitSet timeStep1 = new BitSet();
    timeStep1.set(0);
    timeStep1.set(1);

    assertEquals(ImmutableList.of(timeStep1, timeStep2, new BitSet()),
      RequiredHistory.getRequiredHistory(formula));
  }

  @Test
  public void getRequiredHistoryLiteral() {
    Literal literal = new Literal(0);
    assertEquals(Collections.emptyList(), RequiredHistory.getRequiredHistory(literal));
  }

  @Test
  public void getRequiredHistorySmall() {
    LtlParser parser = new LtlParser();
    Formula formula = parser.parseLtl("a | (X b)");

    BitSet set = new BitSet();
    set.set(0);

    assertEquals(Collections.singletonList(set), RequiredHistory.getRequiredHistory(formula));
  }

  @Test
  public void getRequiredHistoryXOperator() {
    Formula formula = new XOperator(new Literal(0));
    BitSet set = new BitSet();
    assertEquals(Collections.singletonList(set), RequiredHistory.getRequiredHistory(formula));
  }
}