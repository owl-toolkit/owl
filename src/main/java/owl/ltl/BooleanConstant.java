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
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import owl.ltl.visitors.BinaryVisitor;
import owl.ltl.visitors.IntVisitor;
import owl.ltl.visitors.Visitor;
import owl.util.ImmutableObject;

public final class BooleanConstant extends ImmutableObject implements Formula {
  public static final BooleanConstant FALSE = new BooleanConstant(false);
  public static final BooleanConstant TRUE = new BooleanConstant(true);
  public final boolean value;

  private BooleanConstant(boolean value) {
    this.value = value;
  }

  public static BooleanConstant get(boolean value) {
    return value ? TRUE : FALSE;
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
  protected boolean equals2(ImmutableObject o) {
    BooleanConstant that = (BooleanConstant) o;
    return value == that.value;
  }

  @Override
  protected int hashCodeOnce() {
    return Boolean.hashCode(value);
  }

  @Override
  public boolean isPureEventual() {
    return true;
  }

  @Override
  public boolean isPureUniversal() {
    return true;
  }

  @Override
  public boolean isSuspendable() {
    return true;
  }

  @Nonnull
  @Override
  public BooleanConstant not() {
    return value ? FALSE : TRUE;
  }

  @Override
  public Formula temporalStep(BitSet valuation) {
    return this;
  }

  @Override
  public Formula temporalStepUnfold(BitSet valuation) {
    return this;
  }

  @Override
  public String toString() {
    return value ? "true" : "false";
  }

  @Override
  public String toString(List<String> variables) {
    return toString();
  }

  @Override
  public Formula unfold() {
    return this;
  }

  @Override
  public Formula unfoldTemporalStep(BitSet valuation) {
    return this;
  }

  @Override
  public boolean allMatch(Predicate<Formula> predicate) {
    return predicate.test(this);
  }

  @Override
  public boolean anyMatch(Predicate<Formula> predicate) {
    return predicate.test(this);
  }
}
