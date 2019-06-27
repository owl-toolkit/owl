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

import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import owl.ltl.visitors.BinaryVisitor;
import owl.ltl.visitors.IntVisitor;
import owl.ltl.visitors.Visitor;

/**
 * Biconditional.
 */
public final class Biconditional extends Formula.LogicalOperator {
  public final Formula left;
  public final Formula right;

  public Biconditional(Formula leftOperand, Formula rightOperand) {
    super(Objects.hash(Biconditional.class, leftOperand, rightOperand));
    this.left = leftOperand;
    this.right = rightOperand;
  }

  /**
   * Construct a LTL-equivalent formula for (leftOperand)&lt;-&gt;(rightOperand). The method
   * examines the operands and might choose to construct a simpler formula. However, the size of the
   * syntax tree is not increased. In order to syntactically construct
   * (leftOperand)&lt;-&gt;(rightOperand) use the constructor.
   *
   * @param leftOperand The left operand of the biconditional
   * @param rightOperand The right operand of the biconditional
   *
   * @return a formula equivalent to (leftOperand)&lt;-&gt;(rightOperand)
   */
  public static Formula of(Formula leftOperand, Formula rightOperand) {
    if (leftOperand.equals(BooleanConstant.TRUE)) {
      return rightOperand;
    }

    if (leftOperand.equals(BooleanConstant.FALSE)) {
      return rightOperand.not();
    }

    if (rightOperand.equals(BooleanConstant.TRUE)) {
      return leftOperand;
    }

    if (rightOperand.equals(BooleanConstant.FALSE)) {
      return leftOperand.not();
    }

    if (leftOperand.equals(rightOperand)) {
      return BooleanConstant.TRUE;
    }

    if (leftOperand.equals(rightOperand.not())) {
      return BooleanConstant.FALSE;
    }

    return new Biconditional(leftOperand, rightOperand);
  }

  @Override
  public int accept(IntVisitor visitor) {
    return visitor.visit(this);
  }

  @Override
  public <R> R accept(Visitor<R> visitor) {
    return visitor.visit(this);
  }

  @Override
  public <R, P> R accept(BinaryVisitor<P, R> visitor, P parameter) {
    return visitor.visit(this, parameter);
  }

  @Override
  public Set<Formula> children() {
    return Set.of(left, right);
  }

  @Override
  public boolean isPureEventual() {
    return false;
  }

  @Override
  public boolean isPureUniversal() {
    return false;
  }

  @Override
  public Formula not() {
    return Biconditional.of(left.not(), right);
  }

  @Override
  public Formula nnf() {
    Formula nnfLeft = left.nnf();
    Formula nnfRight = right.nnf();

    return Disjunction.of(
      Conjunction.of(nnfLeft, nnfRight),
      Conjunction.of(nnfLeft.not(), nnfRight.not()));
  }

  @Override
  public Formula substitute(Function<? super TemporalOperator, ? extends Formula> substitution) {
    return Biconditional.of(left.substitute(substitution), right.substitute(substitution));
  }

  @Override
  public String toString() {
    return "(" + left + " <-> " + right + ')';
  }

  @Override
  protected int compareToImpl(Formula o) {
    Biconditional that = (Biconditional) o;
    int comparison = left.compareTo(that.left);
    return comparison == 0 ? right.compareTo(that.right) : comparison;
  }

  @Override
  protected boolean equalsImpl(Formula o) {
    Biconditional that = (Biconditional) o;
    return left.equals(that.left) && right.equals(that.right);
  }
}
