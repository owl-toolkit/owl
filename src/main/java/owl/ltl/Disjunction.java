/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import owl.ltl.visitors.BinaryVisitor;
import owl.ltl.visitors.IntVisitor;
import owl.ltl.visitors.Visitor;

public final class Disjunction extends Formula.NaryPropositionalOperator {

  public Disjunction(Formula... disjuncts) {
    this(sortedList(Set.of(disjuncts)), null);
  }

  public Disjunction(Collection<? extends Formula> disjuncts) {
    this(sortedList(new HashSet<>(disjuncts)), null);
  }

  // Internal constructor.
  @SuppressWarnings("PMD.UnusedFormalParameter")
  private Disjunction(List<? extends Formula> disjuncts, @Nullable Void internal) {
    super(Disjunction.class, List.copyOf(disjuncts));
  }

  public static Formula of(Formula e1, Formula e2) {
    ArrayList<Formula> list = new ArrayList<>();
    list.add(e1);
    list.add(e2);
    return ofInternal(list);
  }

  public static Formula of(Formula... formulas) {
    ArrayList<Formula> list = new ArrayList<>(formulas.length);
    Collections.addAll(list, formulas);
    return ofInternal(list);
  }

  public static Formula of(Collection<? extends Formula> collection) {
    return ofInternal(new ArrayList<>(collection));
  }

  public static Formula of(Stream<? extends Formula> stream) {
    return ofInternal(stream.collect(Collectors.toCollection(ArrayList::new)));
  }

  @SuppressWarnings("PMD.LooseCoupling")
  static Formula ofInternal(ArrayList<Formula> list) {
    boolean sorted = false;

    while (!sorted) {
      list.sort(null);
      sorted = true;

      for (int i = list.size() - 1; i >= 0; i--) {
        Formula child = list.get(i);

        if (BooleanConstant.TRUE.equals(child)) {
          return BooleanConstant.TRUE;
        }

        if (BooleanConstant.FALSE.equals(child)) {
          list.remove(i);
          continue;
        }

        if (i > 0 && list.get(i - 1).equals(child)) {
          list.remove(i);
          continue;
        }

        if (child instanceof Disjunction) {
          list.remove(i);
          list.addAll(child.operands);
          sorted = false;
        }
      }
    }

    return switch (list.size()) {
      case 0 -> BooleanConstant.FALSE;
      case 1 -> list.get(0);
      default -> new Disjunction(list, null);
    };
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
    return Disjunction.ofInternal(mapInternal(Formula::nnf));
  }

  @Override
  public Formula not() {
    return Conjunction.ofInternal(mapInternal(Formula::not));
  }

  @Override
  public Formula substitute(Function<? super TemporalOperator, ? extends Formula> substitution) {
    var disjunction = Disjunction.ofInternal(mapInternal(c -> c.substitute(substitution)));
    return equals(disjunction) ? this : disjunction;
  }

  @Override
  public Formula temporalStep(BitSet valuation) {
    var disjunction = Disjunction.ofInternal(mapInternal(c -> c.temporalStep(valuation)));

    if (this.equals(disjunction)) {
      return this;
    }

    return disjunction;
  }

  @Override
  protected String operatorSymbol() {
    return "|";
  }
}
