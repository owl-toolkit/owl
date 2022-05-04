/*
 * Copyright (C) 2016, 2022  (Salomon Sickert, Tobias Meggendorfer)
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

import static java.util.Objects.checkIndex;

import com.google.common.base.Preconditions;
import java.util.BitSet;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;
import javax.annotation.Nonnegative;
import owl.ltl.visitors.BinaryVisitor;
import owl.ltl.visitors.IntVisitor;
import owl.ltl.visitors.Visitor;

// TODO: fix naming.
public final class Literal extends Formula.PropositionalOperator {

  private static final List<Literal> CACHE = IntStream.range(0, 128)
      .mapToObj(Literal::new)
      .toList();
  private final int index;
  private final Literal negation;


  private Literal(Literal other) {
    super(Literal.class, List.of(), Integer.hashCode(-other.index));
    this.index = -other.index;
    this.negation = other;

    assert getAtom() == other.getAtom();
    assert isNegated() ^ other.isNegated();
  }

  // TODO: do not leak 'this'
  private Literal(@Nonnegative int index) {
    super(Literal.class, List.of(), Integer.hashCode(index + 1));
    checkIndex(index, Integer.MAX_VALUE);
    this.index = index + 1;
    this.negation = new Literal(this);

    assert getAtom() == negation.getAtom();
    assert isNegated() ^ negation.isNegated();
  }


  public static Literal of(@Nonnegative int atomicProposition) {
    return of(atomicProposition, false);
  }

  public static Literal of(@Nonnegative int atomicProposition, boolean negate) {
    Preconditions.checkArgument(atomicProposition >= 0);

    var literal = atomicProposition >= CACHE.size()
        ? new Literal(atomicProposition)
        : CACHE.get(atomicProposition);
    return negate ? literal.negation : literal;
  }

  @Override
  public Formula temporalStep(BitSet valuation) {
    return BooleanConstant.of(valuation.get(getAtom()) ^ isNegated());
  }

  @Override
  public Formula substitute(Function<? super TemporalOperator, ? extends Formula> substitution) {
    return this;
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

  public int getAtom() {
    return Math.abs(index) - 1;
  }

  public boolean isNegated() {
    return index < 0;
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
  public Formula nnf() {
    return this;
  }

  @Override
  public Literal not() {
    return negation;
  }

  @Override
  public String toString() {
    return isNegated() ? "!" + negation : String.format("p%d", index - 1);
  }

  @Override
  protected int compareValue(Formula o) {
    Literal that = (Literal) o;
    int comparison = Integer.compare(getAtom(), that.getAtom());

    if (comparison != 0) {
      return comparison;
    }

    return Boolean.compare(isNegated(), that.isNegated());
  }

  @Override
  protected boolean equalsValue(Formula o) {
    Literal that = (Literal) o;
    return index == that.index;
  }
}
