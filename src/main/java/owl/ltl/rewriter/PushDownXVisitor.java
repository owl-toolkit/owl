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

package owl.ltl.rewriter;

import java.util.function.UnaryOperator;
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

class PushDownXVisitor implements BinaryVisitor<Integer, Formula>, UnaryOperator<Formula> {

  @Override
  public Formula apply(Formula formula) {
    return formula.accept(this, 0);
  }

  @Override
  public Formula visit(BooleanConstant booleanConstant, Integer parameter) {
    return booleanConstant;
  }

  @Override
  public Formula visit(Conjunction conjunction, Integer parameter) {
    return Conjunction.create(conjunction.children.stream().map(x -> x.accept(this, parameter)));
  }

  @Override
  public Formula visit(Disjunction disjunction, Integer parameter) {
    return Disjunction.create(disjunction.children.stream().map(x -> x.accept(this, parameter)));
  }

  @Override
  public Formula visit(FOperator fOperator, Integer parameter) {
    return FOperator.create(fOperator.operand.accept(this, parameter));
  }

  @Override
  public Formula visit(GOperator gOperator, Integer parameter) {
    return GOperator.create(gOperator.operand.accept(this, parameter));
  }

  @Override
  public Formula visit(Literal literal, Integer parameter) {
    Formula formula = literal;

    for (int i = 0; i < parameter; i++) {
      formula = new XOperator(formula);
    }

    return formula;
  }

  @Override
  public Formula visit(MOperator mOperator, Integer parameter) {
    return MOperator.create(mOperator.left.accept(this, parameter), mOperator.right.accept(this,
      parameter));
  }

  @Override
  public Formula visit(UOperator uOperator, Integer parameter) {
    return UOperator.create(uOperator.left.accept(this, parameter), uOperator.right.accept(this,
      parameter));
  }

  @Override
  public Formula visit(ROperator rOperator, Integer parameter) {
    return ROperator.create(rOperator.left.accept(this, parameter), rOperator.right.accept(this,
      parameter));
  }

  @Override
  public Formula visit(WOperator wOperator, Integer parameter) {
    return WOperator.create(wOperator.left.accept(this, parameter), wOperator.right.accept(this,
      parameter));
  }

  @Override
  public Formula visit(XOperator xOperator, Integer parameter) {
    return xOperator.operand.accept(this, parameter + 1);
  }
}
