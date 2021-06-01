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

package owl.translations.mastertheorem;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import owl.ltl.BooleanConstant;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.parser.LtlParser;

class RewriterTest {
  private static final List<String> ALPHABET = List.of("a", "b");

  @Disabled
  @Test
  void testFgSubstitution() {
    Formula formula = LtlParser.parse("a U (X((G(F(G(b)))) & (F(X(X(G(b)))))))").formula();

    GOperator operator1 = (GOperator) LtlParser.parse("G b", ALPHABET).formula();
    Rewriter.ToCoSafety visitor1 = new Rewriter.ToCoSafety(Set.of(operator1));
    assertEquals(BooleanConstant.FALSE, formula.accept(visitor1));

    GOperator operator2 = (GOperator) LtlParser.parse("G F G b", ALPHABET).formula();
    Rewriter.ToCoSafety visitor2 = new Rewriter.ToCoSafety(Set.of(operator1, operator2));
    assertEquals(BooleanConstant.TRUE, formula.accept(visitor2));
  }

  @Disabled
  @Test
  void testToCoSafety() {
    assertEquals(BooleanConstant.FALSE, BooleanConstant.FALSE);
  }

  @Disabled
  @Test
  void testToSafety() {
    assertEquals(BooleanConstant.FALSE, BooleanConstant.FALSE);
  }
}
