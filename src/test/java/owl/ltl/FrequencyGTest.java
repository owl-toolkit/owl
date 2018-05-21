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

package owl.ltl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import owl.ltl.parser.LtlParser;

class FrequencyGTest {

  @Test
  void testNegation() {
    Formula actual = LtlParser.syntax("G { >= 0.4} a");
    Formula expected = LtlParser.syntax("G {sup > 0.6} (!a)");
    assertEquals(expected, actual.not());
  }

  @Test
  void testUnfolding() {
    Formula formula = LtlParser.syntax("G { >= 0.4} a");
    assertEquals(formula, formula.unfold());
  }
}
