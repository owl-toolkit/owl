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

package owl.ltl.visitors;

import javax.annotation.Nonnegative;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.XOperator;

public class XDepthVisitor implements IntVisitor {

  private static final XDepthVisitor INSTANCE = new XDepthVisitor();

  @Nonnegative
  public static int getDepth(Formula formula) {
    return formula.accept(INSTANCE);
  }

  @Nonnegative
  @Override
  public int visit(FOperator fOperator) {
    return fOperator.operand().accept(this);
  }

  @Nonnegative
  @Override
  public int visit(GOperator gOperator) {
    return gOperator.operand().accept(this);
  }

  @Nonnegative
  @Override
  public int visit(BooleanConstant booleanConstant) {
    return 0;
  }

  @Nonnegative
  @Override
  public int visit(Conjunction conjunction) {
    return visit((Formula.NaryPropositionalOperator) conjunction);
  }

  @Nonnegative
  @Override
  public int visit(Disjunction disjunction) {
    return visit((Formula.NaryPropositionalOperator) disjunction);
  }

  @Nonnegative
  @Override
  public int visit(Literal literal) {
    return 0;
  }

  @Nonnegative
  @Override
  public int visit(XOperator xOperator) {
    return xOperator.operand().accept(this) + 1;
  }

  @Nonnegative
  private int visit(Formula.NaryPropositionalOperator formula) {
    int max = 0;

    for (Formula child : formula.operands) {
      max = Math.max(max, child.accept(this));
    }

    return max;
  }
}
