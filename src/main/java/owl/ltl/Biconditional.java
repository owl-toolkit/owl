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
import java.util.Objects;
import java.util.function.Predicate;
import owl.ltl.visitors.BinaryVisitor;
import owl.ltl.visitors.IntVisitor;
import owl.ltl.visitors.Visitor;

/**
 * Biconditional.
 */
public class Biconditional extends AbstractFormula {
  public final Formula left;
  public final Formula right;

  public Biconditional(Formula leftOperand, Formula rightOperand) {
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
  public boolean allMatch(Predicate<Formula> predicate) {
    return predicate.test(this) && left.allMatch(predicate) && right.allMatch(predicate);
  }

  @Override
  public boolean anyMatch(Predicate<Formula> predicate) {
    return predicate.test(this) || left.anyMatch(predicate) || right.anyMatch(predicate);
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
  public boolean isSuspendable() {
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
  public Formula temporalStep(BitSet valuation) {
    return Biconditional.of(left.temporalStep(valuation), right.temporalStep(valuation));
  }

  @Override
  public Formula temporalStepUnfold(BitSet valuation) {
    return Biconditional.of(left.temporalStepUnfold(valuation),
      right.temporalStepUnfold(valuation));
  }

  @Override
  public Formula unfold() {
    return Biconditional.of(left.unfold(), right.unfold());
  }

  @Override
  public Formula unfoldTemporalStep(BitSet valuation) {
    return Biconditional.of(left.unfoldTemporalStep(valuation),
      right.unfoldTemporalStep(valuation));
  }

  @Override
  protected boolean equals2(AbstractFormula o) {
    assert this.getClass() == o.getClass();
    Biconditional that = (Biconditional) o;
    return Objects.equals(left, that.left) && Objects.equals(right, that.right);
  }

  @Override
  protected int hashCodeOnce() {
    return Objects.hash(Biconditional.class, left, right);
  }

  @Override
  public String toString() {
    return "(" + left + " <-> " + right + ')';
  }
}
