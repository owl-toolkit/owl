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

package ltl;

import java.util.BitSet;
import java.util.Objects;
import ltl.visitors.BinaryVisitor;
import ltl.visitors.IntVisitor;
import ltl.visitors.Visitor;

/**
 * Globally.
 */
public class GOperator extends UnaryModalOperator {

  public GOperator(Formula f) {
    super(f);
  }

  public static Formula create(Formula operand) {
    if (operand instanceof BooleanConstant) {
      return operand;
    }

    if (operand instanceof GOperator) {
      return operand;
    }

    if (operand instanceof ROperator) {
      return create(((ROperator) operand).right);
    }

    if (operand instanceof Conjunction) {
      return Conjunction.create(((Conjunction) operand).children.stream().map(GOperator::create));
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
  public <A, B> A accept(BinaryVisitor<A, B> v, B extra) {
    return v.visit(this, extra);
  }

  @Override
  public String getOperator() {
    return "G";
  }

  @Override
  protected int hashCodeOnce() {
    return Objects.hash(GOperator.class, operand);
  }

  @Override
  public boolean isPureEventual() {
    return operand.isPureEventual();
  }

  @Override
  public boolean isPureUniversal() {
    return true;
  }

  @Override
  public boolean isSuspendable() {
    return operand.isPureEventual() || operand.isSuspendable();
  }

  @Override
  public UnaryModalOperator not() {
    return new FOperator(operand.not());
  }

  @Override
  public Formula unfold() {
    return new Conjunction(operand.unfold(), this);
  }

  @Override
  public Formula unfoldTemporalStep(BitSet valuation) {
    return Conjunction.create(operand.unfoldTemporalStep(valuation), this);
  }
}
