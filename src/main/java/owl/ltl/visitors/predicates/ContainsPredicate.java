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

package owl.ltl.visitors.predicates;

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
import owl.ltl.visitors.Visitor;

public final class ContainsPredicate implements Visitor<Boolean> {
  private final Class<? extends Formula> clazz;

  public ContainsPredicate(Class<? extends Formula> cl) {
    clazz = cl;
  }

  @Override
  public Boolean visit(BooleanConstant booleanConstant) {
    return clazz.equals(BooleanConstant.class);
  }

  @Override
  public Boolean visit(Conjunction conjunction) {
    return clazz.equals(Conjunction.class) || conjunction.anyMatch(child -> child.accept(this));
  }

  @Override
  public Boolean visit(Disjunction disjunction) {
    return clazz.equals(Disjunction.class) || disjunction.anyMatch(child -> child.accept(this));
  }

  @Override
  public Boolean visit(FOperator fOperator) {
    return clazz.equals(FOperator.class) || fOperator.operand.accept(this);
  }

  @Override
  public Boolean visit(GOperator gOperator) {
    return clazz.equals(GOperator.class) || gOperator.operand.accept(this);
  }

  @Override
  public Boolean visit(FrequencyG freq) {
    return clazz.equals(FrequencyG.class) || freq.operand.accept(this);
  }

  @Override
  public Boolean visit(Literal literal) {
    return clazz.equals(Literal.class);
  }

  @Override
  public Boolean visit(MOperator mOperator) {
    return clazz.equals(MOperator.class) || mOperator.left.accept(this) || mOperator.right
      .accept(this);
  }

  @Override
  public Boolean visit(ROperator rOp) {
    return clazz.equals(ROperator.class) || rOp.left.accept(this) || rOp.right.accept(this);
  }

  @Override
  public Boolean visit(UOperator uOperator) {
    return clazz.equals(UOperator.class) || uOperator.left.accept(this) || uOperator.right
      .accept(this);
  }

  @Override
  public Boolean visit(WOperator wOperator) {
    return clazz.equals(WOperator.class) || wOperator.left.accept(this) || wOperator.right
      .accept(this);
  }

  @Override
  public Boolean visit(XOperator xOperator) {
    return clazz.equals(XOperator.class) || xOperator.operand.accept(this);
  }
}
