/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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

package owl.ltl.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;

class FormulaIsomorphismTest {

  private static final List<String> VARIABLES = List.of("a", "b", "c", "d", "e", "f", "g");

  @Test
  void computeIsomorphism() {
    Formula formula1 = LtlParser.syntax("(F a | G b) & (G c | X d)", VARIABLES);
    Formula formula2 = LtlParser.syntax("(F a | X b) & (G c | X d)", VARIABLES);
    Formula formula3 = LtlParser.syntax("(F a | G c) & (G b | X d)", VARIABLES);

    assertArrayEquals(new int[]{0, 1, 2, 3}, FormulaIsomorphism.compute(formula1, formula1));
    assertNull(FormulaIsomorphism.compute(formula1, formula2));
    assertArrayEquals(new int[]{0, 2, 1, 3}, FormulaIsomorphism.compute(formula1, formula3));
  }

  @Test
  void computeIsomorphism2() {
    Formula formula1 = LtlParser.syntax("G (a | b | X c | d | X X e | (f & F g))", VARIABLES);
    Formula formula2 = LtlParser.syntax("G ((a & F b) | X c | X X d | e | f | g)", VARIABLES);
    assertArrayEquals(new int[]{5, 6, 2, 4, 3, 0, 1},
      FormulaIsomorphism.compute(formula1, formula2));
  }

  @Test
  void testPerformance() {
    LabelledFormula formula1 = LtlParser.parse("(((r_0) && (X (r_1))) -> (X (X ((g_0) && (g_1)))))"
      + "&& (((r_0) && (X (r_2))) -> (X (X ((g_0) && (g_2)))))\n"
      + "&& (((r_0) && (X (r_3))) -> (X (X ((g_0) && (g_3)))))\n"
      + "&& (((r_0) && (X (r_4))) -> (X (X ((g_0) && (g_4)))))\n"
      + "&& (((r_0) && (X (r_5))) -> (X (X ((g_0) && (g_5)))))\n"
      + "&& (((r_1) && (X (r_2))) -> (X (X ((g_1) && (g_2)))))\n"
      + "&& (((r_1) && (X (r_3))) -> (X (X ((g_1) && (g_3)))))\n"
      + "&& (((r_1) && (X (r_4))) -> (X (X ((g_1) && (g_4)))))\n"
      + "&& (((r_1) && (X (r_5))) -> (X (X ((g_1) && (g_5)))))\n"
      + "&& (((r_2) && (X (r_3))) -> (X (X ((g_2) && (g_3)))))\n"
      + "&& (((r_2) && (X (r_4))) -> (X (X ((g_2) && (g_4)))))\n"
      + "&& (((r_2) && (X (r_5))) -> (X (X ((g_2) && (g_5)))))\n"
      + "&& (((r_3) && (X (r_4))) -> (X (X ((g_3) && (g_4)))))\n"
      + "&& (((r_3) && (X (r_5))) -> (X (X ((g_3) && (g_5)))))\n"
      + "&& (((r_4) && (X (r_5))) -> (X (X ((g_4) && (g_5)))))");

    LabelledFormula formula2 = LtlParser.parse("(((r_0) && (X (r_1))) -> (X ((g_0) && (g_1))))"
      + "&& (((r_0) && (X (r_2))) -> (X ((g_0) && (g_2))))\n"
      + "&& (((r_0) && (X (r_3))) -> (X ((g_0) && (g_3))))\n"
      + "&& (((r_0) && (X (r_4))) -> (X ((g_0) && (g_4))))\n"
      + "&& (((r_0) && (X (r_5))) -> (X ((g_0) && (g_5))))\n"
      + "&& (((r_1) && (X (r_2))) -> (X ((g_1) && (g_2))))\n"
      + "&& (((r_1) && (X (r_3))) -> (X ((g_1) && (g_3))))\n"
      + "&& (((r_1) && (X (r_4))) -> (X ((g_1) && (g_4))))\n"
      + "&& (((r_1) && (X (r_5))) -> (X ((g_1) && (g_5))))\n"
      + "&& (((r_2) && (X (r_3))) -> (X ((g_2) && (g_3))))\n"
      + "&& (((r_2) && (X (r_4))) -> (X ((g_2) && (g_4))))\n"
      + "&& (((r_2) && (X (r_5))) -> (X ((g_2) && (g_5))))\n"
      + "&& (((r_3) && (X (r_4))) -> (X ((g_3) && (g_4))))\n"
      + "&& (((r_3) && (X (r_5))) -> (X ((g_3) && (g_5))))\n"
      + "&& (((r_4) && (X (r_5))) -> (X ((g_4) && (g_5))))", formula1.atomicPropositions());

    assertTimeout(Duration.ofMillis(500),
      () -> assertNull(FormulaIsomorphism.compute(formula1.formula(), formula2.formula())));
  }
}