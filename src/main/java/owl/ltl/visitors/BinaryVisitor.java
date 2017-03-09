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

package owl.ltl.visitors;

import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;

public interface BinaryVisitor<P, R> {

  R visit(BooleanConstant booleanConstant, P parameter);

  R visit(Conjunction conjunction, P parameter);

  R visit(Disjunction disjunction, P parameter);

  R visit(FOperator fOperator, P parameter);

  R visit(GOperator gOperator, P parameter);

  R visit(Literal literal, P parameter);

  R visit(MOperator mOperator, P parameter);

  R visit(UOperator uOperator, P parameter);

  R visit(ROperator rOperator, P parameter);

  R visit(WOperator wOperator, P parameter);

  R visit(XOperator xOperator, P parameter);

}
