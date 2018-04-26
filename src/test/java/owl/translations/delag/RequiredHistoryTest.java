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

import java.util.BitSet;
import org.junit.Test;
import owl.ltl.BooleanConstant;
import owl.ltl.Formula;
import owl.ltl.Literal;
import owl.ltl.XOperator;
import owl.ltl.parser.LtlParser;

public class RequiredHistoryTest {

  @Test
  public void getRequiredHistoryConstant() {
    History emptyHistory = new History();
    assertEquals(emptyHistory,
      new History(RequiredHistory.getRequiredHistory(BooleanConstant.TRUE)));
    assertEquals(emptyHistory,
      new History(RequiredHistory.getRequiredHistory(BooleanConstant.FALSE)));
  }

  @Test
  public void getRequiredHistoryLarge() {
    Formula formula = LtlParser.syntax("X (a | (X (b & X c)))");

    History past = new History(new long[] {0, 1L, 3L});

    assertEquals(past, new History(RequiredHistory.getRequiredHistory(formula)));
  }

  @Test
  public void getRequiredHistoryLiteral() {
    History emptyHistory = new History();
    assertEquals(emptyHistory, new History(RequiredHistory.getRequiredHistory(new Literal(0))));
  }

  @Test
  public void getRequiredHistorySmall() {
    Formula formula = LtlParser.syntax("a | (X b)");

    BitSet set = new BitSet();
    set.set(0);

    History expected = new History(new long[] {1L});
    assertEquals(expected, new History(RequiredHistory.getRequiredHistory(formula)));
  }

  @Test
  public void getRequiredHistoryXOperator() {
    Formula formula = XOperator.of(new Literal(0));
    History oneStepHistory = new History(new long[1]);
    assertEquals(oneStepHistory, new History(RequiredHistory.getRequiredHistory(formula)));
  }
}