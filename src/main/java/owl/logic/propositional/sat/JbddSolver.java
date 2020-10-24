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
import de.tum.in.jbdd.Bdd;
import de.tum.in.jbdd.BddFactory;
import de.tum.in.jbdd.ImmutableBddConfiguration;
import java.util.BitSet;
import java.util.Optional;
import owl.logic.propositional.ConjunctiveNormalForm;

public class JbddSolver implements Solver {

  @Override
  public Optional<BitSet> isSatisfiable(ConjunctiveNormalForm<?> conjunctiveNormalForm) {
    ImmutableIntArray clauses = conjunctiveNormalForm.clauses;

    var configuration = ImmutableBddConfiguration.builder()
      .logStatisticsOnShutdown(false)
      .useGlobalComposeCache(false)
      .integrityDuplicatesMaximalSize(50)
      .cacheBinaryDivider(4)
      .cacheTernaryDivider(4)
      .growthFactor(4)
      .build();

    // Do not use buildBddIterative, since 'support(...)' is broken.
    Bdd bdd = BddFactory.buildBddRecursive(10_000, configuration);
    int numberOfVariables = clauses.stream().map(Math::abs).max().orElse(0);
    bdd.createVariables(numberOfVariables);

    int conjunction = bdd.trueNode();
    int disjunction = bdd.falseNode();

    for (int i = 0, s = clauses.length(); i < s; i++) {
      int literal = clauses.get(i);

      if (literal == 0) {
        conjunction = bdd.reference(bdd.and(conjunction, disjunction));
        disjunction = bdd.falseNode();
        continue;
      }

      if (literal > 0) {
        disjunction = bdd.reference(
          bdd.or(disjunction, bdd.variableNode(literal - 1)));
      } else {
        disjunction = bdd.reference(
          bdd.or(disjunction, bdd.not(bdd.variableNode((-literal) - 1))));
      }
    }

    conjunction = bdd.reference(bdd.and(conjunction, disjunction));

    if (conjunction == bdd.falseNode()) {
      return Optional.empty();
    }

    return Optional.of(bdd.getSatisfyingAssignment(conjunction));
  }
}
