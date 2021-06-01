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

import com.google.common.base.Preconditions;
import owl.ltl.visitors.BinaryVisitor;
import owl.ltl.visitors.IntVisitor;
import owl.ltl.visitors.Visitor;

/**
 * Next.
 */
public final class XOperator extends Formula.UnaryTemporalOperator {

  public XOperator(Formula operand) {
    super(XOperator.class, operand);
  }

  /**
   * Construct a LTL-equivalent formula for X(operand). The method examines the operand and might
   * choose to construct a simpler formula. However, the size of the syntax tree is not increased.
   * In order to syntactically construct X(operand) use the constructor.
   *
   * @param operand The operand of the X-operator
   *
   * @return a formula equivalent to X(operand)
   */
  public static Formula of(Formula operand) {
    return of(operand, 1);
  }

  /**
   * Construct a LTL-equivalent formula for X^n(operand). The method examines the operand and might
   * choose to construct a simpler formula. However, the size of the syntax tree is not increased.
   * In order to syntactically construct X^n(operand) use the constructor.
   *
   * @param operand The operand of the X-operator
   * @param n The number of X-operators to add
   *
   * @return a formula equivalent to X^n(operand)
   */
  public static Formula of(Formula operand, int n) {
    Preconditions.checkArgument(n >= 0, "n must be non-negative.");

    if (operand instanceof BooleanConstant) {
      return operand;
    }

    Formula formula = operand;

    for (int i = 0; i < n; i++) {
      formula = new XOperator(formula);
    }

    return formula;
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
    return "X";
  }

  @Override
  public boolean isPureEventual() {
    return operand().isPureEventual();
  }

  @Override
  public boolean isPureUniversal() {
    return operand().isPureUniversal();
  }

  @Override
  public Formula nnf() {
    return XOperator.of(operand().nnf());
  }

  @Override
  public Formula not() {
    return XOperator.of(operand().not());
  }

  @Override
  public Formula unfold() {
    return this;
  }
}
