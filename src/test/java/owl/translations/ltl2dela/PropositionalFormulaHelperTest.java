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

package owl.translations.ltl2dela;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static owl.logic.propositional.PropositionalFormula.Biconditional;
import static owl.logic.propositional.PropositionalFormula.Conjunction;
import static owl.logic.propositional.PropositionalFormula.Disjunction;
import static owl.logic.propositional.PropositionalFormula.Variable;
import static owl.logic.propositional.PropositionalFormula.falseConstant;
import static owl.logic.propositional.PropositionalFormula.trueConstant;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PropositionalFormulaHelperTest {

  @Test
  void findPartialAssignment() {
    assertEquals(
      Map.of(),
      PropositionalFormulaHelper.findPartialAssignment(trueConstant(), trueConstant()));
    assertNull(
      PropositionalFormulaHelper.findPartialAssignment(trueConstant(), falseConstant()));

    assertEquals(
      Map.of(0, true),
      PropositionalFormulaHelper.findPartialAssignment(Variable.of(0), trueConstant()));
    assertEquals(
      Map.of(0, false),
      PropositionalFormulaHelper.findPartialAssignment(Variable.of(0), falseConstant()));

    assertEquals(
      Map.of(1, true),
      PropositionalFormulaHelper.findPartialAssignment(
        Conjunction.of(Variable.of(0), Variable.of(1)), Variable.of(0)));
    assertEquals(Map.of(1, false),
      PropositionalFormulaHelper.findPartialAssignment(
        Disjunction.of(Variable.of(0), Variable.of(1)), Variable.of(0)));
    assertEquals(Map.of(1, true),
      PropositionalFormulaHelper.findPartialAssignment(
        Biconditional.of(Variable.of(0), Variable.of(1)), Variable.of(0)));
  }
}