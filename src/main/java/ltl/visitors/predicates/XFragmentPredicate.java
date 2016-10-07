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

package ltl.visitors.predicates;

import ltl.*;
import ltl.visitors.Visitor;

public final class XFragmentPredicate implements Visitor<Boolean> {

    public static final Visitor<Boolean> INSTANCE = new XFragmentPredicate();

    private XFragmentPredicate() {

    }

    @Override
    public Boolean defaultAction(Formula formula) {
        return Boolean.FALSE;
    }

    @Override
    public Boolean visit(BooleanConstant booleanConstant) {
        return Boolean.TRUE;
    }

    @Override
    public Boolean visit(Conjunction conjunction) {
        return conjunction.allMatch(c -> c.accept(this));
    }

    @Override
    public Boolean visit(Disjunction disjunction) {
        return disjunction.allMatch(c -> c.accept(this));
    }

    @Override
    public Boolean visit(Literal literal) {
        return Boolean.TRUE;
    }

    @Override
    public Boolean visit(XOperator xOperator) {
        return xOperator.operand.accept(this);
    }
}
