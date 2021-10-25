/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
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

import owl.ltl.visitors.BinaryVisitor;
import owl.ltl.visitors.IntVisitor;
import owl.ltl.visitors.Visitor;

/**
 * Weak Until.
 */
public final class WOperator extends Formula.BinaryTemporalOperator
  implements Fixpoint.GreatestFixpoint {

  public WOperator(Formula leftOperand, Formula rightOperand) {
    super(WOperator.class, leftOperand, rightOperand);
  }

  /**
   * Construct a LTL-equivalent formula for (leftOperand)W(rightOperand). The method examines the
   * operands and might choose to construct a simpler formula. However, the size of the syntax tree
   * is not increased. In order to syntactically construct (leftOperand)W(rightOperand) use the
   * constructor.
   *
   * @param leftOperand The left operand of the W-operator
   * @param rightOperand The right operand of the W-operator
   *
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

    if (rightOperand instanceof WOperator && leftOperand.equals(
      ((WOperator) rightOperand).leftOperand())) {
      return rightOperand;
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
  public String operatorSymbol() {
    return "W";
  }

  @Override
  public Formula nnf() {
    return WOperator.of(leftOperand().nnf(), rightOperand().nnf());
  }

  @Override
  public Formula not() {
    return MOperator.of(leftOperand().not(), rightOperand().not());
  }

  @Override
  public Formula unfold() {
    return Disjunction.of(rightOperand().unfold(), Conjunction.of(leftOperand().unfold(), this));
  }
}
