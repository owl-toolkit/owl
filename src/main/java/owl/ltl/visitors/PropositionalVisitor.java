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

import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;

/**
 * Visitor skeleton implementation that views the formula as propositional formula. For this reason
 * all methods for modal operators are final and need to be handled uniformly.
 */
public abstract class PropositionalVisitor<T> implements Visitor<T> {

  protected abstract T visit(Formula.TemporalOperator formula);

  @Override
  public abstract T visit(Literal literal);

  @Override
  public final T visit(FOperator fOperator) {
    return this.visit((Formula.TemporalOperator) fOperator);
  }

  @Override
  public final T visit(GOperator gOperator) {
    return this.visit((Formula.TemporalOperator) gOperator);
  }

  @Override
  public final T visit(MOperator mOperator) {
    return this.visit((Formula.TemporalOperator) mOperator);
  }

  @Override
  public final T visit(ROperator rOperator) {
    return this.visit((Formula.TemporalOperator) rOperator);
  }

  @Override
  public final T visit(UOperator uOperator) {
    return this.visit((Formula.TemporalOperator) uOperator);
  }

  @Override
  public final T visit(WOperator wOperator) {
    return this.visit((Formula.TemporalOperator) wOperator);
  }

  @Override
  public final T visit(XOperator xOperator) {
    return this.visit((Formula.TemporalOperator) xOperator);
  }
}
