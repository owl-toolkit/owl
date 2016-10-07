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
import java.util.stream.Collectors;

class PullupXVisitor implements Visitor<XFormula> {
    @Override
    public XFormula defaultAction(Formula formula) {
        return new XFormula(0, formula);
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
        XFormula r = fOperator.operand.accept(this);
        r.formula = new FOperator(r.formula);
        return r;
    }

    @Override
    public XFormula visit(GOperator gOperator) {
        XFormula r = gOperator.operand.accept(this);
        r.formula = new GOperator(r.formula);
        return r;
    }

    @Override
    public XFormula visit(UOperator uOperator) {
        XFormula r = uOperator.right.accept(this);
        XFormula l = uOperator.left.accept(this);
        l.formula = new UOperator(l.toFormula(r.depth), r.toFormula(l.depth));
        l.depth = Math.min(l.depth, r.depth);
        return l;
    }

    @Override
    public XFormula visit(XOperator xOperator) {
        XFormula r = xOperator.operand.accept(this);
        r.depth++;
        return r;
    }
}
