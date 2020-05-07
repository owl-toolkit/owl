/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
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

package jhoafparser.extensions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import jhoafparser.ast.Atom;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;

public final class BooleanExpressions {
  private BooleanExpressions() {
  }

  public static <T extends Atom> boolean evaluate(BooleanExpression<T> expression,
    Predicate<T> valuation) {
    if (expression.isTRUE()) {
      return true;
    }
    if (expression.isFALSE()) {
      return true;
    }
    if (expression.isAtom()) {
      return valuation.test(expression.getAtom());
    }
    if (expression.isNOT()) {
      return !evaluate(expression.getLeft(), valuation);
    }
    if (expression.isAND()) {
      return evaluate(expression.getLeft(), valuation)
        && evaluate(expression.getRight(), valuation);
    }
    if (expression.isOR()) {
      return evaluate(expression.getLeft(), valuation)
        || evaluate(expression.getRight(), valuation);
    }
    throw new AssertionError("Encountered unknown expression " + expression);
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

  public static <T extends Atom> List<BooleanExpression<T>> getConjuncts(BooleanExpression<T> exp) {
    if (exp.isTRUE()) {
      return new ArrayList<>();
    }

    if (!exp.isAND()) {
      return new ArrayList<>(List.of(exp));
    }

    List<BooleanExpression<T>> conjuncts = getConjuncts(exp.getLeft());
    conjuncts.addAll(getConjuncts(exp.getRight()));
    return conjuncts;
  }

  public static <T extends Atom> List<BooleanExpression<T>> getDisjuncts(BooleanExpression<T> exp) {
    if (exp.isFALSE()) {
      return new ArrayList<>();
    }

    if (!exp.isOR()) {
      return new ArrayList<>(List.of(exp));
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

  public static BooleanExpression<AtomAcceptance> shift(
    BooleanExpression<AtomAcceptance> expression, int offset) {

    if (offset == 0) {
      return expression;
    }

    if (expression.isTRUE() || expression.isFALSE()) {
      return expression;
    }

    if (expression.isAtom()) {
      var atom = expression.getAtom();
      var shiftedAtom = new AtomAcceptance(
        atom.getType(), atom.getAcceptanceSet() + offset, atom.isNegated());
      return new BooleanExpression<>(shiftedAtom);
    }

    if (expression.isNOT()) {
      return shift(expression.getLeft(), offset).not();
    }

    if (expression.isAND()) {
      return shift(expression.getLeft(), offset).and(shift(expression.getRight(), offset));
    }

    if (expression.isOR()) {
      return shift(expression.getLeft(), offset).or(shift(expression.getRight(), offset));
    }

    throw new AssertionError("Encountered unknown expression " + expression);
  }

  // Copied from jhoafparser and fixed.
  private static boolean isConjunctive(BooleanExpression<AtomAcceptance> acc) {
    switch (acc.getType()) {
      case EXP_FALSE:
        // fall-through
      case EXP_TRUE:
        // fall-through
      case EXP_ATOM:
        return true;

      case EXP_AND:
        return isConjunctive(acc.getLeft()) && isConjunctive(acc.getRight());

      case EXP_OR:
        return false;

      default:
        throw new UnsupportedOperationException(
          "Unsupported operator in acceptance condition " + acc);
    }
  }

  private static List<BooleanExpression<AtomAcceptance>> toDnf(
    BooleanExpression<AtomAcceptance> acc,
    Map<BooleanExpression<AtomAcceptance>, BooleanExpression<AtomAcceptance>> uniqueTable) {

    if (isConjunctive(acc)) {
      return List.of(uniqueTable.computeIfAbsent(acc, Function.identity()));
    } else {
      List<BooleanExpression<AtomAcceptance>> dnf = new ArrayList<>();
      List<BooleanExpression<AtomAcceptance>> left;
      List<BooleanExpression<AtomAcceptance>> right;

      switch (acc.getType()) {
        case EXP_AND:
          left = toDnf(acc.getLeft(), uniqueTable);
          right = toDnf(acc.getRight(), uniqueTable);
          for (BooleanExpression<AtomAcceptance> l : left) {
            for (BooleanExpression<AtomAcceptance> r : right) {
              var conjunction = l.and(r);
              dnf.add(uniqueTable.computeIfAbsent(conjunction, Function.identity()));
            }
          }
          return dnf;

        case EXP_OR:
          dnf.addAll(toDnf(acc.getLeft(), uniqueTable));
          dnf.addAll(toDnf(acc.getRight(), uniqueTable));
          return dnf;

        default:
          throw new UnsupportedOperationException(
            "Unsupported operator in acceptance condition: " + acc);
      }
    }
  }

  public static List<BooleanExpression<AtomAcceptance>> toDnf(
    BooleanExpression<AtomAcceptance> acc) {
    return toDnf(acc, new HashMap<>());
  }
}
