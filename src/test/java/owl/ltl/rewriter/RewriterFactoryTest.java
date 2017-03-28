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

import org.junit.Test;
import owl.ltl.Formula;
import owl.ltl.parser.LtlParser;
import owl.ltl.rewriter.RewriterFactory.RewriterEnum;

public class RewriterFactoryTest {

  private static final String[] INPUT = new String[] {
    "F (a U b)",
    "F G X a",
    "F G F b",
    "a & a U b",
    "b & a U b",
    "! F G (a <-> (F b))",
    // TODO: This is part of the new simp: "(a & b) U (c | d)"
  };

  private static final String[] OUTPUT = new String[] {
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
      LtlParser parser = new LtlParser();
      Formula input = parser.parseLtl(INPUT[i]);
      Formula output = parser.parseLtl(OUTPUT[i]);
      assertEquals(output, RewriterFactory.apply(RewriterEnum.MODAL_ITERATIVE, input));
    }
  }

  @Test
  public void testPullupX() {
    LtlParser parser = new LtlParser();
    Formula f1 = parser.parseLtl(" G (F (X b))");
    Formula f2 = parser.parseLtl("X(G(F(b)))");
    assertEquals(RewriterFactory.apply(RewriterEnum.PULLUP_X, f1), f2);
  }
}
