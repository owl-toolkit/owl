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

package owl.translations;

import static owl.bdd.EquivalenceClassFactory.Encoding.AP_COMBINED;

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

/**
 * Check if a language represented by an EquivalenceClass is "blocked". These checks are suitable
 * for on-the-fly automata generation.
 */
public final class BlockingElements {

  private BlockingElements() {
  }

  public static boolean isBlockedByCoSafety(EquivalenceClass state) {
    assert state.equals(state.unfold());

    if (SyntacticFragments.isCoSafety(state.encode(AP_COMBINED).unfold())) {
      return true;
    }

    var successors = state.temporalStepTree().flatValues();

    if (!(successors instanceof HashSet)) {
      successors = new HashSet<>(successors);
    }

    // Pre-filter.
    for (var iterator = successors.iterator(); iterator.hasNext(); ) {
      var successor = iterator.next();

      // The successor class belongs to different SCC, hence it is irrelevant.
      if (detectSccChange(state, successor)) {
        iterator.remove();
        continue;
      }

      // The SCC might have more than one state, hence we cannot say for sure if it is blocked
      // by a coSafety property.
      if (!state.equals(successor.unfold())) {
        return false;
      }
    }

    // Expensive check.
    for (var successor : successors) {
      // We found a potential non-blocking successor.
      if (extractBlockingCoSafetyFormulas(successor).anyMatch(Set::isEmpty)) {
        return false;
      }
    }

    return true;
  }

  public static boolean isBlockedBySafety(EquivalenceClass state) {
    assert state.equals(state.unfold());

    if (SyntacticFragments.isSafety(state.encode(AP_COMBINED).unfold())) {
      return true;
    }

    var successors = state.temporalStepTree().flatValues();

    if (!(successors instanceof HashSet)) {
      successors = new HashSet<>(successors);
    }

    // Pre-filter.
    for (var iterator = successors.iterator(); iterator.hasNext(); ) {
      var successor = iterator.next();

      // The successor class belongs to different SCC, hence it is irrelevant.
      if (detectSccChange(state, successor)) {
        iterator.remove();
        continue;
      }

      // The SCC might have more than one state, hence we cannot say for sure if it is blocked
      // by a safety property.
      if (!state.equals(successor.unfold())) {
        return false;
      }
    }

    // Expensive check.
    for (var successor : successors) {
      // We found a potential non-blocking successor.
      if (extractBlockingSafetyFormulas(successor).anyMatch(Set::isEmpty)) {
        return false;
      }
    }

    return true;
  }

  public static boolean isBlockedByTransient(EquivalenceClass state) {
    assert state.equals(state.unfold());

    for (EquivalenceClass successor : state.temporalStepTree().flatValues()) {
      if (!detectSccChange(state, successor)) {
        return false;
      }
    }

    return true;
  }

  // Remove this optimisation.
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

  public static boolean surelyContainedInDifferentSccs(
      EquivalenceClass state1, EquivalenceClass state2) {

    return !state1.support(true).equals(state2.support(true));
  }

  private static boolean detectSccChange(
      EquivalenceClass state, EquivalenceClass successor) {

    List<Formula> stateSupport = state.support(true);
    List<Formula> successorSupport = successor.support(true);
    // assert stateSupport.containsAll(successorSupport);
    assert stateSupport.size() >= successorSupport.size();
    return stateSupport.size() > successorSupport.size();
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

      for (Formula formula : clause) {
        if (formula instanceof Literal) {
          continue;
        }

        assert formula instanceof Formula.TemporalOperator;

        if (SyntacticFragments.isSafety(formula)
            && !isProperSubformula(formula, nonSafetyFormulas)) {

          clauseSafetyFormulas.add((Formula.TemporalOperator) formula);
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
