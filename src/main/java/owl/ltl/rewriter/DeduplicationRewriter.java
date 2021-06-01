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

package owl.ltl.rewriter;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import owl.ltl.Biconditional;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;
import owl.ltl.visitors.Visitor;

public final class DeduplicationRewriter implements Visitor<Formula>, UnaryOperator<Formula> {
  // TODO Static + Concurrent / Cache?
  private final Map<Formula, Formula> map = new HashMap<>();

  private DeduplicationRewriter() {}


  public static Formula deduplicate(Formula formula) {
    return formula.accept(new DeduplicationRewriter());
  }


  private Formula computeIfAbsent(Formula formula, Supplier<Formula> supplier) { // NOPMD
    // Can't use map.computeIfAbsent due to recursive calls; supplier.get() might modify the map
    Formula value = map.get(formula);
    if (value != null) {
      return value;
    }
    Formula newValue = supplier.get();
    Formula oldValue = map.putIfAbsent(formula, newValue);
    return oldValue == null ? newValue : oldValue;
  }


  @Override
  public Formula visit(Biconditional biconditional) {
    return computeIfAbsent(biconditional,
      () -> new Biconditional(
        biconditional.leftOperand().accept(this), biconditional.rightOperand().accept(this)));
  }

  @Override
  public Formula visit(BooleanConstant booleanConstant) {
    return booleanConstant;
  }

  @Override
  public Formula visit(Conjunction conjunction) {
    return computeIfAbsent(conjunction, () -> Conjunction.of(conjunction.map(this)));
  }

  @Override
  public Formula visit(Disjunction disjunction) {
    return computeIfAbsent(disjunction, () -> Disjunction.of(disjunction.map(this)));
  }

  @Override
  public Formula visit(FOperator fOperator) {
    return computeIfAbsent(fOperator, () -> new FOperator(fOperator.operand().accept(this)));
  }

  @Override
  public Formula visit(GOperator gOperator) {
    return computeIfAbsent(gOperator, () -> new GOperator(gOperator.operand().accept(this)));
  }

  @Override
  public Formula visit(Literal literal) {
    return computeIfAbsent(literal, () -> literal);
  }

  @Override
  public Formula visit(MOperator mOperator) {
    return computeIfAbsent(mOperator,
      () -> new MOperator(mOperator.leftOperand().accept(this),
        mOperator.rightOperand().accept(this)));
  }

  @Override
  public Formula visit(ROperator rOperator) {
    return computeIfAbsent(rOperator,
      () -> new ROperator(rOperator.leftOperand().accept(this),
        rOperator.rightOperand().accept(this)));
  }

  @Override
  public Formula visit(UOperator uOperator) {
    return computeIfAbsent(uOperator,
      () -> new UOperator(uOperator.leftOperand().accept(this),
        uOperator.rightOperand().accept(this)));
  }

  @Override
  public Formula visit(WOperator wOperator) {
    return computeIfAbsent(wOperator,
      () -> new WOperator(wOperator.leftOperand().accept(this),
        wOperator.rightOperand().accept(this)));
  }

  @Override
  public Formula visit(XOperator xOperator) {
    return computeIfAbsent(xOperator, () -> new XOperator(xOperator.operand().accept(this)));
  }
}
