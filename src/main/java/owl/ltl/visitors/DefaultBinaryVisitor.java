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

package owl.ltl.visitors;

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

public abstract class DefaultBinaryVisitor<P, R> implements BinaryVisitor<P, R> {

  protected abstract R defaultAction(Formula formula, P parameter);

  @Override
  public R visit(BooleanConstant booleanConstant, P parameter) {
    return defaultAction(booleanConstant, parameter);
  }

  @Override
  public R visit(Conjunction conjunction, P parameter) {
    return defaultAction(conjunction, parameter);
  }

  @Override
  public R visit(Disjunction disjunction, P parameter) {
    return defaultAction(disjunction, parameter);
  }

  @Override
  public R visit(FOperator fOperator, P parameter) {
    return defaultAction(fOperator, parameter);
  }

  @Override
  public R visit(GOperator gOperator, P parameter) {
    return defaultAction(gOperator, parameter);
  }

  @Override
  public R visit(Literal literal, P parameter) {
    return defaultAction(literal, parameter);
  }

  @Override
  public R visit(MOperator mOperator, P parameter) {
    return defaultAction(mOperator, parameter);
  }

  @Override
  public R visit(UOperator uOperator, P parameter) {
    return defaultAction(uOperator, parameter);
  }

  @Override
  public R visit(ROperator rOperator, P parameter) {
    return defaultAction(rOperator, parameter);
  }

  @Override
  public R visit(WOperator wOperator, P parameter) {
    return defaultAction(wOperator, parameter);
  }

  @Override
  public R visit(XOperator xOperator, P parameter) {
    return defaultAction(xOperator, parameter);
  }
}
