/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
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

package owl.translations.delag;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.BitSet;
import org.junit.jupiter.api.Test;
import owl.ltl.BooleanConstant;
import owl.ltl.Formula;
import owl.ltl.Literal;
import owl.ltl.parser.LtlParser;

class SatisfactionRelationTest {

  @Test
  void modelsConjunction() {
    Formula formula = LtlParser.parse("a | (X b)").formula();
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
  void modelsConstants() {
    assertTrue(SatisfactionRelation.models(new History(), new BitSet(), BooleanConstant.TRUE));
    assertFalse(SatisfactionRelation.models(new History(), new BitSet(), BooleanConstant.FALSE));
  }

  @Test
  void modelsLiteral() {
    Literal literal = Literal.of(0);
    assertFalse(SatisfactionRelation.models(new History(), new BitSet(), literal));

    BitSet set = new BitSet();
    set.set(0);
    assertTrue(SatisfactionRelation.models(new History(), set, literal));
  }
}