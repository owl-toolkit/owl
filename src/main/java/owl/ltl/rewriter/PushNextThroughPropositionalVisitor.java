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

import owl.ltl.Biconditional;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.LabelledFormula;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;
import owl.ltl.visitors.BinaryVisitor;

public final class PushNextThroughPropositionalVisitor implements BinaryVisitor<Integer, Formula> {
  public static final PushNextThroughPropositionalVisitor INSTANCE
    = new PushNextThroughPropositionalVisitor();

  public static Formula apply(Formula formula) {
    return formula.accept(INSTANCE, 0);
  }

  public static LabelledFormula apply(LabelledFormula formula) {
    return formula.wrap(apply(formula.formula()));
  }

  @Override
  public Formula visit(Biconditional biconditional, Integer parameter) {
    return Biconditional.of(biconditional.leftOperand().accept(this, parameter),
      biconditional.rightOperand().accept(this, parameter));
  }

  @Override
  public Formula visit(BooleanConstant booleanConstant, Integer parameter) {
    return booleanConstant;
  }

  @Override
  public Formula visit(Conjunction conjunction, Integer parameter) {
    return Conjunction.of(conjunction.map(x -> x.accept(this, parameter)));
  }

  @Override
  public Formula visit(Disjunction disjunction, Integer parameter) {
    return Disjunction.of(disjunction.map(x -> x.accept(this, parameter)));
  }

  @Override
  public Formula visit(FOperator fOperator, Integer parameter) {
    return visitT(fOperator, parameter);
  }

  @Override
  public Formula visit(GOperator gOperator, Integer parameter) {
    return visitT(gOperator, parameter);
  }

  @Override
  public Formula visit(Literal literal, Integer parameter) {
    return XOperator.of(literal, parameter);
  }

  @Override
  public Formula visit(MOperator mOperator, Integer parameter) {
    return visitT(mOperator, parameter);
  }

  @Override
  public Formula visit(UOperator uOperator, Integer parameter) {
    return visitT(uOperator, parameter);
  }

  @Override
  public Formula visit(ROperator rOperator, Integer parameter) {
    return visitT(rOperator, parameter);
  }

  @Override
  public Formula visit(WOperator wOperator, Integer parameter) {
    return visitT(wOperator, parameter);
  }

  @Override
  public Formula visit(XOperator xOperator, Integer parameter) {
    return xOperator.operand().accept(this, parameter + 1);
  }

  private static Formula visitT(Formula.TemporalOperator temporalOperator, Integer parameter) {
    return XOperator.of(temporalOperator, parameter);
  }
}
