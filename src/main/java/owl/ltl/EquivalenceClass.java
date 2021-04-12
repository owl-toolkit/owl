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

package owl.ltl;

import java.util.BitSet;
import java.util.Set;
import java.util.function.Function;
import owl.bdd.EquivalenceClassFactory;
import owl.bdd.MtBdd;
import owl.collections.ImmutableBitSet;

/**
 * A propositional equivalence class of an LTL formula.
 *
 * @implSpec If two implementing objects were created by different factories, methods combining or
 *     comparing these objects are allowed to throw exceptions.
 */
public interface EquivalenceClass extends LtlLanguageExpressible {

  /**
   * See {@link Formula#atomicPropositions(boolean)}.
   */
  default ImmutableBitSet atomicPropositions() {
    return atomicPropositions(false);
  }

  /**
   * Collects all literals used in the bdd and stores the corresponding atomic propositions in
   * the BitSet. See also {@link Formula#atomicPropositions(boolean)}.
   */
  ImmutableBitSet atomicPropositions(boolean includeNested);

  Set<Set<Formula>> conjunctiveNormalForm();

  Set<Set<Formula>> disjunctiveNormalForm();

  /**
   * The canonical representative for this equivalence class, which is defined as the formula
   * representation of the {@link EquivalenceClass#conjunctiveNormalForm}.
   *
   * @return The canonical representative.
   */
  default Formula canonicalRepresentativeCnf() {
    return Conjunction.of(conjunctiveNormalForm().stream().map(Disjunction::of));
  }

  /**
   * The canonical representative for this equivalence class, which is defined as the formula
   * representation of the {@link EquivalenceClass#disjunctiveNormalForm}.
   *
   * @return The canonical representative.
   */
  default Formula canonicalRepresentativeDnf() {
    return Disjunction.of(disjunctiveNormalForm().stream().map(Conjunction::of));
  }

  EquivalenceClassFactory factory();

  boolean isFalse();

  boolean isTrue();

  default Set<Formula.TemporalOperator> temporalOperators() {
    return temporalOperators(false);
  }

  Set<Formula.TemporalOperator> temporalOperators(boolean includeNested);

  boolean implies(EquivalenceClass other);

  EquivalenceClass and(EquivalenceClass other);

  EquivalenceClass or(EquivalenceClass other);

  /**
   * See {@link Formula#substitute(Function)}.
   *
   * @param substitution
   *   The substitution function. It is only called on modal / temporal operators.
   */
  EquivalenceClass substitute(
    Function<? super Formula.TemporalOperator, ? extends Formula> substitution);

  /**
   * See {@link Formula#temporalStep(BitSet)}.
   *
   * @param valuation The assignment for the atomic propositions.
   */
  EquivalenceClass temporalStep(BitSet valuation);

  default MtBdd<EquivalenceClass> temporalStepTree() {
    return temporalStepTree(Set::of);
  }

  <T> MtBdd<T> temporalStepTree(Function<EquivalenceClass, Set<T>> mapper);

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
