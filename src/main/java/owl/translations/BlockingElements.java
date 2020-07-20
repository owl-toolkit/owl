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
import java.util.BitSet;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.SyntacticFragments;
import owl.translations.mastertheorem.Predicates;

public class BlockingElements {
  private final BitSet atomicPropositions;

  private final Set<Formula.TemporalOperator> blockingCoSafety;
  private final Set<Formula.TemporalOperator> blockingSafety;

  public BlockingElements(Formula formula) {
    this.atomicPropositions = formula.atomicPropositions(true);
    formula.subformulas(Predicates.IS_FIXPOINT)
      .forEach(x -> atomicPropositions.andNot(x.atomicPropositions(true)));

    if (formula instanceof Conjunction) {
      var coSafetyTemporalChildren = new ArrayList<Formula.TemporalOperator>();
      var otherChildren = new ArrayList<Formula>();

      for (Formula child : formula.operands) {
        if (child instanceof Formula.TemporalOperator && SyntacticFragments.isCoSafety(child)) {
          coSafetyTemporalChildren.add((Formula.TemporalOperator) child);
        } else {
          otherChildren.add(child);
        }
      }

      coSafetyTemporalChildren
        .removeIf(x -> otherChildren.stream().anyMatch(y -> y.anyMatch(x::equals)));
      blockingCoSafety = Set.of(coSafetyTemporalChildren.toArray(Formula.TemporalOperator[]::new));
    } else {
      blockingCoSafety = Set.of();
    }

    if (formula instanceof Disjunction) {
      var fixpoints = formula
        .subformulas(Predicates.IS_FIXPOINT)
        .stream()
        .filter(
          x -> !SyntacticFragments.isCoSafety(x) && !SyntacticFragments.isSafety(x))
        .collect(Collectors.toSet());

      blockingSafety = formula.operands
        .stream()
        .filter(x -> x instanceof Formula.TemporalOperator
          && SyntacticFragments.isSafety(x)
          && fixpoints.stream().noneMatch(y -> y.anyMatch(x::equals)))
        .map(Formula.TemporalOperator.class::cast)
        .collect(Collectors.toUnmodifiableSet());
    } else {
      blockingSafety = Set.of();
    }
  }

  public boolean isBlockedByCoSafety(EquivalenceClass clazz) {
    return SyntacticFragments.isCoSafety(clazz)
      || clazz.atomicPropositions(true).intersects(atomicPropositions)
      || !Collections.disjoint(blockingCoSafety, clazz.temporalOperators());
  }

  public boolean isBlockedBySafety(EquivalenceClass clazz) {
    return SyntacticFragments.isSafety(clazz)
      || !Collections.disjoint(blockingSafety, clazz.temporalOperators());
  }
}
