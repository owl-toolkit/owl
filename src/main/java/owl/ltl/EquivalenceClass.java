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

import java.util.BitSet;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;
import owl.collections.LabelledTree;
import owl.factories.EquivalenceClassFactory;

/**
 * EquivalenceClass interface. The general contract of this interface is: If two implementing
 * objects were created from different factories, implies and equals have to return {@code false}.
 */
public class EquivalenceClass {
  public static final EquivalenceClass[] EMPTY_ARRAY = new EquivalenceClass[0];

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
   * See {@link EquivalenceClassFactory#atomicPropositions(EquivalenceClass)}.
   *
   * @return
   */
  public final BitSet atomicPropositions() {
    return factory.atomicPropositions(this);
  }

  /**
   * See {@link EquivalenceClassFactory#modalOperators(EquivalenceClass)}.
   *
   * @return
   */
  public final Set<Formula> modalOperators() {
    return factory.modalOperators(this);
  }

  /**
   * See {@link EquivalenceClassFactory#implies(EquivalenceClass, EquivalenceClass)}.
   *
   * @param other the conclusion
   * @return
   */
  public final boolean implies(EquivalenceClass other) {
    return factory.implies(this, other);
  }

  /**
   * See {@link EquivalenceClassFactory#conjunction(EquivalenceClass, EquivalenceClass)}.
   *
   * @param other the other class
   * @return
   */
  public final EquivalenceClass and(EquivalenceClass other) {
    return factory.conjunction(this, other);
  }

  /**
   * See {@link EquivalenceClassFactory#disjunction(EquivalenceClass, EquivalenceClass)}.
   *
   * @param other the other class
   * @return
   */
  public final EquivalenceClass or(EquivalenceClass other) {
    return factory.disjunction(this, other);
  }

  /**
   * See {@link EquivalenceClassFactory#substitute(EquivalenceClass, Function)}.
   *
   * @param substitution the substitution function. It is only called on modal operators.
   * @return
   */
  public final EquivalenceClass substitute(Function<Formula, Formula> substitution) {
    return factory.substitute(this, substitution);
  }

  /**
   * See {@link EquivalenceClassFactory#temporalStep(EquivalenceClass, BitSet)}.
   *
   * @param valuation the assignment for the atomic propositions
   * @return
   */
  public final EquivalenceClass temporalStep(BitSet valuation) {
    return factory.temporalStep(this, valuation);
  }

  public final LabelledTree<Integer, EquivalenceClass> temporalStepTree() {
    return factory.temporalStepTree(this);
  }

  /**
   * See {@link EquivalenceClassFactory#temporalStepUnfold(EquivalenceClass, BitSet)}.
   *
   * @param valuation the assignment for the atomic propositions
   * @return
   */
  public final EquivalenceClass temporalStepUnfold(BitSet valuation) {
    return factory.temporalStepUnfold(this, valuation);
  }

  /**
   * See {@link EquivalenceClassFactory#unfold(EquivalenceClass)}.
   *
   * @return
   */
  public final EquivalenceClass unfold() {
    return factory.unfold(this);
  }

  /**
   * See {@link EquivalenceClassFactory#unfoldTemporalStep(EquivalenceClass, BitSet)}.
   *
   * @param valuation the assignment for the atomic propositions
   * @return
   */
  public final EquivalenceClass unfoldTemporalStep(BitSet valuation) {
    return factory.unfoldTemporalStep(this, valuation);
  }

  @Override
  public final String toString() {
    return factory.toString(this);
  }
}
