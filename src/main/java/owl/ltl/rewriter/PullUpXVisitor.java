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

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import owl.ltl.Biconditional;
import owl.ltl.BinaryModalOperator;
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
import owl.ltl.UnaryModalOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;
import owl.ltl.visitors.Visitor;

class PullUpXVisitor implements Visitor<PullUpXVisitor.XFormula>, UnaryOperator<Formula> {

  static final UnaryOperator<Formula> INSTANCE = new PullUpXVisitor();

  @Override
  public Formula apply(Formula formula) {
    return formula.accept(this).toFormula();
  }

  @Override
  public XFormula visit(Biconditional biconditional) {
    XFormula right = biconditional.right.accept(this);
    XFormula left = biconditional.left.accept(this);
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
    List<XFormula> children = conjunction.map(c -> c.accept(this)).collect(Collectors.toList());
    int depth = children.stream().mapToInt(c -> c.depth).min().orElse(0);
    return new XFormula(depth, Conjunction.of(children.stream().map(c -> c.toFormula(depth))));
  }

  @Override
  public XFormula visit(Disjunction disjunction) {
    List<XFormula> children = disjunction.map(c -> c.accept(this)).collect(Collectors.toList());
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
    XFormula r = xOperator.operand.accept(this);
    r.depth++;
    return r;
  }

  private XFormula visit(BinaryModalOperator operator,
    BiFunction<Formula, Formula, Formula> constructor) {
    XFormula right = operator.right.accept(this);
    XFormula left = operator.left.accept(this);
    left.formula = constructor.apply(left.toFormula(right.depth), right.toFormula(left.depth));
    left.depth = Math.min(left.depth, right.depth);
    return left;
  }

  private XFormula visit(UnaryModalOperator operator, Function<Formula, Formula> constructor) {
    XFormula formula = operator.operand.accept(this);
    formula.formula = constructor.apply(formula.formula);
    return formula;
  }

  static final class XFormula {
    int depth;
    Formula formula;

    XFormula(int depth, Formula formula) {
      this.depth = depth;
      this.formula = formula;
    }

    Formula toFormula(int newDepth) {
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
