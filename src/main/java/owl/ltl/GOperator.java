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

import owl.ltl.visitors.BinaryVisitor;
import owl.ltl.visitors.IntVisitor;
import owl.ltl.visitors.Visitor;
import owl.util.annotation.CEntryPoint;

/**
 * Globally.
 */
public final class GOperator extends Formula.UnaryTemporalOperator {

  public GOperator(Formula operand) {
    super(GOperator.class, operand);
  }

  /**
   * Construct a LTL-equivalent formula for G(operand). The method examines the operand and might
   * choose to construct a simpler formula. However, the size of the syntax tree is not increased.
   * In order to syntactically construct G(operand) use the constructor.
   *
   * @param operand The operand of the G-operator
   *
   * @return a formula equivalent to G(operand)
   */
  @CEntryPoint
  public static Formula of(Formula operand) {
    if (operand instanceof BooleanConstant
      || operand instanceof GOperator
      || (operand instanceof FOperator && ((FOperator) operand).operand() instanceof GOperator)) {
      return operand;
    }

    if (operand instanceof Conjunction) {
      return Conjunction.of(((Conjunction) operand).map(GOperator::of));
    }

    if (operand instanceof MOperator) {
      MOperator mOperator = (MOperator) operand;
      return Conjunction
        .of(of(mOperator.rightOperand()), of(FOperator.of(mOperator.leftOperand())));
    }

    if (operand instanceof ROperator) {
      return of(((ROperator) operand).rightOperand());
    }

    if (operand instanceof WOperator) {
      WOperator wOperator = (WOperator) operand;
      return of(Disjunction.of(wOperator.leftOperand(), wOperator.rightOperand()));
    }

    return new GOperator(operand);
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
    return "G";
  }

  @Override
  public boolean isPureEventual() {
    return operand().isPureEventual();
  }

  @Override
  public boolean isPureUniversal() {
    return true;
  }

  @Override
  public Formula nnf() {
    if (operand() instanceof Biconditional) {
      var left = ((Biconditional) operand()).leftOperand().nnf();
      var right = ((Biconditional) operand()).rightOperand().nnf();

      return Conjunction.of(
        GOperator.of(Disjunction.of(left.not(), right)),
        GOperator.of(Disjunction.of(left, right.not())));
    }

    return GOperator.of(operand().nnf());
  }

  @Override
  public Formula not() {
    return FOperator.of(operand().not());
  }

  @Override
  public Formula unfold() {
    return Conjunction.of(operand().unfold(), this);
  }
}
