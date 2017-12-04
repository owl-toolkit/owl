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
import owl.ltl.Fragments;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.XOperator;
import owl.ltl.visitors.DefaultBinaryVisitor;

final class NormaliseX extends DefaultBinaryVisitor<Integer, Formula> implements
  UnaryOperator<Formula> {

  static final UnaryOperator<Formula> INSTANCE = new TraverseRewriter(new NormaliseX(),
    NormaliseX::isApplicable);

  public static boolean isApplicable(Formula formula) {
    return Fragments.isFgx(formula) && (
      FairnessSimplifier.getAlmostAllOperand(formula) != null
        || FairnessSimplifier.getInfinitelyOftenOperand(formula) != null);
  }

  @Override
  public Formula apply(Formula formula) {
    return formula.accept(this, 0);
  }

  @Override
  protected Formula defaultAction(Formula formula, Integer depth) {
    throw FairnessSimplifier.getUnsupportedFormulaException(formula);
  }

  @Override
  public Formula visit(BooleanConstant booleanConstant, Integer depth) {
    return booleanConstant;
  }

  @Override
  public Formula visit(Conjunction conjunction, Integer depth) {
    if (Fragments.isFinite(conjunction)) {
      return XOperator.of(conjunction, depth);
    }

    return Conjunction.of(conjunction.map(x -> x.accept(this, depth)));
  }

  @Override
  public Formula visit(Disjunction disjunction, Integer depth) {
    if (Fragments.isFinite(disjunction)) {
      return XOperator.of(disjunction, depth);
    }

    return Disjunction.of(disjunction.map(x -> x.accept(this, depth)));
  }

  // The F is within of the scope of FG / GF. Resetting the  X-depth is safe.
  @Override
  public Formula visit(FOperator fOperator, Integer depth) {
    return FOperator.of(fOperator.operand.accept(this, 0));
  }

  // The G is within of the scope of FG / GF. Resetting the  X-depth is safe.
  @Override
  public Formula visit(GOperator gOperator, Integer depth) {
    return GOperator.of(gOperator.operand.accept(this, 0));
  }

  @Override
  public Formula visit(Literal literal, Integer parameter) {
    return XOperator.of(literal, parameter);
  }

  @Override
  public Formula visit(XOperator xOperator, Integer parameter) {
    return xOperator.operand.accept(this, parameter + 1);
  }
}
