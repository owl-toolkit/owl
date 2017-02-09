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

package owl.ltl.simplifier;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import owl.ltl.Formula;
import owl.ltl.parser.Parser;
import owl.ltl.simplifier.Simplifier.Strategy;
import org.junit.Test;

public class SimplifierTest {

  private static final String[] EXPECTED = new String[] {
    "F b",
    "F G a",
    "G F b",
    // TODO: This is part of the new simp: "((a U c) & (b U c)) | ((a U d) & (b U d))"
  };
  private static final String[] INPUT = new String[] {
    "F (a U b)",
    "F G X a",
    "F G F b",
    // TODO: This is part of the new simp: "(a & b) U (c | d)"
  };

  @Test
  public void testModal() {
    BiMap<String, Integer> aliases = HashBiMap.create();

    for (int i = 0; i < INPUT.length; i++) {
      Formula input = Parser.formula(INPUT[i], aliases);
      Formula output = Parser.formula(EXPECTED[i], aliases);
      assertEquals(output, Simplifier.simplify(input, Strategy.MODAL_EXT));
    }
  }

  @Test
  public void testPullupX() {
    Formula f1 = Parser.formula(" G (F (X b))");
    Formula f2 = Parser.formula("X(G(F(b)))");
    assertEquals(Simplifier.simplify(f1, Simplifier.Strategy.PULLUP_X), f2);
  }
}
