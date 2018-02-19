/*
 * Copyright (C) 2016  (See AUTHORS)
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import owl.ltl.BooleanConstant;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;

public interface EquivalenceClassFactory {
  ImmutableList<String> variables();


  EquivalenceClass of(Formula formula);

  default EquivalenceClass getFalse() {
    return of(BooleanConstant.FALSE);
  }

  default EquivalenceClass getTrue() {
    return of(BooleanConstant.TRUE);
  }


  BitSet getAtoms(EquivalenceClass clazz);

  Set<Formula> getSupport(EquivalenceClass clazz);

  default Set<Formula> getSupport(EquivalenceClass clazz, Predicate<Formula> predicate) {
    return Sets.filter(getSupport(clazz), predicate::test);
  }

  boolean testSupport(EquivalenceClass clazz, Predicate<Formula> predicate);

  boolean implies(EquivalenceClass clazz, EquivalenceClass other);


  default EquivalenceClass conjunction(EquivalenceClass clazz, EquivalenceClass other) {
    return conjunction(List.of(clazz, other));
  }

  default EquivalenceClass conjunction(EquivalenceClass... classes) {
    return conjunction(Iterators.forArray(classes));
  }

  default EquivalenceClass conjunction(Iterable<EquivalenceClass> classes) {
    return conjunction(classes.iterator());
  }

  EquivalenceClass conjunction(Iterator<EquivalenceClass> classes);


  default EquivalenceClass disjunction(EquivalenceClass clazz, EquivalenceClass other) {
    return disjunction(List.of(clazz, other));
  }

  default EquivalenceClass disjunction(EquivalenceClass... classes) {
    return disjunction(Iterators.forArray(classes));
  }

  default EquivalenceClass disjunction(Iterable<EquivalenceClass> classes) {
    return disjunction(classes.iterator());
  }

  EquivalenceClass disjunction(Iterator<EquivalenceClass> classes);


  EquivalenceClass exists(EquivalenceClass clazz, Predicate<Formula> predicate);

  EquivalenceClass substitute(EquivalenceClass clazz,
    Function<? super Formula, ? extends Formula> substitution);


  EquivalenceClass temporalStep(EquivalenceClass clazz, BitSet valuation);

  EquivalenceClass temporalStepUnfold(EquivalenceClass clazz, BitSet valuation);

  EquivalenceClass unfold(EquivalenceClass clazz);

  EquivalenceClass unfoldTemporalStep(EquivalenceClass clazz, BitSet valuation);


  String toString(EquivalenceClass clazz);
}
