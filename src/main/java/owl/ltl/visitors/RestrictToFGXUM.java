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

import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.WOperator;

public class RestrictToFGXUM extends DefaultConverter {

  @Override
  public Formula visit(ROperator rOperator) {
    Formula left = rOperator.left.accept(this);
    Formula right = rOperator.right.accept(this);

    return Disjunction.create(GOperator.create(right), MOperator.create(left, right));
  }

  @Override
  public Formula visit(WOperator wOperator) {
    Formula left = wOperator.left.accept(this);
    Formula right = wOperator.right.accept(this);

    return Disjunction.create(GOperator.create(left), UOperator.create(left, right));
  }
}
