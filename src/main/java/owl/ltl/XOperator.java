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
import java.util.Objects;
import owl.ltl.visitors.BinaryVisitor;
import owl.ltl.visitors.IntVisitor;
import owl.ltl.visitors.Visitor;

/**
 * Next.
 */
public final class XOperator extends UnaryModalOperator {

  public XOperator(Formula f) {
    super(f);
  }

  public static Formula create(Formula operand) {
    return create(operand, 1);
  }

  public static Formula create(Formula operand, int n) {
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
  public String getOperator() {
    return "X";
  }

  @Override
  protected int hashCodeOnce() {
    return Objects.hash(XOperator.class, operand);
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
  public boolean isSuspendable() {
    return operand.isSuspendable();
  }

  @Override
  public Formula not() {
    return new XOperator(operand.not());
  }

  @Override
  public Formula temporalStep(BitSet valuation) {
    return operand;
  }

  @Override
  public Formula temporalStepUnfold(BitSet valuation) {
    return operand.unfold();
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
