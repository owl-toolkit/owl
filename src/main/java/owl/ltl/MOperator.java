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
 * Strong Release.
 */
public final class MOperator extends Formula.BinaryTemporalOperator
  implements Fixpoint.LeastFixpoint {

  public MOperator(Formula leftOperand, Formula rightOperand) {
    super(MOperator.class, leftOperand, rightOperand);
  }

  /**
   * Construct a LTL-equivalent formula for (leftOperand)M(rightOperand). The method examines the
   * operands and might choose to construct a simpler formula. However, the size of the syntax tree
   * is not increased. In order to syntactically construct (leftOperand)M(rightOperand) use the
   * constructor.
   *
   * @param leftOperand The left operand of the M-operator
   * @param rightOperand The right operand of the M-operator
   *
   * @return a formula equivalent to (leftOperand)M(rightOperand)
   */
  @SuppressWarnings({"PMD.CompareObjectsWithEquals", "ReferenceEquality", "ObjectEquality"})
  public static Formula of(Formula leftOperand, Formula rightOperand) {
    if (leftOperand instanceof BooleanConstant
      || leftOperand instanceof FOperator
      || leftOperand.equals(rightOperand)
      || rightOperand.equals(BooleanConstant.FALSE)) {
      return Conjunction.of(leftOperand, rightOperand);
    }

    if (rightOperand == BooleanConstant.TRUE) {
      return FOperator.of(leftOperand);
    }

    if (leftOperand instanceof MOperator && rightOperand.equals(
      ((MOperator) leftOperand).rightOperand())) {
      return leftOperand;
    }

    return new MOperator(leftOperand, rightOperand);
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
    return "M";
  }

  @Override
  public Formula nnf() {
    return MOperator.of(leftOperand().nnf(), rightOperand().nnf());
  }

  @Override
  public Formula not() {
    return WOperator.of(leftOperand().not(), rightOperand().not());
  }

  @Override
  public Formula unfold() {
    return Conjunction.of(rightOperand().unfold(), Disjunction.of(leftOperand().unfold(), this));
  }
}
