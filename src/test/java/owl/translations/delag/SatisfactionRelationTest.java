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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.BitSet;
import org.junit.Test;
import owl.ltl.BooleanConstant;
import owl.ltl.Formula;
import owl.ltl.Literal;
import owl.ltl.parser.LtlParser;

public class SatisfactionRelationTest {

  @Test
  public void modelsConjunction() {
    Formula formula = LtlParser.formula("a | (X b)");
    BitSet now = new BitSet();
    long[] pastArray = new long[1];
    History past = new History(pastArray);

    // { }, { }
    assertFalse(SatisfactionRelation.models(past, now, formula));

    // {a}, { }
    now.set(0);
    assertFalse(SatisfactionRelation.models(past, now, formula));

    // {a}, {a}
    pastArray[0] |= 1L;
    past = new History(pastArray);
    assertTrue(SatisfactionRelation.models(past, now, formula));

    // { }, {a}
    now.clear(0);
    assertTrue(SatisfactionRelation.models(past, now, formula));

    // {b}, { }
    assertTrue(SatisfactionRelation.models(past, now, formula));

    // {b}, {b}
    assertTrue(SatisfactionRelation.models(past, now, formula));
  }

  @Test
  public void modelsConstants() {
    assertTrue(SatisfactionRelation.models(new History(), new BitSet(), BooleanConstant.TRUE));
    assertFalse(SatisfactionRelation.models(new History(), new BitSet(), BooleanConstant.FALSE));
  }

  @Test
  public void modelsLiteral() {
    Literal literal = new Literal(0);
    assertFalse(SatisfactionRelation.models(new History(), new BitSet(), literal));

    BitSet set = new BitSet();
    set.set(0);
    assertTrue(SatisfactionRelation.models(new History(), set, literal));
  }

  /* @Test
  public void modelsRequiredHistoryCompatible() {
    Formula formula = LtlParser.formula("X (a | (X (b & X c)))");

    List<BitSet> allValuations = Lists.newArrayList(BitSets.powerSet(3));

    for (BitSet valuation : allValuations) {
      for (List<BitSet> history : Lists
        .cartesianProduct(allValuations, allValuations, allValuations, allValuations)) {
        History restrictedHistory = RequiredHistory.getRequiredHistory(formula);
        Util.intersection(restrictedHistory, history);
        assertEquals(SatisfactionRelation.models(Lists2.cons(valuation, history), formula),
          SatisfactionRelation.models(Lists2.cons(valuation, restrictedHistory), formula));
      }
    }
  } */
}