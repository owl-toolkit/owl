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
 * Weak Until.
 */
public final class WOperator extends BinaryModalOperator {

  public WOperator(Formula leftOperand, Formula rightOperand) {
    super(leftOperand, rightOperand);
  }

  /**
   * Construct a LTL-equivalent formula for (leftOperand)W(rightOperand). The method examines the
   * operands and might choose to construct a simpler formula. However, the size of the syntax tree
   * is not increased. In order to syntactically construct (leftOperand)W(rightOperand) use the
   * constructor.
   *
   * @param leftOperand the left operand of the W-operator
   * @param rightOperand the right operand of the W-operator
   * @return a formula equivalent to (leftOperand)W(rightOperand)
   */
  public static Formula of(Formula leftOperand, Formula rightOperand) {
    if (leftOperand instanceof BooleanConstant
      || leftOperand instanceof GOperator
      || leftOperand.equals(rightOperand)
      || rightOperand.equals(BooleanConstant.TRUE)) {
      return Disjunction.of(leftOperand, rightOperand);
    }

    if (rightOperand.equals(BooleanConstant.FALSE)) {
      return GOperator.of(leftOperand);
    }

    return new WOperator(leftOperand, rightOperand);
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
    return 'W';
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
    return MOperator.of(left.not(), right.not());
  }

  @Override
  public Formula unfold() {
    return Disjunction.of(right.unfold(), Conjunction.of(left.unfold(), this));
  }

  @Override
  public Formula unfoldTemporalStep(BitSet valuation) {
    return Disjunction.of(right.unfoldTemporalStep(valuation),
      Conjunction.of(left.unfoldTemporalStep(valuation), this));
  }
}
