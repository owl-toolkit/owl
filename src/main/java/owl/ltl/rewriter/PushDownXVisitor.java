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

package owl.ltl.rewriter;

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
import owl.ltl.visitors.BinaryVisitor;

final class PushDownXVisitor implements BinaryVisitor<Integer, Formula> {
  private static final PushDownXVisitor INSTANCE = new PushDownXVisitor();
  static final UnaryOperator<Formula> OPERATOR = f -> f.accept(INSTANCE, 0);


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
    return FOperator.of(fOperator.operand().accept(this, parameter));
  }

  @Override
  public Formula visit(GOperator gOperator, Integer parameter) {
    return GOperator.of(gOperator.operand().accept(this, parameter));
  }

  @Override
  public Formula visit(Literal literal, Integer parameter) {
    return XOperator.of(literal, parameter);
  }

  @Override
  public Formula visit(MOperator mOperator, Integer parameter) {
    return MOperator
      .of(mOperator.leftOperand().accept(this, parameter), mOperator.rightOperand().accept(this,
        parameter));
  }

  @Override
  public Formula visit(UOperator uOperator, Integer parameter) {
    return UOperator
      .of(uOperator.leftOperand().accept(this, parameter), uOperator.rightOperand().accept(this,
        parameter));
  }

  @Override
  public Formula visit(ROperator rOperator, Integer parameter) {
    return ROperator
      .of(rOperator.leftOperand().accept(this, parameter), rOperator.rightOperand().accept(this,
        parameter));
  }

  @Override
  public Formula visit(WOperator wOperator, Integer parameter) {
    return WOperator
      .of(wOperator.leftOperand().accept(this, parameter), wOperator.rightOperand().accept(this,
        parameter));
  }

  @Override
  public Formula visit(XOperator xOperator, Integer parameter) {
    return xOperator.operand().accept(this, parameter + 1);
  }
}
