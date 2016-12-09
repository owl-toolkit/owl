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
import ltl.visitors.BinaryVisitor;
import ltl.visitors.Visitor;

import java.util.Collection;
import java.util.stream.Collectors;

class PushDownXVisitor implements BinaryVisitor<Formula, Integer> {

    @Override
    public Formula visit(BooleanConstant b, Integer n) {
        return b;
    }

    @Override
    public Formula visit(Conjunction conjunction, Integer n) {
        return Conjunction.create(conjunction.children.stream().map(x -> x.accept(this, n)));
    }

    @Override
    public Formula visit(Disjunction disjunction, Integer n) {
        return Disjunction.create(disjunction.children.stream().map(x -> x.accept(this, n)));
    }

    @Override
    public Formula visit(FOperator f, Integer n) {
        return FOperator.create(f.operand.accept(this, n));
    }

    @Override
    public Formula visit(GOperator g, Integer n) {
        return GOperator.create(g.operand.accept(this, n));
    }

    @Override
    public Formula visit(Literal l, Integer n) {
        Formula formula = l;

        for (int i = 0; i < n; i++) {
            formula = new XOperator(formula);
        }

        return formula;
    }

    @Override
    public Formula visit(MOperator m, Integer extra) {
        return MOperator.create(m.left.accept(this, extra), m.right.accept(this, extra));
    }

    @Override
    public Formula visit(UOperator u, Integer fo) {
        return UOperator.create(u.left.accept(this, fo), u.right.accept(this, fo));
    }

    @Override
    public Formula visit(ROperator r, Integer fo) {
        return ROperator.create(r.left.accept(this, fo), r.right.accept(this, fo));
    }

    @Override
    public Formula visit(WOperator w, Integer extra) {
        return WOperator.create(w.left.accept(this, extra), w.right.accept(this, extra));
    }

    @Override
    public Formula visit(XOperator x, Integer fo) {
        return x.operand.accept(this, fo + 1);
    }
}
