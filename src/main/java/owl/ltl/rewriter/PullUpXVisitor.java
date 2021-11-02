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

import java.util.function.BiFunction;
import java.util.function.Function;
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

final class PullUpXVisitor implements Visitor<PullUpXVisitor.XFormula> {

  static final PullUpXVisitor INSTANCE = new PullUpXVisitor();

  private PullUpXVisitor() {}

  @Override
  public XFormula visit(Biconditional biconditional) {
    XFormula right = biconditional.rightOperand().accept(this);
    XFormula left = biconditional.leftOperand().accept(this);
    left.formula = Biconditional.of(left.toFormula(right.depth), right.toFormula(left.depth));
    left.depth = Math.min(left.depth, right.depth);
    return left;
  }

  @Override
  public XFormula visit(BooleanConstant booleanConstant) {
    return new XFormula(0, booleanConstant);
  }

  @Override
  public XFormula visit(Conjunction conjunction) {
    var children = conjunction.operands.stream()
      .map(c -> c.accept(this)).toList();
    int depth = children.stream().mapToInt(c -> c.depth).min().orElse(0);
    return new XFormula(depth, Conjunction.of(children.stream().map(c -> c.toFormula(depth))));
  }

  @Override
  public XFormula visit(Disjunction disjunction) {
    var children = disjunction.operands.stream()
      .map(c -> c.accept(this)).toList();
    int depth = children.stream().mapToInt(c -> c.depth).min().orElse(0);
    return new XFormula(depth, Disjunction.of(children.stream().map(c -> c.toFormula(depth))));
  }

  @Override
  public XFormula visit(FOperator fOperator) {
    return visit(fOperator, FOperator::of);
  }

  @Override
  public XFormula visit(GOperator gOperator) {
    return visit(gOperator, GOperator::of);
  }

  @Override
  public XFormula visit(Literal literal) {
    return new XFormula(0, literal);
  }

  @Override
  public XFormula visit(MOperator mOperator) {
    return visit(mOperator, MOperator::of);
  }

  @Override
  public XFormula visit(ROperator rOperator) {
    return visit(rOperator, ROperator::of);
  }

  @Override
  public XFormula visit(UOperator uOperator) {
    return visit(uOperator, UOperator::of);
  }

  @Override
  public XFormula visit(WOperator wOperator) {
    return visit(wOperator, WOperator::of);
  }

  @Override
  public XFormula visit(XOperator xOperator) {
    XFormula r = xOperator.operand().accept(this);
    r.depth++;
    return r;
  }

  private XFormula visit(Formula.BinaryTemporalOperator operator,
    BiFunction<Formula, Formula, Formula> constructor) {
    XFormula right = operator.rightOperand().accept(this);
    XFormula left = operator.leftOperand().accept(this);
    left.formula = constructor.apply(left.toFormula(right.depth), right.toFormula(left.depth));
    left.depth = Math.min(left.depth, right.depth);
    return left;
  }

  private XFormula visit(Formula.UnaryTemporalOperator operator,
    Function<Formula, Formula> constructor) {
    XFormula formula = operator.operand().accept(this);
    formula.formula = constructor.apply(formula.formula);
    return formula;
  }

  static final class XFormula {
    private int depth;
    private Formula formula;

    private XFormula(int depth, Formula formula) {
      this.depth = depth;
      this.formula = formula;
    }

    Formula toFormula(int newDepth) {
      Formula formula = this.formula;
      int i = depth - newDepth;

      for (; i > 0; i--) {
        formula = XOperator.of(formula);
      }

      return formula;
    }

    Formula toFormula() {
      return toFormula(0);
    }
  }
}
