/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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
import owl.ltl.FrequencyG;
import owl.ltl.GOperator;
import owl.ltl.HOperator;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.OOperator;
import owl.ltl.ROperator;
import owl.ltl.SOperator;
import owl.ltl.TOperator;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;
import owl.ltl.YOperator;
import owl.ltl.ZOperator;

/**
 * Visitor skeleton implementation that views the formula as propositional formula. For this reason
 * all methods for modal operators are final and need to be handled uniformly.
 */
public abstract class PropositionalVisitor<T> implements Visitor<T> {

  protected abstract T visit(Formula.TemporalOperator formula);

  @Override
  public final T visit(FOperator fOperator) {
    return this.visit((Formula.TemporalOperator) fOperator);
  }

  @Override
  public final T visit(FrequencyG freq) {
    return this.visit((Formula.TemporalOperator) freq);
  }

  @Override
  public final T visit(GOperator gOperator) {
    return this.visit((Formula.TemporalOperator) gOperator);
  }

  @Override
  public final T visit(HOperator hOperator) {
    return this.visit((Formula.TemporalOperator) hOperator);
  }

  @Override
  public final T visit(Literal literal) {
    return this.visit((Formula.TemporalOperator) literal);
  }

  @Override
  public final T visit(MOperator mOperator) {
    return this.visit((Formula.TemporalOperator) mOperator);
  }

  @Override
  public final T visit(OOperator oOperator) {
    return this.visit((Formula.TemporalOperator) oOperator);
  }

  @Override
  public final T visit(ROperator rOperator) {
    return this.visit((Formula.TemporalOperator) rOperator);
  }

  @Override
  public final T visit(SOperator sOperator) {
    return this.visit((Formula.TemporalOperator) sOperator);
  }

  @Override
  public final T visit(TOperator tOperator) {
    return this.visit((Formula.TemporalOperator) tOperator);
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

  @Override
  public final T visit(YOperator yOperator) {
    return this.visit((Formula.TemporalOperator) yOperator);
  }

  @Override
  public final T visit(ZOperator zOperator) {
    return this.visit((Formula.TemporalOperator) zOperator);
  }

}
