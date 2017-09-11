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

import static com.google.common.base.Preconditions.checkArgument;

import java.util.BitSet;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nonnegative;
import owl.ltl.visitors.BinaryVisitor;
import owl.ltl.visitors.IntVisitor;
import owl.ltl.visitors.Visitor;
import owl.util.ImmutableObject;

public final class Literal extends ImmutableObject implements Formula {
  private final int atom;

  public Literal(@Nonnegative int letter) {
    this(letter, false);
  }

  public Literal(@Nonnegative int letter, boolean negate) {
    checkArgument(letter >= 0);
    this.atom = negate ? -(letter + 1) : letter + 1;
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
    assert o instanceof Literal;
    Literal literal = (Literal) o;
    return atom == literal.atom;
  }

  public int getAtom() {
    return Math.abs(atom) - 1;
  }

  @Override
  protected int hashCodeOnce() {
    return 17 * atom + 5;
  }

  public boolean isNegated() {
    return atom < 0;
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
  public Literal not() {
    return new Literal(getAtom(), !isNegated());
  }

  @Override
  public Formula temporalStep(BitSet valuation) {
    return BooleanConstant.get(valuation.get(getAtom()) ^ isNegated());
  }

  @Override
  public Formula temporalStepUnfold(BitSet valuation) {
    return temporalStep(valuation);
  }

  @Override
  public String toString() {
    return (isNegated() ? "!p" : "p") + getAtom();
  }

  @Override
  public String toString(List<String> atomMapping, boolean fullyParenthesized) {
    if (atomMapping.size() > getAtom()) {
      return (isNegated() ? "!" : "") + atomMapping.get(getAtom());
    }

    return toString();
  }

  @Override
  public Formula unfold() {
    return this;
  }

  @Override
  public Formula unfoldTemporalStep(BitSet valuation) {
    return temporalStep(valuation);
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
