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
import java.util.Objects;
import java.util.function.Predicate;

public abstract class BinaryModalOperator extends AbstractFormula {
  public final Formula left;
  public final Formula right;

  BinaryModalOperator(Formula left, Formula right) {
    this.left = left;
    this.right = right;
  }

  @Override
  public boolean allMatch(Predicate<Formula> predicate) {
    return predicate.test(this) && left.allMatch(predicate) && right.allMatch(predicate);
  }

  @Override
  public boolean anyMatch(Predicate<Formula> predicate) {
    return predicate.test(this) || left.anyMatch(predicate) || right.anyMatch(predicate);
  }

  @Override
  protected boolean equals2(AbstractFormula o) {
    BinaryModalOperator that = (BinaryModalOperator) o;
    return Objects.equals(left, that.left) && Objects.equals(right, that.right);
  }

  public abstract char getOperator();

  @Override
  public Formula temporalStep(BitSet valuation) {
    return this;
  }

  @Override
  public Formula temporalStepUnfold(BitSet valuation) {
    return unfold();
  }

  @Override
  public String toString() {
    return String.format("(%s%s%s)", left, getOperator(), right);
  }
}
