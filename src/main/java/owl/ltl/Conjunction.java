/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import owl.ltl.visitors.BinaryVisitor;
import owl.ltl.visitors.IntVisitor;
import owl.ltl.visitors.Visitor;
import owl.util.annotation.CEntryPoint;

public final class Conjunction extends PropositionalFormula {

  private Conjunction(Formula[] conjuncts) {
    super(Conjunction.class, Set.of(conjuncts));
  }

  @CEntryPoint
  public static Formula of(Formula left, Formula right) {
    return of(Arrays.asList(left, right));
  }

  public static Formula of(Formula... formulas) {
    return of(Arrays.asList(formulas));
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

      if (BooleanConstant.FALSE.equals(child)) {
        return BooleanConstant.FALSE;
      }

      if (BooleanConstant.TRUE.equals(child)) {
        continue;
      }

      if (child instanceof Conjunction) {
        set.addAll(((Conjunction) child).children);
      } else {
        set.add(child);
      }
    }

    if (set.isEmpty()) {
      return BooleanConstant.TRUE;
    }

    if (set.size() == 1) {
      return set.iterator().next();
    }

    // Set.copyOf is stupid if given a Set<>, hence this hack
    return new Conjunction(set.toArray(Formula[]::new));
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
  public Formula nnf() {
    return Conjunction.of(map(Formula::nnf));
  }

  @Override
  public Formula not() {
    return Disjunction.of(map(Formula::not));
  }

  @Override
  public Formula substitute(Function<? super TemporalOperator, ? extends Formula> substitution) {
    return Conjunction.of(map(c -> c.substitute(substitution)));
  }

  @Override
  protected String operatorSymbol() {
    return "&";
  }
}
