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

package ltl;

public abstract class DefaultConverter implements Visitor<Formula> {
    @Override
    public Formula defaultAction(Formula formula) {
        return formula;
    }

    @Override
    public Formula visit(Conjunction conjunction) {
        return Conjunction.create(conjunction.children.stream().map(c -> c.accept(this)));
    }

    @Override
    public Formula visit(Disjunction disjunction) {
        return Disjunction.create(disjunction.children.stream().map(c -> c.accept(this)));
    }

    @Override
    public Formula visit(FOperator fOperator) {
        return FOperator.create(fOperator.operand.accept(this));
    }

    @Override
    public Formula visit(GOperator gOperator) {
        return GOperator.create(gOperator.operand.accept(this));
    }

    @Override
    public Formula visit(UOperator uOperator) {
        return UOperator.create(uOperator.left.accept(this), uOperator.right.accept(this));
    }

    @Override
    public Formula visit(XOperator xOperator) {
        return XOperator.create(xOperator.operand.accept(this));
    }
}
