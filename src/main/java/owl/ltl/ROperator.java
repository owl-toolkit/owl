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
 * Weak Release.
 */
public final class ROperator extends BinaryModalOperator {

  public ROperator(Formula left, Formula right) {
    super(left, right);
  }

  public static Formula create(Formula left, Formula right) {
    if (left == BooleanConstant.TRUE || right instanceof BooleanConstant) {
      return right;
    }

    if (left.equals(right)) {
      return left;
    }

    if (left == BooleanConstant.FALSE) {
      return GOperator.create(right);
    }

    return new ROperator(left, right);
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
  public char getOperator() {
    return 'R';
  }

  @Override
  protected int hashCodeOnce() {
    return Objects.hash(ROperator.class, left, right);
  }

  @Override
  public boolean isPureEventual() {
    return left.isPureEventual() && right.isPureEventual();
  }

  @Override
  public boolean isPureUniversal() {
    return right.isPureUniversal();
  }

  @Override
  public boolean isSuspendable() {
    return right.isSuspendable();
  }

  @Override
  public UOperator not() {
    return new UOperator(left.not(), right.not());
  }

  @Override
  public Formula unfold() {
    return new Conjunction(right.unfold(), new Disjunction(left.unfold(), this));
  }

  @Override
  public Formula unfoldTemporalStep(BitSet valuation) {
    return Conjunction.create(right.unfoldTemporalStep(valuation),
      Disjunction.create(left.unfoldTemporalStep(valuation), this));
  }

}
