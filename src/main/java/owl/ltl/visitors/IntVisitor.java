/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
 *
 * This file is part of Owl.
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

import java.util.function.ToIntFunction;
import owl.ltl.Biconditional;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.Negation;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;

@SuppressWarnings({"checkstyle:LeftCurly", "checkstyle:RightCurly",
                    "checkstyle:EmptyLineSeparator"})
public interface IntVisitor extends ToIntFunction<Formula> {

  @Override
  default int applyAsInt(Formula value)              { return value.accept(this); }

  default int visit(Biconditional biconditional)     { throw uoe(biconditional);   }
  default int visit(BooleanConstant booleanConstant) { throw uoe(booleanConstant); }
  default int visit(Conjunction conjunction)         { throw uoe(conjunction);     }
  default int visit(Disjunction disjunction)         { throw uoe(disjunction);     }
  default int visit(Literal literal)                 { throw uoe(literal);         }
  default int visit(Negation negation)               { throw uoe(negation);        }

  default int visit(FOperator fOperator)             { throw uoe(fOperator);       }
  default int visit(GOperator gOperator)             { throw uoe(gOperator);       }
  default int visit(MOperator mOperator)             { throw uoe(mOperator);       }
  default int visit(ROperator rOperator)             { throw uoe(rOperator);       }
  default int visit(UOperator uOperator)             { throw uoe(uOperator);       }
  default int visit(WOperator wOperator)             { throw uoe(wOperator);       }
  default int visit(XOperator xOperator)             { throw uoe(xOperator);       }

  private static UnsupportedOperationException uoe(Formula formula) {
    return new UnsupportedOperationException("No action defined for " + formula.getClass());
  }
}
