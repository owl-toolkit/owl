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

public interface IntVisitor {

    int defaultAction(Formula formula);

    default int visit(BooleanConstant booleanConstant) {
        return defaultAction(booleanConstant);
    }

    default int visit(Conjunction conjunction) {
        return defaultAction(conjunction);
    }

    default int visit(Disjunction disjunction) {
        return defaultAction(disjunction);
    }

    default int visit(FOperator fOperator) {
        return defaultAction(fOperator);
    }

    default int visit(GOperator gOperator) {
        return defaultAction(gOperator);
    }

    default int visit(FrequencyG freq) {
        return defaultAction(freq);
    }

    default int visit(Literal literal) {
        return defaultAction(literal);
    }

    default int visit(UOperator uOperator) {
        return defaultAction(uOperator);
    }

    default int visit(ROperator rOperator) {
        return defaultAction(rOperator);
    }

    default int visit(XOperator xOperator) {
        return defaultAction(xOperator);
    }
}
