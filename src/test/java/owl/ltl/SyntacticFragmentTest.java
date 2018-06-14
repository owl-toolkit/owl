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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import owl.ltl.parser.LtlParser;

public class SyntacticFragmentTest {

  @DataPoints
  public static final List<Formula> FORMULAS;

  static {
    FORMULAS = List.of(
      LtlParser.syntax("true"),
      LtlParser.syntax("false"),
      LtlParser.syntax("a"),
      LtlParser.syntax("F a"),
      LtlParser.syntax("G a"),
      LtlParser.syntax("X a"),
      LtlParser.syntax("a U b"),
      LtlParser.syntax("a R b"),
      LtlParser.syntax("a & X F b")
    );
  }

  @Test
  public void isCoSafety() {
    assertTrue(SyntacticFragment.CO_SAFETY.contains(FORMULAS.get(0)));
  }

  @Test
  public void isX() {
    assertTrue(SyntacticFragment.FINITE.contains(FORMULAS.get(0)));
    assertTrue(SyntacticFragment.FINITE.contains(FORMULAS.get(1)));
    assertTrue(SyntacticFragment.FINITE.contains(FORMULAS.get(2)));
    assertFalse(SyntacticFragment.FINITE.contains(FORMULAS.get(3)));
    assertFalse(SyntacticFragment.FINITE.contains(FORMULAS.get(4)));
    assertTrue(SyntacticFragment.FINITE.contains(FORMULAS.get(5)));
    assertFalse(SyntacticFragment.FINITE.contains(FORMULAS.get(8)));
  }

}