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

package owl.ltl.rewriter;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import owl.ltl.Formula;
import owl.ltl.parser.LtlParser;
import owl.ltl.rewriter.RewriterFactory.RewriterEnum;

public class RewriterFactoryTest {
  private static final List<String> literals = ImmutableList.of("a", "b");

  private static final String[] INPUT = {
    "F (a U b)",
    "F G X a",
    "F G F b",
    "a & a U b",
    "b & a U b",
    "! F G (a <-> (F b))",
    // TODO: This is part of the new simp: "(a & b) U (c | d)"
  };

  private static final String[] OUTPUT = {
    "F b",
    "F G a",
    "G F b",
    "a & a U b",
    "b",
    "((G F b & G F !a) | (F G !b & G F a))",
    // TODO: This is part of the new simp: "((a U c) & (b U c)) | ((a U d) & (b U d))"
  };

  @Test
  public void testModal() {
    for (int i = 0; i < INPUT.length; i++) {
      Formula input = LtlParser.create(INPUT[i]).parse(literals).getFormula();
      Formula output = LtlParser.create(OUTPUT[i]).parse(literals).getFormula();
      assertEquals(output, RewriterFactory.apply(RewriterEnum.MODAL_ITERATIVE, input));
    }
  }

  @Test
  public void testPullupX() {
    Formula f1 = LtlParser.formula("G (F (X b))");
    Formula f2 = LtlParser.formula("X(G(F(b)))");
    assertEquals(RewriterFactory.apply(RewriterEnum.PULLUP_X, f1), f2);
  }
}
