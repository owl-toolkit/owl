/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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

import static owl.util.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import owl.ltl.Formula;
import owl.ltl.parser.LtlParser;

class FormulaIsomorphismTest {

  private static final List<String> VARIABLES = List.of("a", "b", "c", "d", "e", "f", "g");

  @Test
  void computeIsomorphism() {
    Formula formula1 = LtlParser.syntax("(F a | G b) & (G c | X d)", VARIABLES);
    Formula formula2 = LtlParser.syntax("(F a | X b) & (G c | X d)", VARIABLES);
    Formula formula3 = LtlParser.syntax("(F a | G c) & (G b | X d)", VARIABLES);

    assertThat(FormulaIsomorphism.compute(formula1, formula1),
      x -> Arrays.equals(x, new int[] {0, 1, 2, 3}));
    assertThat(FormulaIsomorphism.compute(formula1, formula2),
      Objects::isNull);
    assertThat(FormulaIsomorphism.compute(formula1, formula3),
      x -> Arrays.equals(x, (new int[] {0, 2, 1, 3})));
  }

  @Test
  void computeIsomorphism2() {
    Formula formula1 = LtlParser.syntax("G (a | b | X c | d | X X e | (f & F g))", VARIABLES);
    Formula formula2 = LtlParser.syntax("G ((a & F b) | X c | X X d | e | f | g)", VARIABLES);
    assertThat(FormulaIsomorphism.compute(formula1, formula2),
      x -> Arrays.equals(x, new int[] {5, 6, 2, 4, 3, 0, 1}));
  }
}