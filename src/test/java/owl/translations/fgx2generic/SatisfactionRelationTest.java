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

package owl.translations.fgx2generic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import owl.collections.Lists2;
import owl.collections.ints.BitSets;
import owl.ltl.BooleanConstant;
import owl.ltl.Formula;
import owl.ltl.Literal;
import owl.ltl.parser.LtlParser;

public class SatisfactionRelationTest {

  @Test
  public void modelsConjunction() {
    LtlParser parser = new LtlParser();
    Formula formula = parser.parseLtl("a | (X b)");
    BitSet now = new BitSet();
    BitSet past = new BitSet();
    List<BitSet> valuations = ImmutableList.of(now, past);

    // { }, { }
    assertFalse(SatisfactionRelation.models(valuations, formula));

    // {a}, { }
    now.set(0);
    assertFalse(SatisfactionRelation.models(valuations, formula));

    // {a}, {a}
    past.set(0);
    assertTrue(SatisfactionRelation.models(valuations, formula));

    // { }, {a}
    now.clear(0);
    assertTrue(SatisfactionRelation.models(valuations, formula));

    // {b}, { }
    past.clear(0);
    now.set(1);
    assertTrue(SatisfactionRelation.models(valuations, formula));

    // {b}, {b}
    past.set(1);
    assertTrue(SatisfactionRelation.models(valuations, formula));
  }

  @Test
  public void modelsConstants() {
    assertTrue(SatisfactionRelation.models(Collections.emptyList(), BooleanConstant.TRUE));
    assertFalse(SatisfactionRelation.models(Collections.emptyList(), BooleanConstant.FALSE));
  }

  @Test
  public void modelsLiteral() {
    Literal literal = new Literal(0);
    assertFalse(SatisfactionRelation.models(Collections.emptyList(), literal));
    assertFalse(SatisfactionRelation.models(Collections.singletonList(new BitSet()), literal));

    BitSet set = new BitSet();
    set.set(0);
    assertTrue(SatisfactionRelation.models(Collections.singletonList(set), literal));
  }

  @Test
  public void modelsRequiredHistoryCompatible() {
    LtlParser parser = new LtlParser();
    Formula formula = parser.parseLtl("X (a | (X (b & X c)))");

    List<BitSet> allValuations = Lists.newArrayList(BitSets.powerSet(3));

    for (BitSet valuation : allValuations) {
      for (List<BitSet> history : Lists
        .cartesianProduct(allValuations, allValuations, allValuations, allValuations)) {
        List<BitSet> restrictedHistory = RequiredHistory.getRequiredHistory(formula);
        Util.intersection(restrictedHistory, history);
        assertEquals(SatisfactionRelation.models(Lists2.cons(valuation, history), formula),
          SatisfactionRelation.models(Lists2.cons(valuation, restrictedHistory), formula));
      }
    }
  }
}