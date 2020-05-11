/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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

package owl.ltl;

import java.util.BitSet;
import java.util.Set;
import java.util.function.Function;
import owl.collections.ValuationTree;
import owl.factories.EquivalenceClassFactory;
import owl.ltl.visitors.Visitor;

/**
 * EquivalenceClass interface. The general contract of this interface is: If two implementing
 * objects were created from different factories, implies and equals have to return {@code false}.
 */
public interface EquivalenceClass extends LtlLanguageExpressible {
  Formula representative();

  EquivalenceClassFactory factory();

  boolean isFalse();

  boolean isTrue();

  /**
   * See {@link Formula#atomicPropositions(boolean)}.
   */
  default BitSet atomicPropositions() {
    return atomicPropositions(false);
  }

  /**
   * Collects all literals used in the bdd and stores the corresponding atomic propositions in
   * the BitSet. See also {@link Formula#atomicPropositions(boolean)}.
   */
  BitSet atomicPropositions(boolean includeNested);

  Set<Formula.TemporalOperator> temporalOperators();

  boolean implies(EquivalenceClass other);

  EquivalenceClass and(EquivalenceClass other);

  EquivalenceClass or(EquivalenceClass other);

  /**
   * See {@link Formula#substitute(Function)}.
   *
   * @param substitution
   *   The substitution function. It is only called on modal operators.
   */
  EquivalenceClass substitute(
    Function<? super Formula.TemporalOperator, ? extends Formula> substitution);

  EquivalenceClass accept(Visitor<? extends Formula> visitor);

  /**
   * See {@link Formula#temporalStep(BitSet)}.
   *
   * @param valuation The assignment for the atomic propositions.
   */
  EquivalenceClass temporalStep(BitSet valuation);

  default ValuationTree<EquivalenceClass> temporalStepTree() {
    return temporalStepTree(Set::of);
  }

  <T> ValuationTree<T> temporalStepTree(Function<EquivalenceClass, Set<T>> mapper);

  /**
   * See {@link Formula#unfold()}.
   */
  EquivalenceClass unfold();

  double trueness();

  @Override
  default EquivalenceClass language() {
    return this;
  }
}
