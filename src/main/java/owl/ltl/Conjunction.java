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

import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.stream.Stream;
import owl.ltl.visitors.BinaryVisitor;
import owl.ltl.visitors.IntVisitor;
import owl.ltl.visitors.Visitor;

public final class Conjunction extends PropositionalFormula {

  public Conjunction(Iterable<? extends Formula> conjuncts) {
    super(conjuncts);
  }

  public Conjunction(Formula... conjuncts) {
    super(conjuncts);
  }

  public Conjunction(Stream<? extends Formula> formulaStream) {
    super(formulaStream);
  }

  public static Formula create(Formula left, Formula right) {
    return create(Stream.of(left, right));
  }

  public static Formula create(Formula... formulas) {
    return create(Arrays.asList(formulas));
  }

  public static Formula create(Iterable<? extends Formula> iterable) {
    return create(iterable.iterator());
  }

  public static Formula create(Stream<? extends Formula> stream) {
    return create(stream.iterator());
  }

  public static Formula create(Iterator<? extends Formula> iterator) {
    ImmutableSet.Builder<Formula> builder = ImmutableSet.builder();

    while (iterator.hasNext()) {
      Formula child = iterator.next();
      assert child != null;

      if (child == BooleanConstant.FALSE) {
        return BooleanConstant.FALSE;
      }

      if (child == BooleanConstant.TRUE) {
        continue;
      }

      if (child instanceof Conjunction) {
        builder.addAll(((Conjunction) child).children);
      } else {
        builder.add(child);
      }
    }

    ImmutableSet<Formula> set = builder.build();

    if (set.isEmpty()) {
      return BooleanConstant.TRUE;
    }

    if (set.size() == 1) {
      return set.iterator().next();
    }

    return new Conjunction(set);
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
  protected char getOperator() {
    return '&';
  }

  @Override
  protected int hashCodeOnce() {
    return Objects.hash(Conjunction.class, children);
  }

  @Override
  public Formula not() {
    return new Disjunction(children.stream().map(Formula::not));
  }

  @Override
  public Formula temporalStep(BitSet valuation) {
    return create(children.stream().map(c -> c.temporalStep(valuation)));
  }

  @Override
  public Formula temporalStepUnfold(BitSet valuation) {
    return create(children.stream().map(c -> c.temporalStepUnfold(valuation)));
  }

  @Override
  public Formula unfold() {
    return create(children.stream().map(Formula::unfold));
  }

  @Override
  public Formula unfoldTemporalStep(BitSet valuation) {
    return create(children.stream().map(c -> c.unfoldTemporalStep(valuation)));
  }
}
