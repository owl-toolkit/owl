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

package owl.automaton.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static owl.logic.propositional.PropositionalFormula.Conjunction;
import static owl.logic.propositional.PropositionalFormula.Disjunction;
import static owl.logic.propositional.PropositionalFormula.Negation;
import static owl.logic.propositional.PropositionalFormula.Variable;

import org.junit.jupiter.api.Test;

class RabinAcceptanceTest {

  @Test
  void ofPartial() {
    // Test for aut2filt regression.
    // (Fin(0) & Inf(1) | Fin(2) & Inf(3) | Fin(4) & Inf(5))
    var condition = Disjunction.of(
      Conjunction.of(Negation.of(Variable.of(4)), Variable.of(5)),
      Conjunction.of(Negation.of(Variable.of(2)), Variable.of(3)),
      Conjunction.of(Negation.of(Variable.of(0)), Variable.of(1)));
    var parsedCondition = RabinAcceptance.ofPartial(condition);
    assertTrue(parsedCondition.isPresent());
    assertEquals(3, parsedCondition.get().pairs.size());
  }

  @Test
  void ofPartial2() {
    // Test for aut2filt regression.
    // (Fin(0) & Inf(1) | Fin(2) & Inf(3) | Fin(4) & Inf(5))
    var condition = RabinAcceptance.of(3).booleanExpression();
    var parsedCondition = RabinAcceptance.ofPartial(condition);
    assertTrue(parsedCondition.isPresent());
    assertEquals(3, parsedCondition.get().pairs.size());
  }
}