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
 * Finally.
 */
public final class FOperator extends Formula.UnaryTemporalOperator
  implements Fixpoint.LeastFixpoint {

  public FOperator(Formula operand) {
    super(FOperator.class, operand);
  }

  /**
   * Construct a LTL-equivalent formula for F(operand). The method examines the operand and might
   * choose to construct a simpler formula. However, the size of the syntax tree is not increased.
   * In order to syntactically construct F(operand) use the constructor.
   *
   * @param operand The operand of the F-operator
   *
   * @return a formula equivalent to F(operand)
   */
  public static Formula of(Formula operand) {
    if (operand instanceof BooleanConstant
      || operand instanceof FOperator
      || (operand instanceof GOperator && ((GOperator) operand).operand() instanceof FOperator)) {
      return operand;
    }

    if (operand instanceof Disjunction disjunction) {
      return Disjunction.of(disjunction.map(FOperator::of));
    }

    if (operand instanceof MOperator mOperator) {
      return FOperator.of(Conjunction.of(mOperator.leftOperand(), mOperator.rightOperand()));
    }

    if (operand instanceof UOperator uOperator) {
      return of(uOperator.rightOperand());
    }

    if (operand instanceof WOperator wOperator) {
      return Disjunction
        .of(of(GOperator.of(wOperator.leftOperand())), of(wOperator.rightOperand()));
    }

    return new FOperator(operand);
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
    return "F";
  }

  @Override
  public boolean isPureEventual() {
    return true;
  }

  @Override
  public boolean isPureUniversal() {
    return operand().isPureUniversal();
  }

  @Override
  public Formula nnf() {
    if (operand() instanceof Biconditional) {
      var left = ((Biconditional) operand()).leftOperand().nnf();
      var right = ((Biconditional) operand()).rightOperand().nnf();

      return Disjunction.of(
        FOperator.of(Conjunction.of(left, right)),
        FOperator.of(Conjunction.of(left.not(), right.not())));
    }

    return FOperator.of(operand().nnf());
  }

  @Override
  public Formula not() {
    return GOperator.of(operand().not());
  }

  @Override
  public Formula unfold() {
    return Disjunction.of(operand().unfold(), this);
  }
}
