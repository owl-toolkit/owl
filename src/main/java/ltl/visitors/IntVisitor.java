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
import ltl.FrequencyG;
import ltl.GOperator;
import ltl.Literal;
import ltl.MOperator;
import ltl.ROperator;
import ltl.UOperator;
import ltl.WOperator;
import ltl.XOperator;

public interface IntVisitor {

  int visit(BooleanConstant booleanConstant);

  int visit(Conjunction conjunction);

  int visit(Disjunction disjunction);

  int visit(FOperator fOperator);

  int visit(FrequencyG freq);

  int visit(GOperator gOperator);

  int visit(Literal literal);

  int visit(MOperator mOperator);

  int visit(ROperator rOperator);

  int visit(UOperator uOperator);

  int visit(WOperator wOperator);

  int visit(XOperator xOperator);

}
