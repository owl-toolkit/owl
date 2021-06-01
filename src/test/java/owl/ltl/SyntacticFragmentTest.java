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

package owl.ltl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import owl.ltl.parser.LtlParser;

class SyntacticFragmentTest {

  private static final List<Formula> FORMULAS;

  static {
    FORMULAS = List.of(
        LtlParser.parse("true").formula(),
        LtlParser.parse("false").formula(),
        LtlParser.parse("a").formula(),
        LtlParser.parse("F a").formula(),
        LtlParser.parse("G a").formula(),
        LtlParser.parse("X a").formula(),
        LtlParser.parse("a U b").formula(),
        LtlParser.parse("a R b").formula(),
        LtlParser.parse("a & X F b").formula()
    );
  }

  @Test
  void isCoSafety() {
    assertTrue(SyntacticFragments.isCoSafety(FORMULAS.get(0)));
  }

  @Test
  void isX() {
    assertTrue(SyntacticFragments.isFinite(FORMULAS.get(0)));
    assertTrue(SyntacticFragments.isFinite(FORMULAS.get(1)));
    assertTrue(SyntacticFragments.isFinite(FORMULAS.get(2)));
    assertFalse(SyntacticFragments.isFinite(FORMULAS.get(3)));
    assertFalse(SyntacticFragments.isFinite(FORMULAS.get(4)));
    assertTrue(SyntacticFragments.isFinite(FORMULAS.get(5)));
    assertFalse(SyntacticFragments.isFinite(FORMULAS.get(8)));
  }
}