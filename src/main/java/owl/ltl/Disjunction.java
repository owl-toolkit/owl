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
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import owl.ltl.visitors.BinaryVisitor;
import owl.ltl.visitors.IntVisitor;
import owl.ltl.visitors.Visitor;

public final class Disjunction extends PropositionalFormula {

  public Disjunction(Collection<? extends Formula> disjuncts) {
    super(disjuncts);
  }

  public Disjunction(Formula... disjuncts) {
    this(Set.of(disjuncts));
  }

  public Disjunction(Stream<? extends Formula> formulaStream) {
    this(formulaStream.collect(Collectors.toUnmodifiableSet()));
  }

  public static Formula of(Formula left, Formula right) {
    return of(List.of(left, right));
  }

  public static Formula of(Formula... formulas) {
    return of(Stream.of(formulas));
  }

  public static Formula of(Iterable<? extends Formula> iterable) {
    return of(iterable.iterator());
  }

  public static Formula of(Stream<? extends Formula> stream) {
    return of(stream.iterator());
  }

  public static Formula of(Iterator<? extends Formula> iterator) {
    Set<Formula> set = new HashSet<>();

    while (iterator.hasNext()) {
      Formula child = iterator.next();
      assert child != null;

      if (child == BooleanConstant.TRUE) {
        return BooleanConstant.TRUE;
      }

      if (child == BooleanConstant.FALSE) {
        continue;
      }

      if (child instanceof Disjunction) {
        set.addAll(((Disjunction) child).children);
      } else {
        set.add(child);
      }
    }

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
  public <A, B> A accept(BinaryVisitor<B, A> v, B parameter) {
    return v.visit(this, parameter);
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
  public Formula nnf() {
    return new Disjunction(map(Formula::nnf));
  }

  @Override
  public Formula not() {
    return new Conjunction(map(Formula::not));
  }

  @Override
  public Formula temporalStep(BitSet valuation) {
    return of(map(c -> c.temporalStep(valuation)));
  }

  @Override
  public Formula temporalStepUnfold(BitSet valuation) {
    return of(map(c -> c.temporalStepUnfold(valuation)));
  }

  @Override
  public Formula unfold() {
    return of(map(Formula::unfold));
  }

  @Override
  public Formula unfoldTemporalStep(BitSet valuation) {
    return of(map(c -> c.unfoldTemporalStep(valuation)));
  }
}
