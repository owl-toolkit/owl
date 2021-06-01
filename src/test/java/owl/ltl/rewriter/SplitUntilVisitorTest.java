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

package owl.ltl.rewriter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;

public class SplitUntilVisitorTest {
  private static final List<String> LITERALS = List.of("a", "b", "c", "d", "t");

  private static final List<LabelledFormula> INPUTS = List.of(
      LtlParser.parse("X((a & b) U c)", LITERALS),
      LtlParser.parse("(a & F(b) & X(c)) U (!t)", LITERALS)
  );

  private static final List<LabelledFormula> OUTPUTS = List.of(
      LtlParser.parse("X((a U c)& (b U c))", LITERALS),
      LtlParser.parse("(a U !t) & (F(b) U !t) & (X(c) U (!t))", LITERALS)
  );

  @Test
  void testCorrectness() {
    SplitUntilVisitor v = new SplitUntilVisitor();
    for (int i = 0; i < INPUTS.size(); i++) {
      assertEquals(OUTPUTS.get(i).formula(), v.apply(INPUTS.get(i).formula()));
    }
  }
}
