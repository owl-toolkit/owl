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
 * Weak Until.
 */
public final class WOperator extends BinaryModalOperator {

  public WOperator(Formula left, Formula right) {
    super(left, right);
  }

  public static Formula create(Formula left, Formula right) {
    if (left == BooleanConstant.TRUE || right == BooleanConstant.TRUE) {
      return BooleanConstant.TRUE;
    }

    if (left == BooleanConstant.FALSE) {
      return right;
    }

    if (left.equals(right)) {
      return left;
    }

    if (right == BooleanConstant.FALSE) {
      return GOperator.create(left);
    }

    if (left instanceof GOperator) {
      return Disjunction.create(left, right);
    }

    return new WOperator(left, right);
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
    return 'W';
  }

  @Override
  protected int hashCodeOnce() {
    return Objects.hash(WOperator.class, left, right);
  }

  @Override
  public boolean isPureEventual() {
    return false;
  }

  @Override
  public boolean isPureUniversal() {
    return false;
  }

  @Override
  public boolean isSuspendable() {
    return false;
  }

  @Override
  public MOperator not() {
    return new MOperator(left.not(), right.not());
  }

  @Override
  public Formula unfold() {
    return new Disjunction(right.unfold(), new Conjunction(left.unfold(), this));
  }

  @Override
  public Formula unfoldTemporalStep(BitSet valuation) {
    return Disjunction.create(right.unfoldTemporalStep(valuation),
      Conjunction.create(left.unfoldTemporalStep(valuation), this));
  }

}
