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

package owl.automaton.acceptance;

import com.google.common.collect.Lists;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import jhoafparser.ast.Atom;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;

public final class BooleanExpressions {
  private BooleanExpressions() {
  }

  public static <T extends Atom> BooleanExpression<T> createConjunction(
    Iterable<BooleanExpression<T>> conjuncts) {
    return createConjunction(conjuncts.iterator());
  }

  public static <T extends Atom> BooleanExpression<T> createConjunction(
    Stream<BooleanExpression<T>> conjuncts) {
    return createConjunction(conjuncts.iterator());
  }

  public static <T extends Atom> BooleanExpression<T> createConjunction(
    Iterator<BooleanExpression<T>> conjuncts) {

    if (!conjuncts.hasNext()) {
      return new BooleanExpression<>(true);
    }

    BooleanExpression<T> conjunction = conjuncts.next();

    while (conjuncts.hasNext()) {
      conjunction = conjunction.and(conjuncts.next());
    }

    return conjunction;
  }

  public static <T extends Atom> BooleanExpression<T> createDisjunction(
    Iterable<BooleanExpression<T>> disjuncts) {
    return createDisjunction(disjuncts.iterator());
  }

  public static <T extends Atom> BooleanExpression<T> createDisjunction(
    Stream<BooleanExpression<T>> disjuncts) {
    return createDisjunction(disjuncts.iterator());
  }

  public static <T extends Atom> BooleanExpression<T> createDisjunction(
    Iterator<BooleanExpression<T>> disjuncts) {

    if (!disjuncts.hasNext()) {
      return new BooleanExpression<>(false);
    }

    BooleanExpression<T> disjunction = disjuncts.next();

    while (disjuncts.hasNext()) {
      disjunction = disjunction.or(disjuncts.next());
    }

    return disjunction;
  }

  static <T extends Atom> List<BooleanExpression<T>> getConjuncts(BooleanExpression<T> exp) {
    if (!exp.isAND()) {
      return Lists.newArrayList(exp);
    }

    List<BooleanExpression<T>> conjuncts = getConjuncts(exp.getLeft());
    conjuncts.addAll(getConjuncts(exp.getRight()));
    return conjuncts;
  }

  static <T extends Atom> List<BooleanExpression<T>> getDisjuncts(BooleanExpression<T> exp) {
    if (!exp.isOR()) {
      return Lists.newArrayList(exp);
    }

    List<BooleanExpression<T>> disjuncts = getDisjuncts(exp.getLeft());
    disjuncts.addAll(getDisjuncts(exp.getRight()));
    return disjuncts;
  }

  public static BooleanExpression<AtomAcceptance> mkFin(int number) {
    return new BooleanExpression<>(
      new AtomAcceptance(AtomAcceptance.Type.TEMPORAL_FIN, number, false));
  }

  public static BooleanExpression<AtomAcceptance> mkInf(int number) {
    return new BooleanExpression<>(
      new AtomAcceptance(AtomAcceptance.Type.TEMPORAL_INF, number, false));
  }
}
