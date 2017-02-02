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

import java.util.function.Predicate;
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

public final class XFragmentPredicate implements Predicate<Formula>, Visitor<Boolean> {

  public static final XFragmentPredicate INSTANCE = new XFragmentPredicate();

  private XFragmentPredicate() {

  }

  public static boolean testStatic(Formula formula) {
    return INSTANCE.test(formula);
  }

  @Override
  public boolean test(Formula formula) {
    return formula.accept(this);
  }

  @Override
  public Boolean visit(FOperator fOperator) {
    return false;
  }

  @Override
  public Boolean visit(FrequencyG freq) {
    return false;
  }

  @Override
  public Boolean visit(GOperator gOperator) {
    return false;
  }

  @Override
  public Boolean visit(MOperator mOperator) {
    return false;
  }

  @Override
  public Boolean visit(ROperator rOperator) {
    return false;
  }

  @Override
  public Boolean visit(UOperator uOperator) {
    return false;
  }

  @Override
  public Boolean visit(WOperator wOperator) {
    return false;
  }

  @Override
  public Boolean visit(BooleanConstant booleanConstant) {
    return Boolean.TRUE;
  }

  @Override
  public Boolean visit(Conjunction conjunction) {
    return conjunction.allMatch(c -> c.accept(this));
  }

  @Override
  public Boolean visit(Disjunction disjunction) {
    return disjunction.allMatch(c -> c.accept(this));
  }

  @Override
  public Boolean visit(Literal literal) {
    return Boolean.TRUE;
  }

  @Override
  public Boolean visit(XOperator xOperator) {
    return xOperator.operand.accept(this);
  }
}
