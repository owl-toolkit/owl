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

package owl.factories;

import com.google.common.collect.Iterators;
import java.util.BitSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import owl.collections.ValuationTree;
import owl.ltl.BooleanConstant;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;

// Design and performance notes: literals are different from unary and binary modal operators,
// due to performance and consistency issues
public interface EquivalenceClassFactory {
  List<String> variables();


  EquivalenceClass of(Formula formula);

  default EquivalenceClass getFalse() {
    return of(BooleanConstant.FALSE);
  }

  default EquivalenceClass getTrue() {
    return of(BooleanConstant.TRUE);
  }


  /**
   * Collects all literals used in the bdd and stores the corresponding atomic propositions in
   * the BitSet.
   */
  BitSet atomicPropositions(EquivalenceClass clazz);

  /**
   * Compute the support of the EquivalenceClass.
   *
   * @return All modal operators this equivalence class depends on.
   */
  Set<Formula> modalOperators(EquivalenceClass clazz);

  boolean implies(EquivalenceClass clazz, EquivalenceClass other);


  default EquivalenceClass conjunction(EquivalenceClass clazz, EquivalenceClass other) {
    return conjunction(List.of(clazz, other));
  }

  default EquivalenceClass conjunction(EquivalenceClass... classes) {
    return conjunction(Iterators.forArray(classes));
  }

  default EquivalenceClass conjunction(Collection<EquivalenceClass> classes) {
    return conjunction(classes.iterator());
  }

  EquivalenceClass conjunction(Iterator<EquivalenceClass> classes);


  default EquivalenceClass disjunction(EquivalenceClass clazz, EquivalenceClass other) {
    return disjunction(List.of(clazz, other));
  }

  default EquivalenceClass disjunction(EquivalenceClass... classes) {
    return disjunction(Iterators.forArray(classes));
  }

  default EquivalenceClass disjunction(Collection<EquivalenceClass> classes) {
    return disjunction(classes.iterator());
  }

  EquivalenceClass disjunction(Iterator<EquivalenceClass> classes);


  EquivalenceClass substitute(EquivalenceClass clazz, Function<Formula, Formula> substitution);

  EquivalenceClass temporalStep(EquivalenceClass clazz, BitSet valuation);

  EquivalenceClass temporalStepUnfold(EquivalenceClass clazz, BitSet valuation);

  EquivalenceClass unfold(EquivalenceClass clazz);

  EquivalenceClass unfoldTemporalStep(EquivalenceClass clazz, BitSet valuation);


  String toString(EquivalenceClass clazz);

  <T> ValuationTree<T> temporalStepTree(EquivalenceClass clazz,
    Function<EquivalenceClass, Set<T>> mapper);

  double trueness(EquivalenceClass clazz);
}
