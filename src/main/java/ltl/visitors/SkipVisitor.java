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

import ltl.*;

public class SkipVisitor implements Visitor<Formula> {

    private final Visitor<Formula> visitor;

    public SkipVisitor(Visitor<Formula> visitor) {
        this.visitor = visitor;
    }

    @Override
    public Formula defaultAction(Formula formula) {
        throw new IllegalStateException("Unreachable Code.");
    }

    @Override
    public Formula visit(BooleanConstant booleanConstant) {
        return booleanConstant;
    }

    @Override
    public Formula visit(Conjunction conjunction) {
        return Conjunction.create(conjunction.children.stream().map(c -> c.accept(visitor)));
    }

    @Override
    public Formula visit(Disjunction disjunction) {
        return Disjunction.create(disjunction.children.stream().map(c -> c.accept(visitor)));
    }

    @Override
    public Formula visit(FOperator fOperator) {
        return FOperator.create(fOperator.operand.accept(visitor));
    }

    @Override
    public Formula visit(GOperator gOperator) {
        return GOperator.create(gOperator.operand.accept(visitor));
    }

    @Override
    public Formula visit(Literal literal) {
        return literal;
    }

    @Override
    public Formula visit(UOperator uOperator) {
        return UOperator.create(uOperator.left.accept(visitor), uOperator.right.accept(visitor));
    }

    @Override
    public Formula visit(ROperator rOperator) {
        return ROperator.create(rOperator.left.accept(visitor), rOperator.right.accept(visitor));
    }

    @Override
    public Formula visit(XOperator xOperator) {
        return XOperator.create(xOperator.operand.accept(visitor));
    }
}
