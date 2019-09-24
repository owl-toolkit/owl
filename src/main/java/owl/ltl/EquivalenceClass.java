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

import java.util.Arrays;
import java.util.BitSet;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;
import owl.collections.ValuationTree;
import owl.factories.EquivalenceClassFactory;

/**
 * EquivalenceClass interface. The general contract of this interface is: If two implementing
 * objects were created from different factories, implies and equals have to return {@code false}.
 */
public class EquivalenceClass implements LtlLanguageExpressible {
  private final EquivalenceClassFactory factory;
  @Nullable
  private final Formula representative;

  protected EquivalenceClass(EquivalenceClassFactory factory, @Nullable Formula representative) {
    this.factory = factory;
    this.representative = representative;
  }

  @Nullable
  public final Formula representative() {
    return representative;
  }

  public final EquivalenceClassFactory factory() {
    return factory;
  }

  public final boolean isFalse() {
    return equals(factory.getFalse());
  }

  public final boolean isTrue() {
    return equals(factory.getTrue());
  }

  /**
   * See {@link EquivalenceClassFactory#atomicPropositions(EquivalenceClass, boolean)}.
   */
  public final BitSet atomicPropositions() {
    return factory.atomicPropositions(this, false);
  }

  /**
   * See {@link EquivalenceClassFactory#atomicPropositions(EquivalenceClass, boolean)}.
   */
  public final BitSet atomicPropositions(boolean includeNested) {
    return factory.atomicPropositions(this, includeNested);
  }

  // TODO: cache this field.
  /**
   * See {@link EquivalenceClassFactory#modalOperators(EquivalenceClass)}.
   */
  public final Set<Formula.ModalOperator> modalOperators() {
    return factory.modalOperators(this);
  }

  /**
   * See {@link EquivalenceClassFactory#implies(EquivalenceClass, EquivalenceClass)}.
   */
  public final boolean implies(EquivalenceClass other) {
    return factory.implies(this, other);
  }

  /**
   * See {@link EquivalenceClassFactory#conjunction(java.util.Collection)}.
   */
  public final EquivalenceClass and(EquivalenceClass other) {
    return factory.conjunction(Arrays.asList(this, other));
  }

  /**
   * See {@link EquivalenceClassFactory#disjunction(java.util.Collection)}.
   */
  public final EquivalenceClass or(EquivalenceClass other) {
    return factory.disjunction(Arrays.asList(this, other));
  }

  /**
   * See {@link EquivalenceClassFactory#substitute(EquivalenceClass, Function)}.
   *
   * @param substitution The substitution function. It is only called on modal operators.
   */
  public final EquivalenceClass substitute(
    Function<? super Formula.ModalOperator, ? extends Formula> substitution) {
    return factory.substitute(this, substitution);
  }

  /**
   * See {@link EquivalenceClassFactory#temporalStep(EquivalenceClass, BitSet)}.
   *
   * @param valuation The assignment for the atomic propositions.
   */
  public final EquivalenceClass temporalStep(BitSet valuation) {
    return factory.temporalStep(this, valuation);
  }

  public final ValuationTree<EquivalenceClass> temporalStepTree() {
    return temporalStepTree(Set::of);
  }

  public final <T> ValuationTree<T> temporalStepTree(Function<EquivalenceClass, Set<T>> mapper) {
    return factory.temporalStepTree(this, mapper);
  }

  /**
   * See {@link EquivalenceClassFactory#temporalStepUnfold(EquivalenceClass, BitSet)}.
   *
   * @param valuation The assignment for the atomic propositions.
   */
  public final EquivalenceClass temporalStepUnfold(BitSet valuation) {
    return factory.temporalStepUnfold(this, valuation);
  }

  /**
   * See {@link EquivalenceClassFactory#unfold(EquivalenceClass)}.
   */
  public final EquivalenceClass unfold() {
    return factory.unfold(this);
  }

  /**
   * See {@link EquivalenceClassFactory#unfoldTemporalStep(EquivalenceClass, BitSet)}.
   *
   * @param valuation The assignment for the atomic propositions.
   */
  public final EquivalenceClass unfoldTemporalStep(BitSet valuation) {
    return factory.unfoldTemporalStep(this, valuation);
  }

  public final double trueness() {
    return factory.trueness(this);
  }

  @Override
  public EquivalenceClass language() {
    return this;
  }

  @Override
  public final String toString() {
    return factory.toString(this);
  }
}
