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
import owl.ltl.visitors.BinaryVisitor;
import owl.ltl.visitors.IntVisitor;
import owl.ltl.visitors.Visitor;

/**
 * Weak Release.
 */
public final class ROperator extends BinaryModalOperator {

  public ROperator(Formula leftOperand, Formula rightOperand) {
    super(leftOperand, rightOperand);
  }

  /**
   * Construct a LTL-equivalent formula for (leftOperand)R(rightOperand). The method examines the
   * operands and might choose to construct a simpler formula. However, the size of the syntax tree
   * is not increased. In order to syntactically construct (leftOperand)R(rightOperand) use the
   * constructor.
   *
   * @param leftOperand the left operand of the R-operator
   * @param rightOperand the right operand of the R-operator
   * @return a formula equivalent to (leftOperand)R(rightOperand)
   */
  public static Formula of(Formula leftOperand, Formula rightOperand) {
    if (rightOperand instanceof BooleanConstant
      || rightOperand instanceof GOperator
      || leftOperand.equals(rightOperand)
      || leftOperand.equals(BooleanConstant.TRUE)) {
      return rightOperand;
    }

    if (leftOperand.equals(BooleanConstant.FALSE)) {
      return GOperator.of(rightOperand);
    }

    return new ROperator(leftOperand, rightOperand);
  }

  @Override
  public int accept(IntVisitor v) {
    return v.visit(this);
  }

  @Override
  public <R> R accept(Visitor<R> v) {
    return v.visit(this);
  }

  @Override
  public <A, B> A accept(BinaryVisitor<B, A> v, B parameter) {
    return v.visit(this, parameter);
  }

  @Override
  public char getOperator() {
    return 'R';
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
  public Formula nnf() {
    return ROperator.of(left.nnf(), right.nnf());
  }

  @Override
  public Formula not() {
    return UOperator.of(left.not(), right.not());
  }

  @Override
  public Formula unfold() {
    return Conjunction.of(right.unfold(), Disjunction.of(left.unfold(), this));
  }

  @Override
  public Formula unfoldTemporalStep(BitSet valuation) {
    return Conjunction.of(right.unfoldTemporalStep(valuation),
      Disjunction.of(left.unfoldTemporalStep(valuation), this));
  }
}
