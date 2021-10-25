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
 * Strong Until.
 */
public final class UOperator extends Formula.BinaryTemporalOperator
  implements Fixpoint.LeastFixpoint {

  public UOperator(Formula leftOperand, Formula rightOperand) {
    super(UOperator.class, leftOperand, rightOperand);
  }

  /**
   * Construct a LTL-equivalent formula for (leftOperand)U(rightOperand). The method examines the
   * operands and might choose to construct a simpler formula. However, the size of the syntax tree
   * is not increased. In order to syntactically construct (leftOperand)U(rightOperand) use the
   * constructor.
   *
   * @param leftOperand The left operand of the U-operator
   * @param rightOperand The right operand of the U-operator
   *
   * @return a formula equivalent to (leftOperand)U(rightOperand)
   */
  public static Formula of(Formula leftOperand, Formula rightOperand) {
    if (rightOperand instanceof BooleanConstant
      || rightOperand instanceof FOperator
      || leftOperand.equals(rightOperand)
      || leftOperand.equals(BooleanConstant.FALSE)) {
      return rightOperand;
    }

    if (leftOperand.equals(BooleanConstant.TRUE)) {
      return FOperator.of(rightOperand);
    }

    if (rightOperand instanceof UOperator && leftOperand.equals(
      ((UOperator) rightOperand).leftOperand())) {
      return rightOperand;
    }

    return new UOperator(leftOperand, rightOperand);
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
    return "U";
  }

  @Override
  public Formula nnf() {
    return UOperator.of(leftOperand().nnf(), rightOperand().nnf());
  }

  @Override
  public Formula not() {
    return ROperator.of(leftOperand().not(), rightOperand().not());
  }

  @Override
  public Formula unfold() {
    return Disjunction.of(rightOperand().unfold(), Conjunction.of(leftOperand().unfold(), this));
  }
}
