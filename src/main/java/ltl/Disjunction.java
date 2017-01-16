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

import com.google.common.collect.ImmutableSet;
import java.util.BitSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.stream.Stream;
import ltl.visitors.BinaryVisitor;
import ltl.visitors.IntVisitor;
import ltl.visitors.Visitor;

public final class Disjunction extends PropositionalFormula {

  public Disjunction(Iterable<? extends Formula> disjuncts) {
    super(disjuncts);
  }

  public Disjunction(Formula... disjuncts) {
    super(disjuncts);
  }

  public Disjunction(Stream<? extends Formula> formulaStream) {
    super(formulaStream);
  }

  public static Formula create(Formula... formulaStream) {
    return create(Stream.of(formulaStream));
  }

  public static Formula create(Stream<? extends Formula> formulaStream) {
    Iterator<? extends Formula> iterator = formulaStream.iterator();
    ImmutableSet.Builder<Formula> builder = ImmutableSet.builder();

    while (iterator.hasNext()) {
      Formula child = iterator.next();

      if (child == null) {
        return null;
      }

      if (child == BooleanConstant.TRUE) {
        return BooleanConstant.TRUE;
      }

      if (child == BooleanConstant.FALSE) {
        continue;
      }

      if (child instanceof Disjunction) {
        builder.addAll(((Disjunction) child).children);
      } else {
        builder.add(child);
      }
    }

    ImmutableSet<Formula> set = builder.build();

    if (set.isEmpty()) {
      return BooleanConstant.FALSE;
    }

    if (set.size() == 1) {
      return set.iterator().next();
    }

    return new Disjunction(set);
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
  protected char getOperator() {
    return '|';
  }

  @Override
  protected int hashCodeOnce() {
    return Objects.hash(Disjunction.class, children);
  }

  @Override
  public Formula not() {
    return new Conjunction(children.stream().map(Formula::not));
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
