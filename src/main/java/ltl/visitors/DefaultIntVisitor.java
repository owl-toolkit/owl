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
import ltl.Formula;

public abstract class DefaultIntVisitor implements IntVisitor {

    protected abstract int defaultAction(Formula formula);

    @Override
    public int visit(BooleanConstant booleanConstant) {
        return defaultAction(booleanConstant);
    }

    @Override
    public int visit(Conjunction conjunction) {
        return defaultAction(conjunction);
    }

    @Override
    public int visit(Disjunction disjunction) {
        return defaultAction(disjunction);
    }

    @Override
    public int visit(FOperator fOperator) {
        return defaultAction(fOperator);
    }

    @Override
    public int visit(FrequencyG freq) {
        return defaultAction(freq);
    }

    @Override
    public int visit(GOperator gOperator) {
        return defaultAction(gOperator);
    }

    @Override
    public int visit(Literal literal) {
        return defaultAction(literal);
    }

    @Override
    public int visit(MOperator mOperator) {
        return defaultAction(mOperator);
    }

    @Override
    public int visit(ROperator rOperator) {
        return defaultAction(rOperator);
    }

    @Override
    public int visit(UOperator uOperator) {
        return defaultAction(uOperator);
    }

    @Override
    public int visit(WOperator wOperator) {
        return defaultAction(wOperator);
    }

    @Override
    public int visit(XOperator xOperator) {
        return defaultAction(xOperator);
    }

}
