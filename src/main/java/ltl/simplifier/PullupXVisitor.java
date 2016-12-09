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

package ltl.simplifier;

import ltl.*;
import ltl.visitors.Visitor;

import java.util.Collection;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

class PullupXVisitor implements Visitor<XFormula> {

    @Override
    public XFormula visit(BooleanConstant booleanConstant) {
        return new XFormula(0, booleanConstant);
    }

    @Override
    public XFormula visit(Conjunction conjunction) {
        Collection<XFormula> children = conjunction.children.stream().map(c -> c.accept(this)).collect(Collectors.toList());
        int depth = children.stream().mapToInt(c -> c.depth).min().orElse(0);
        return new XFormula(depth, new Conjunction(children.stream().map(c -> c.toFormula(depth))));
    }

    @Override
    public XFormula visit(Disjunction disjunction) {
        Collection<XFormula> children = disjunction.children.stream().map(c -> c.accept(this)).collect(Collectors.toList());
        int depth = children.stream().mapToInt(c -> c.depth).min().orElse(0);
        return new XFormula(depth, new Disjunction(children.stream().map(c -> c.toFormula(depth))));
    }

    @Override
    public XFormula visit(FOperator fOperator) {
        return visit(fOperator, FOperator::create);
    }

    @Override
    public XFormula visit(FrequencyG freq) {
        throw new UnsupportedOperationException();
    }

    @Override
    public XFormula visit(GOperator gOperator) {
        return visit(gOperator, GOperator::create);
    }

    @Override
    public XFormula visit(Literal literal) {
        return new XFormula(0, literal);
    }

    @Override
    public XFormula visit(MOperator mOperator) {
        return visit(mOperator, MOperator::create);
    }

    @Override
    public XFormula visit(ROperator rOperator) {
        return visit(rOperator, ROperator::create);
    }

    @Override
    public XFormula visit(UOperator uOperator) {
        return visit(uOperator, UOperator::create);
    }

    @Override
    public XFormula visit(WOperator wOperator) {
        return visit(wOperator, WOperator::create);
    }

    @Override
    public XFormula visit(XOperator xOperator) {
        XFormula r = xOperator.operand.accept(this);
        r.depth++;
        return r;
    }

    private XFormula visit(BinaryModalOperator operator, BiFunction<Formula, Formula, Formula> constructor) {
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
}
