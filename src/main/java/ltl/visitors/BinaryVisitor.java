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

public interface BinaryVisitor<A, B> {
    A visit(BooleanConstant b, B fo);

    A visit(Conjunction c, B fo);

    A visit(Disjunction b, B fo);

    A visit(FOperator f, B fo);

    A visit(GOperator g, B fo);

    A visit(Literal l, B fo);

    A visit(UOperator u, B fo);

    A visit(ROperator r, B fo);

    A visit(XOperator x, B fo);
}
