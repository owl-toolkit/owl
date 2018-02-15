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
import java.util.function.Predicate;
import javax.annotation.Nullable;
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
  public final Formula getRepresentative() {
    return representative;
  }

  public final EquivalenceClassFactory getFactory() {
    return factory;
  }


  public final boolean isFalse() {
    return equals(factory.getFalse());
  }

  public final boolean isTrue() {
    return equals(factory.getTrue());
  }


  /**
   * Collects all literals used in the bdd and stores the corresponding atoms in the BitSet.
   */
  public final BitSet getAtoms() {
    return factory.getAtoms(this);
  }

  /**
   * Compute the support of the EquivalenceClass.
   *
   * @return All literals and modal operators this equivalence class depends on.
   */
  public final Set<Formula> getSupport() {
    return factory.getSupport(this);
  }

  public final Set<Formula> getSupport(Predicate<Formula> predicate) {
    return factory.getSupport(this, predicate);
  }

  public final boolean testSupport(Predicate<Formula> predicate) {
    return factory.testSupport(this, predicate);
  }

  public final boolean implies(EquivalenceClass other) {
    return factory.implies(this, other);
  }


  public final EquivalenceClass and(EquivalenceClass other) {
    return factory.conjunction(this, other);
  }

  public final EquivalenceClass or(EquivalenceClass other) {
    return factory.disjunction(this, other);
  }

  public final EquivalenceClass exists(Predicate<Formula> predicate) {
    return factory.exists(this, predicate);
  }

  public final EquivalenceClass substitute(
    Function<? super Formula, ? extends Formula> substitution) {
    return factory.substitute(this, substitution);
  }

  public final EquivalenceClass temporalStep(BitSet valuation) {
    return factory.temporalStep(this, valuation);
  }

  public final EquivalenceClass temporalStepUnfold(BitSet valuation) {
    return factory.temporalStepUnfold(this, valuation);
  }

  public final EquivalenceClass unfold() {
    return factory.unfold(this);
  }

  public final EquivalenceClass unfoldTemporalStep(BitSet valuation) {
    return factory.unfoldTemporalStep(this, valuation);
  }

  @Override
  public final String toString() {
    return factory.toString(this);
  }
}
