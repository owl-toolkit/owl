/*
 * Copyright (C) 2020, 2022  (Salomon Sickert)
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static owl.logic.propositional.PropositionalFormula.Conjunction;
import static owl.logic.propositional.PropositionalFormula.Disjunction;
import static owl.logic.propositional.PropositionalFormula.Negation;
import static owl.logic.propositional.PropositionalFormula.Variable;

import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import owl.collections.Collections3;

class SolverTest {

  @ParameterizedTest
  @EnumSource(
      value = Solver.class,
      names = {"DPLL", "JBDD", "KISSAT_EXTERNAL"})
  void testModel(Solver solver) {
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

    var model1a = solver.model(formula1);
    assertTrue(model1a.isPresent());
    assertTrue(formula1.evaluate(model1a.get()), formula1.toString() + " " + model1a.toString());

    var model1b = solver.model(Negation.of(formula1));
    assertTrue(model1b.isPresent());
    assertFalse(formula1.evaluate(model1b.get()));

    var formula2 = Conjunction.of(
        List.of(
            Negation.of(Variable.of(1)),
            Disjunction.of(
                Variable.of(1),
                Variable.of(3),
                Variable.of(4)
            ),
            Disjunction.of(
                Negation.of(Variable.of(2)),
                Negation.of(Variable.of(3))),
            Negation.of(Variable.of(4)),
            Variable.of(2)));

    var model2a = solver.model(formula2);
    assertTrue(model2a.isEmpty());

    var model2b = solver.model(Negation.of(formula2));
    assertTrue(model2b.isPresent());
    assertFalse(formula2.evaluate(model2b.get()));
  }

  @ParameterizedTest
  @EnumSource(
      value = Solver.class,
      names = {"DPLL", "JBDD", "KISSAT_EXTERNAL"})
  void testModelCnf(Solver solver) {
    var formula1 = List.of(
        new Solver.Clause<>(List.of(), List.of(1)),
        new Solver.Clause<>(List.of(1, 2, 3), List.of()),
        new Solver.Clause<>(List.of(), List.of(2, 3)));

    var model1a = solver.model(formula1);
    assertTrue(model1a.isPresent());
    assertTrue(formula1.stream().allMatch(x -> x.evaluate(model1a.get())),
        formula1.toString() + " " + model1a.toString());

    var formula2 = Conjunction.of(
        List.of(
            Negation.of(Variable.of(1)),
            Disjunction.of(
                Variable.of(1),
                Variable.of(3),
                Variable.of(4)
            ),
            Disjunction.of(
                Negation.of(Variable.of(2)),
                Negation.of(Variable.of(3))),
            Negation.of(Variable.of(4)),
            Variable.of(2)));

    var model2a = solver.model(formula2);
    assertTrue(model2a.isEmpty());

    var model2b = solver.model(Negation.of(formula2));
    assertTrue(model2b.isPresent());
    assertFalse(formula2.evaluate(model2b.get()));
  }

  @ParameterizedTest
  @EnumSource(
      value = Solver.class,
      names = {"JBDD"})
  void testMaximalModel(Solver solver) {
    var formula = Conjunction.of(
        List.of(
            Variable.of(1),
            Disjunction.of(
                Negation.of(Variable.of(1)),
                Variable.of(2),
                Variable.of(3)),
            Disjunction.of(
                Negation.of(Variable.of(2)),
                Negation.of(Variable.of(3))),
            Variable.of(4),
            Negation.of(Variable.of(5))));

    for (Set<Integer> upperBound : Sets.powerSet(Set.of(1, 2, 3, 4, 5, 6))) {
      List<Set<Integer>> actualMaximalModels = solver.maximalModels(formula, upperBound);
      List<Set<Integer>> expectedMaximalModels = new ArrayList<>();

      for (Set<Integer> model : Sets.powerSet(upperBound)) {
        if (formula.evaluate(model)) {
          expectedMaximalModels.add(model);
          expectedMaximalModels = Collections3
              .maximalElements(expectedMaximalModels, (x, y) -> y.containsAll(x));
        }
      }

      assertEquals(new HashSet<>(expectedMaximalModels), new HashSet<>(actualMaximalModels));
    }
  }
}