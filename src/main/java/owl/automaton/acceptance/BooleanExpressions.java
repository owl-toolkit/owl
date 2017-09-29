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
import java.util.List;
import jhoafparser.ast.Atom;
import jhoafparser.ast.BooleanExpression;

public final class BooleanExpressions {
  private BooleanExpressions() {
  }

  public static <T extends Atom> BooleanExpression<T> createConjunction(
    Iterable<BooleanExpression<T>> conjuncts) {

    BooleanExpression<T> conjunction = null;

    for (BooleanExpression<T> conjunct : conjuncts) {
      if (conjunction == null) {
        conjunction = conjunct;
      } else {
        conjunction = conjunction.and(conjunct);
      }
    }

    if (conjunction == null) {
      return new BooleanExpression<>(true);
    }

    return conjunction;
  }

  public static <T extends Atom> BooleanExpression<T> createDisjunction(
    Iterable<BooleanExpression<T>> disjuncts) {

    BooleanExpression<T> disjunction = null;

    for (BooleanExpression<T> disjunct : disjuncts) {
      if (disjunction == null) {
        disjunction = disjunct;
      } else {
        disjunction = disjunction.or(disjunct);
      }
    }

    if (disjunction == null) {
      return new BooleanExpression<>(false);
    }

    return disjunction;
  }

  public static <T extends Atom> List<BooleanExpression<T>> getConjuncts(BooleanExpression<T> exp) {
    if (!exp.isAND()) {
      return Lists.newArrayList(exp);
    }

    List<BooleanExpression<T>> conjuncts = getConjuncts(exp.getLeft());
    conjuncts.addAll(getConjuncts(exp.getRight()));
    return conjuncts;
  }

  public static <T extends Atom> List<BooleanExpression<T>> getDisjuncts(BooleanExpression<T> exp) {
    if (!exp.isOR()) {
      return Lists.newArrayList(exp);
    }

    List<BooleanExpression<T>> disjuncts = getDisjuncts(exp.getLeft());
    disjuncts.addAll(getDisjuncts(exp.getRight()));
    return disjuncts;
  }
}
