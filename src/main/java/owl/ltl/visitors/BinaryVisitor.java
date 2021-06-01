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

import java.util.function.BiFunction;
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
public interface BinaryVisitor<P, R> extends BiFunction<Formula, P, R> {

  @Override
  default R apply(Formula formula, P p)                         { return formula.accept(this, p); }

  default R visit(Biconditional biconditional, P parameter)     { throw uoe(biconditional);   }
  default R visit(BooleanConstant booleanConstant, P parameter) { throw uoe(booleanConstant); }
  default R visit(Conjunction conjunction, P parameter)         { throw uoe(conjunction);     }
  default R visit(Disjunction disjunction, P parameter)         { throw uoe(disjunction);     }
  default R visit(Literal literal, P parameter)                 { throw uoe(literal);         }
  default R visit(Negation negation, P parameter)               { throw uoe(negation);        }

  default R visit(FOperator fOperator, P parameter)             { throw uoe(fOperator);       }
  default R visit(GOperator gOperator, P parameter)             { throw uoe(gOperator);       }
  default R visit(MOperator mOperator, P parameter)             { throw uoe(mOperator);       }
  default R visit(UOperator uOperator, P parameter)             { throw uoe(uOperator);       }
  default R visit(ROperator rOperator, P parameter)             { throw uoe(rOperator);       }
  default R visit(WOperator wOperator, P parameter)             { throw uoe(wOperator);       }
  default R visit(XOperator xOperator, P parameter)             { throw uoe(xOperator);       }

  private static UnsupportedOperationException uoe(Formula formula) {
    return new UnsupportedOperationException("No action defined for " + formula.getClass());
  }
}
