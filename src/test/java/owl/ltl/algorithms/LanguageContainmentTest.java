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

package owl.ltl.algorithms;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import owl.ltl.parser.LtlParser;

class LanguageContainmentTest {

  @Test
  void isSatisfiable() {
    assertTrue(LanguageAnalysis.isSatisfiable(LtlParser.parse("F a").formula()));
  }

  @Test
  void isUnsatisfiable() {
    assertTrue(LanguageAnalysis.isUnsatisfiable(LtlParser.parse("F a & !a & X G !a").formula()));
  }

  @Test
  void isUniversal() {
    assertTrue(LanguageAnalysis.isUniversal(LtlParser.parse("X X F a | X X F !a").formula()));
  }
}