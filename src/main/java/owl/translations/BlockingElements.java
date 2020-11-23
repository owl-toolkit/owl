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

package owl.translations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import owl.collections.Collections3;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.Literal;
import owl.ltl.SyntacticFragments;

public final class BlockingElements {

  private BlockingElements() {}

  public static boolean isBlockedByCoSafety(EquivalenceClass clazz) {
    if (SyntacticFragments.isCoSafety(clazz)
      || extractBlockingCoSafetyFormulas(clazz).noneMatch(Set::isEmpty)) {
      return true;
    }

    int classTemporalOperatorsSize = clazz.temporalOperators(true).size();

    return clazz.temporalStepTree().flatValues().stream()
      .allMatch(
        x -> x.temporalOperators(true).size() < classTemporalOperatorsSize
          || SyntacticFragments.isCoSafety(x)
          || extractBlockingCoSafetyFormulas(x).noneMatch(Set::isEmpty));
  }

  public static boolean isBlockedBySafety(EquivalenceClass clazz) {
    if (SyntacticFragments.isSafety(clazz)
      || extractBlockingSafetyFormulas(clazz).noneMatch(Set::isEmpty)) {
      return true;
    }

    int classTemporalOperatorsSize = clazz.temporalOperators(true).size();

    return clazz.temporalStepTree().flatValues().stream()
      .allMatch(
        x -> x.temporalOperators(true).size() < classTemporalOperatorsSize
          || SyntacticFragments.isSafety(x)
          || extractBlockingSafetyFormulas(x).noneMatch(Set::isEmpty));
  }

  public static Set<Formula.TemporalOperator> blockingCoSafetyFormulas(EquivalenceClass clazz) {
    if (SyntacticFragments.isCoSafety(clazz)) {
      return clazz.temporalOperators();
    }

    return extractBlockingCoSafetyFormulas(clazz).reduce((x, y) -> {
      x.retainAll(y);
      return x;
    }).orElseThrow();
  }

  public static Set<Formula.TemporalOperator> blockingSafetyFormulas(EquivalenceClass clazz) {
    if (SyntacticFragments.isSafety(clazz)) {
      return clazz.temporalOperators();
    }

    return extractBlockingSafetyFormulas(clazz).reduce((x, y) -> {
      x.retainAll(y);
      return x;
    }).orElseThrow();
  }

  private static Stream<Set<Formula.TemporalOperator>>
    extractBlockingCoSafetyFormulas(EquivalenceClass clazz) {

    var nonCoSafetyFormulas = new ArrayList<>(clazz.temporalOperators());
    nonCoSafetyFormulas.removeIf(SyntacticFragments::isCoSafety);

    return clazz.disjunctiveNormalForm().stream().map(clause -> {
      List<Formula.TemporalOperator> clauseCoSafetyFormulas = new ArrayList<>();

      for (Formula literal : clause) {
        if (literal instanceof Literal) {
          continue;
        }

        assert literal instanceof Formula.TemporalOperator;

        if (SyntacticFragments.isCoSafety(literal)
          && !isProperSubformula(literal, nonCoSafetyFormulas)) {

          clauseCoSafetyFormulas.add((Formula.TemporalOperator) literal);
        }
      }

      // Select only the temporal operators that do not occur in the scope of other temporal
      // operators, since blocking should only depend on them.
      return new HashSet<>(
        Collections3.maximalElements(clauseCoSafetyFormulas, (x, y) -> y.anyMatch(x::equals)));
    });
  }

  private static Stream<Set<Formula.TemporalOperator>>
    extractBlockingSafetyFormulas(EquivalenceClass clazz) {

    var nonSafetyFormulas = new ArrayList<>(clazz.temporalOperators());
    nonSafetyFormulas.removeIf(SyntacticFragments::isSafety);

    return clazz.conjunctiveNormalForm().stream().map(clause -> {
      List<Formula.TemporalOperator> clauseSafetyFormulas = new ArrayList<>();

      for (Formula literal : clause) {
        if (literal instanceof Literal) {
          continue;
        }

        assert literal instanceof Formula.TemporalOperator;

        if (SyntacticFragments.isSafety(literal)
          && !isProperSubformula(literal, nonSafetyFormulas)) {

          clauseSafetyFormulas.add((Formula.TemporalOperator) literal);
        }
      }

      // Select only the temporal operators that do not occur in the scope of other temporal
      // operators, since blocking should only depend on them.
      return new HashSet<>(
        Collections3.maximalElements(clauseSafetyFormulas, (x, y) -> y.anyMatch(x::equals)));
    });
  }

  private static boolean isProperSubformula(Formula formula, Collection<? extends Formula> set) {
    return set.stream().anyMatch(x -> !x.equals(formula) && x.anyMatch(formula::equals));
  }
}
