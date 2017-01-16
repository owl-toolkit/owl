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

package ltl.visitors;

import ltl.BooleanConstant;
import ltl.Conjunction;
import ltl.Disjunction;
import ltl.FOperator;
import ltl.Formula;
import ltl.FrequencyG;
import ltl.GOperator;
import ltl.Literal;
import ltl.MOperator;
import ltl.ROperator;
import ltl.UOperator;
import ltl.WOperator;
import ltl.XOperator;

public abstract class DefaultVisitor<T> implements Visitor<T> {

  protected abstract T defaultAction(Formula formula);

  @Override
  public T visit(BooleanConstant booleanConstant) {
    return defaultAction(booleanConstant);
  }

  @Override
  public T visit(Conjunction conjunction) {
    return defaultAction(conjunction);
  }

  @Override
  public T visit(Disjunction disjunction) {
    return defaultAction(disjunction);
  }

  @Override
  public T visit(FOperator fOperator) {
    return defaultAction(fOperator);
  }

  @Override
  public T visit(FrequencyG freq) {
    return defaultAction(freq);
  }

  @Override
  public T visit(GOperator gOperator) {
    return defaultAction(gOperator);
  }

  @Override
  public T visit(Literal literal) {
    return defaultAction(literal);
  }

  @Override
  public T visit(MOperator mOperator) {
    return defaultAction(mOperator);
  }

  @Override
  public T visit(ROperator rOperator) {
    return defaultAction(rOperator);
  }

  @Override
  public T visit(UOperator uOperator) {
    return defaultAction(uOperator);
  }

  @Override
  public T visit(WOperator wOperator) {
    return defaultAction(wOperator);
  }

  @Override
  public T visit(XOperator xOperator) {
    return defaultAction(xOperator);
  }

}
