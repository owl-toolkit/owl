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
import owl.ltl.visitors.BinaryVisitor;
import owl.ltl.visitors.IntVisitor;
import owl.ltl.visitors.Visitor;
import owl.util.annotation.CEntryPoint;

/**
 * Yesterday.
 */
public final class ZOperator extends UnaryModalOperator {

  public ZOperator(Formula operand) {
    super(ZOperator.class, operand);
  }

  /**
   * Construct a LTL-equivalent formula for Z(operand). The method examines the operand and might
   * choose to construct a simpler formula. However, the size of the syntax tree is not increased.
   * In order to syntactically construct Z(operand) use the constructor.
   *
   * @param operand The operand of the Z-operator
   *
   * @return a formula equivalent to Z(operand)
   */
  @CEntryPoint
  public static Formula of(Formula operand) {
    if (operand instanceof BooleanConstant) {
      return operand;
    }

    return new ZOperator(operand);
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
    return "Z";
  }

  @Override
  public boolean isPureEventual() {
    return operand.isPureEventual();
  }

  @Override
  public boolean isPureUniversal() {
    return operand.isPureUniversal();
  }

  @Override
  public Formula nnf() {
    return YOperator.of(operand.nnf());
  }

  @Override
  public Formula not() {
    return YOperator.of(operand.not());
  }

  @Override
  public Formula unfold() {
    return this;
  }

  @Override
  public Formula unfoldTemporalStep(BitSet valuation) {
    return temporalStep(valuation);
  }
}
