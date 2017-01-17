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

import ltl.BooleanConstant;
import ltl.Conjunction;
import ltl.Disjunction;
import ltl.FOperator;
import ltl.GOperator;
import ltl.Literal;
import ltl.MOperator;
import ltl.ROperator;
import ltl.UOperator;
import ltl.WOperator;
import ltl.XOperator;

public interface BinaryVisitor<A, B> {

  A visit(BooleanConstant b, B extra);

  A visit(Conjunction c, B extra);

  A visit(Disjunction b, B extra);

  A visit(FOperator f, B extra);

  A visit(GOperator g, B extra);

  A visit(Literal l, B extra);

  A visit(MOperator m, B extra);

  A visit(UOperator u, B extra);

  A visit(ROperator r, B extra);

  A visit(WOperator w, B extra);

  A visit(XOperator x, B extra);

}
