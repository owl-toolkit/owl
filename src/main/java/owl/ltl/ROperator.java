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
 * Weak Release.
 */
public final class ROperator extends Formula.BinaryTemporalOperator
  implements Fixpoint.GreatestFixpoint {

  public ROperator(Formula leftOperand, Formula rightOperand) {
    super(ROperator.class, leftOperand, rightOperand);
  }

  /**
   * Construct a LTL-equivalent formula for (leftOperand)R(rightOperand). The method examines the
   * operands and might choose to construct a simpler formula. However, the size of the syntax tree
   * is not increased. In order to syntactically construct (leftOperand)R(rightOperand) use the
   * constructor.
   *
   * @param leftOperand The left operand of the R-operator
   * @param rightOperand The right operand of the R-operator
   *
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

    if (leftOperand instanceof ROperator && rightOperand.equals(
      ((ROperator) leftOperand).rightOperand())) {
      return leftOperand;
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
  public String operatorSymbol() {
    return "R";
  }

  @Override
  public Formula nnf() {
    return ROperator.of(leftOperand().nnf(), rightOperand().nnf());
  }

  @Override
  public Formula not() {
    return UOperator.of(leftOperand().not(), rightOperand().not());
  }

  @Override
  public Formula unfold() {
    return Conjunction.of(rightOperand().unfold(), Disjunction.of(leftOperand().unfold(), this));
  }
}
