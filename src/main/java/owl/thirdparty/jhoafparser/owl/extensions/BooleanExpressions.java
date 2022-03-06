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

package owl.thirdparty.jhoafparser.owl.extensions;

import static owl.logic.propositional.PropositionalFormula.Disjunction;
import static owl.logic.propositional.PropositionalFormula.Variable;
import static owl.logic.propositional.PropositionalFormula.falseConstant;
import static owl.logic.propositional.PropositionalFormula.trueConstant;

import com.google.common.collect.Iterables;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import owl.logic.propositional.PropositionalFormula;
import owl.logic.propositional.PropositionalFormula.Conjunction;
import owl.logic.propositional.PropositionalFormula.Negation;
import owl.thirdparty.jhoafparser.ast.Atom;
import owl.thirdparty.jhoafparser.ast.AtomAcceptance;
import owl.thirdparty.jhoafparser.ast.BooleanExpression;

public final class BooleanExpressions {

  private BooleanExpressions() {}

  public static <T extends Atom> BooleanExpression<T> createConjunction(
    Iterable<BooleanExpression<T>> conjuncts) {
    Iterator<BooleanExpression<T>> iterator = conjuncts.iterator();

    if (!iterator.hasNext()) {
      return new BooleanExpression<>(true);
    }

    BooleanExpression<T> conjunction = iterator.next();

    while (iterator.hasNext()) {
      conjunction = conjunction.and(iterator.next());
    }

    return conjunction;
  }

  public static BooleanExpression<AtomAcceptance> mkFin(int number) {
    return new BooleanExpression<>(
      new AtomAcceptance(AtomAcceptance.Type.TEMPORAL_FIN, number, false));
  }

  public static BooleanExpression<AtomAcceptance> mkInf(int number) {
    return new BooleanExpression<>(
      new AtomAcceptance(AtomAcceptance.Type.TEMPORAL_INF, number, false));
  }

  // Copied from jhoafparser and fixed.
  private static boolean isConjunctive(BooleanExpression<AtomAcceptance> acc) {
    return switch (acc.getType()) {
      case EXP_FALSE, EXP_TRUE, EXP_ATOM -> true;
      case EXP_AND -> isConjunctive(acc.getLeft()) && isConjunctive(acc.getRight());
      case EXP_OR -> false;
      default -> throw new UnsupportedOperationException(
        "Unsupported operator in acceptance condition " + acc);
    };
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
        case EXP_AND -> {
          left = toDnf(acc.getLeft(), uniqueTable);
          right = toDnf(acc.getRight(), uniqueTable);
          for (BooleanExpression<AtomAcceptance> l : left) {
            for (BooleanExpression<AtomAcceptance> r : right) {
              var conjunction = l.and(r);
              dnf.add(uniqueTable.computeIfAbsent(conjunction, Function.identity()));
            }
          }
          return dnf;
        }

        case EXP_OR -> {
          dnf.addAll(toDnf(acc.getLeft(), uniqueTable));
          dnf.addAll(toDnf(acc.getRight(), uniqueTable));
          return dnf;
        }

        default -> throw new UnsupportedOperationException(
          "Unsupported operator in acceptance condition: " + acc);
      }
    }
  }

  public static List<BooleanExpression<AtomAcceptance>> toDnf(
    BooleanExpression<AtomAcceptance> acc) {
    return toDnf(acc, new HashMap<>());
  }

  public static PropositionalFormula<Integer> toPropositionalFormula(
    BooleanExpression<? extends AtomAcceptance> expression) {

    switch (expression.getType()) {
      case EXP_FALSE:
        return falseConstant();

      case EXP_TRUE:
        return trueConstant();

      case EXP_ATOM:
        var atom = expression.getAtom();
        if (atom.getType() == AtomAcceptance.Type.TEMPORAL_INF) {
          return atom.isNegated()
            ? Negation.of(Variable.of(atom.getAcceptanceSet()))
            : Variable.of(atom.getAcceptanceSet());
        } else {
          assert atom.getType() == AtomAcceptance.Type.TEMPORAL_FIN;
          return atom.isNegated()
            ? Variable.of(atom.getAcceptanceSet())
            : Negation.of(Variable.of(atom.getAcceptanceSet()));
        }

      case EXP_AND:
        return Conjunction.of(
          toPropositionalFormula(expression.getLeft()),
          toPropositionalFormula(expression.getRight()));

      case EXP_OR:
        return Disjunction.of(
          toPropositionalFormula(expression.getLeft()),
          toPropositionalFormula(expression.getRight()));

      case EXP_NOT:
        return Negation.of(toPropositionalFormula(expression.getLeft()));

      default:
        throw new AssertionError("Encountered unknown expression " + expression);
    }
  }

  public static BooleanExpression<AtomAcceptance> fromPropositionalFormula(
    PropositionalFormula<Integer> formula) {
    return fromPropositionalFormula(formula, BooleanExpressions::mkInf, BooleanExpressions::mkFin);
  }

  public static <A extends Atom> BooleanExpression<A> fromPropositionalFormula(
    PropositionalFormula<Integer> formula,
    Function<? super Integer, ? extends BooleanExpression<A>> mapper) {
    return fromPropositionalFormula(formula, mapper, x -> mapper.apply(x).not());
  }

  public static <A extends Atom> BooleanExpression<A> fromPropositionalFormula(
    PropositionalFormula<Integer> formula,
    Function<? super Integer, ? extends BooleanExpression<A>> mapper,
    Function<? super Integer, ? extends BooleanExpression<A>> negatedMapper) {

    if (formula instanceof Variable) {
      return mapper.apply(((Variable<Integer>) formula).variable());
    }

    if (formula instanceof Negation) {
      var operand = ((Negation<Integer>) formula).operand();

      if (operand instanceof Variable) {
        return negatedMapper.apply(((Variable<Integer>) operand).variable());
      }

      return fromPropositionalFormula(operand, mapper, negatedMapper).not();
    }

    if (formula instanceof Conjunction) {
      var conjuncts = ((Conjunction<Integer>) formula).conjuncts().stream()
        .map(x -> fromPropositionalFormula(x, mapper, negatedMapper))
        .collect(Collectors.toCollection(ArrayDeque::new));

      if (conjuncts.isEmpty()) {
        return new BooleanExpression<>(true);
      }

      while (conjuncts.size() > 1) {
        var right = conjuncts.removeLast();
        var left = conjuncts.removeLast();
        conjuncts.addLast(left.and(right));
      }

      return Iterables.getOnlyElement(conjuncts);
    }

    if (formula instanceof Disjunction) {
      var disjuncts = ((Disjunction<Integer>) formula).disjuncts().stream()
        .map(x -> fromPropositionalFormula(x, mapper, negatedMapper))
        .collect(Collectors.toCollection(ArrayDeque::new));

      if (disjuncts.isEmpty()) {
        return new BooleanExpression<>(false);
      }

      while (disjuncts.size() > 1) {
        var right = disjuncts.removeLast();
        var left = disjuncts.removeLast();
        disjuncts.addLast(left.or(right));
      }

      return Iterables.getOnlyElement(disjuncts);
    }

    throw new AssertionError("Encountered unknown type of " + formula);
  }
}
