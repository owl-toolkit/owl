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

import java.util.Objects;
import java.util.Set;

public abstract class BinaryModalOperator extends Formula.ModalOperator {
  public final Formula left;
  public final Formula right;

  BinaryModalOperator(Class<? extends BinaryModalOperator> clazz, Formula leftOperand,
    Formula rightOperand) {
    super(Objects.hash(clazz, leftOperand, rightOperand));
    this.left = leftOperand;
    this.right = rightOperand;
  }

  @Override
  public Set<Formula> children() {
    return Set.of(left, right);
  }

  @Override
  public final boolean isPureEventual() {
    return false;
  }

  @Override
  public final boolean isPureUniversal() {
    return false;
  }

  public abstract String operatorSymbol();

  @Override
  public final String toString() {
    return String.format("(%s%s%s)", left, operatorSymbol(), right);
  }

  @Override
  protected final int compareToImpl(Formula o) {
    assert this.getClass() == o.getClass();
    BinaryModalOperator that = (BinaryModalOperator) o;
    int comparison = left.compareTo(that.left);
    return comparison == 0 ? right.compareTo(that.right) : comparison;
  }

  @Override
  protected final boolean equalsImpl(Formula o) {
    assert this.getClass() == o.getClass();
    BinaryModalOperator that = (BinaryModalOperator) o;
    return left.equals(that.left) && right.equals(that.right);
  }
}
