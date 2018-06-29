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

package owl.ltl;

import java.util.BitSet;
import java.util.function.Predicate;
import owl.ltl.visitors.BinaryVisitor;
import owl.ltl.visitors.IntVisitor;
import owl.ltl.visitors.Visitor;

public abstract class Formula {

  static final Formula[] EMPTY_FORMULA_ARRAY = new Formula[0];

  private final int hashCode;

  Formula(int hashCode) {
    this.hashCode = hashCode;
  }

  public abstract int accept(IntVisitor visitor);

  public abstract <R> R accept(Visitor<R> visitor);

  public abstract <R, P> R accept(BinaryVisitor<P, R> visitor, P parameter);

  public abstract boolean allMatch(Predicate<Formula> predicate);

  public abstract boolean anyMatch(Predicate<Formula> predicate);

  // Temporal Properties of an LTL Formula
  public abstract boolean isPureEventual();

  public abstract boolean isPureUniversal();

  public abstract boolean isSuspendable();

  public abstract Formula nnf();

  /**
   * Syntactically negate this formula.
   *
   * <p>If this formula is in NNF, the returned negation will also be in NNF.</p>
   *
   * @return the negation of this formula.
   */
  public abstract Formula not();

  /**
   * Do a single temporal step. This means that one layer of X-operators is removed and literals are
   * replaced by their valuations.
   */
  public abstract Formula temporalStep(BitSet valuation);

  /**
   * Short-cut operation to avoid intermediate construction of formula ASTs.
   */
  public abstract Formula temporalStepUnfold(BitSet valuation);

  public abstract Formula unfold();

  /**
   * Short-cut operation to avoid intermediate construction of formula ASTs.
   */
  public abstract Formula unfoldTemporalStep(BitSet valuation);

  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || !getClass().equals(o.getClass())) {
      return false;
    }

    Formula other = (Formula) o;
    return other.hashCode == hashCode && deepEquals(other);
  }

  protected abstract boolean deepEquals(Formula other);

  @Override
  public final int hashCode() {
    return hashCode;
  }
}
