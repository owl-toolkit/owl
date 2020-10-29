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

import com.google.common.primitives.ImmutableIntArray;
import java.util.BitSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import owl.logic.propositional.ConjunctiveNormalForm;
import owl.logic.propositional.PropositionalFormula;

/**
 * Interface for SAT-solver for propositional formulas.
 */
public final class Solver {

  private static final Engine DEFAULT_ENGINE = Engine.JBDD;

  private Solver() {}

  public static IncrementalSolver incrementalSolver() {
    return incrementalSolver(DEFAULT_ENGINE);
  }

  public static IncrementalSolver incrementalSolver(Engine engine) {
    assert engine == Engine.JBDD;
    return new JbddIncrementalSolver();
  }

  /**
   * Determine if the the given conjunctiveNormalForm is satisfiable and if this is the case return
   * a satisfying assignment. Note that variables are shifted by 1. Thus {@code bs.get(0)} retrieves
   * the assigend value to variable 1 in the given conjunctiveNormalForm.
   *
   * @param clauses in conjunctive normal form.
   * @return {@link Optional#empty()} if the given conjunctiveNormalForm is not satisfiable.
   *     Otherwise a satisfying assignment.
   */
  public static Optional<BitSet> model(ImmutableIntArray clauses) {
    return model(clauses, DEFAULT_ENGINE);
  }

  public static Optional<BitSet> model(ImmutableIntArray clauses, Engine engine) {
    var incrementalSolver = incrementalSolver(engine);
    incrementalSolver.pushClauses(clauses);
    return incrementalSolver.model();
  }

  public static <V> Optional<Set<V>> model(PropositionalFormula<V> formula) {
    return model(formula, DEFAULT_ENGINE);
  }

  public static <V> Optional<Set<V>> model(PropositionalFormula<V> formula, Engine engine) {
    if (formula instanceof PropositionalFormula.Disjunction) {
      for (var disjunct : ((PropositionalFormula.Disjunction<V>) formula).disjuncts) {
        Optional<Set<V>> satisfiable = model(disjunct, engine);

        if (satisfiable.isPresent()) {
          return satisfiable;
        }
      }

      return Optional.empty();
    }

    // Translate into equisatisfiable CNF.
    var conjunctiveNormalForm = new ConjunctiveNormalForm<>(formula);
    return model(conjunctiveNormalForm.clauses).map(bitSet -> bitSet.stream()
      .map(x -> x + 1) // shift indices
      .filter(conjunctiveNormalForm.variableMapping::containsValue) // skip Tsetin variables
      .mapToObj(i -> conjunctiveNormalForm.variableMapping.inverse().get(i))
      .collect(Collectors.toSet()));
  }

  enum Engine {
    JBDD
  }
}
