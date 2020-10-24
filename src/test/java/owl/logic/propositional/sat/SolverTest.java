/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
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

package owl.logic.propositional.sat;

import static owl.logic.propositional.PropositionalFormula.Conjunction;
import static owl.logic.propositional.PropositionalFormula.Disjunction;
import static owl.logic.propositional.PropositionalFormula.Negation;
import static owl.logic.propositional.PropositionalFormula.Variable;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SolverTest {

  @Test
  void testSatisfyingAssignment() {
    var solver = SolverFactory.create();

    var formula1 = Conjunction.of(
      Negation.of(Variable.of(1)),
      Disjunction.of(
        Variable.of(1),
        Variable.of(2),
        Variable.of(3)
      ),
      Disjunction.of(
        Negation.of(Variable.of(2)),
        Negation.of(Variable.of(3))
      ));

    var satisfyingAssignment1 = solver.isSatisfiable(formula1);
    Assertions.assertTrue(satisfyingAssignment1.isPresent());
    Assertions.assertTrue(formula1.evaluate(satisfyingAssignment1.get()));

    var formula2 = Conjunction.of(
      Negation.of(Variable.of(1)),
      Disjunction.of(
        Variable.of(1),
        Variable.of(3),
        Variable.of(4)
      ),
      Disjunction.of(
        Negation.of(Variable.of(2)),
        Negation.of(Variable.of(3))
      ),
      Negation.of(Variable.of(4)),
      Conjunction.of(
        Variable.of(2)
      ));

    var satisfyingAssignment2 = solver.isSatisfiable(formula2);
    Assertions.assertTrue(satisfyingAssignment2.isEmpty());
  }
}