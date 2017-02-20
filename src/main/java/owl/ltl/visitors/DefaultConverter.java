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
import owl.ltl.Formula;
import owl.ltl.FrequencyG;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;

public abstract class DefaultConverter implements Visitor<Formula> {

  @Override
  public Formula visit(BooleanConstant booleanConstant) {
    return booleanConstant;
  }

  @Override
  public Formula visit(Conjunction conjunction) {
    return Conjunction.create(conjunction.children.stream().map(c -> c.accept(this)));
  }

  @Override
  public Formula visit(Disjunction disjunction) {
    return Disjunction.create(disjunction.children.stream().map(c -> c.accept(this)));
  }

  @Override
  public Formula visit(FOperator fOperator) {
    return FOperator.create(fOperator.operand.accept(this));
  }

  @Override
  public Formula visit(GOperator gOperator) {
    return GOperator.create(gOperator.operand.accept(this));
  }

  @Override
  public Formula visit(Literal literal) {
    return literal;
  }

  @Override
  public Formula visit(MOperator mOperator) {
    return MOperator.create(mOperator.left.accept(this), mOperator.right.accept(this));
  }

  @Override
  public Formula visit(FrequencyG freq) {
    return new FrequencyG(freq.operand.accept(this), freq.bound, freq.cmp, freq.limes);
  }

  @Override
  public Formula visit(UOperator uOperator) {
    return UOperator.create(uOperator.left.accept(this), uOperator.right.accept(this));
  }

  @Override
  public Formula visit(WOperator wOperator) {
    return WOperator.create(wOperator.left.accept(this), wOperator.right.accept(this));
  }

  @Override
  public Formula visit(ROperator rOperator) {
    return ROperator.create(rOperator.left.accept(this), rOperator.right.accept(this));
  }

  @Override
  public Formula visit(XOperator xOperator) {
    return XOperator.create(xOperator.operand.accept(this));
  }
}