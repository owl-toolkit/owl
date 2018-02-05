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

package owl.ltl;

import com.google.common.collect.Sets;
import java.util.BitSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import owl.factories.EquivalenceClassFactory;

/**
 * EquivalenceClass interface. The general contract of this interface is: If two implementing
 * objects were created from different factories, implies and equals have to return {@code false}.
 */
public interface EquivalenceClass {

  EquivalenceClass and(EquivalenceClass equivalenceClass);

  /**
   * Performs the same operation as {@link EquivalenceClass#and}, but also calls free() on the
   * instance.
   */
  default EquivalenceClass andWith(EquivalenceClass equivalenceClass) {
    EquivalenceClass and = and(equivalenceClass);
    free();
    return and;
  }

  EquivalenceClass duplicate();

  EquivalenceClass exists(Predicate<Formula> predicate);

  void free();

  void freeRepresentative();

  /**
   * Collects all literals used in the bdd and stores the corresponding atoms in the BitSet.
   */
  BitSet getAtoms();

  EquivalenceClassFactory getFactory();

  @Nullable
  Formula getRepresentative();

  /**
   * Compute the support of the EquivalenceClass.
   *
   * @return All literals and modal operators this equivalence class depends on.
   */
  Set<Formula> getSupport();

  default Set<Formula> getSupport(Predicate<Formula> predicate) {
    return Sets.filter(getSupport(), predicate::test);
  }

  boolean implies(EquivalenceClass equivalenceClass);

  boolean isFalse();

  boolean isTrue();

  EquivalenceClass or(EquivalenceClass equivalenceClass);

  /**
   * Performs the same operation as {@link EquivalenceClass#or}, but also calls free() on the
   * instance.
   */
  default EquivalenceClass orWith(EquivalenceClass equivalenceClass) {
    EquivalenceClass or = or(equivalenceClass);
    free();
    return or;
  }

  EquivalenceClass substitute(Function<? super Formula, ? extends Formula> substitution);

  EquivalenceClass temporalStep(BitSet valuation);

  EquivalenceClass temporalStepUnfold(BitSet valuation);

  boolean testSupport(Predicate<Formula> predicate);

  EquivalenceClass unfold();

  EquivalenceClass unfoldTemporalStep(BitSet valuation);
}
