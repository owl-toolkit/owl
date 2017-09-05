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

package owl.translations.frequency;

import com.google.common.collect.Sets;
import java.util.HashSet;
import java.util.Set;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.FrequencyG;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.UnaryModalOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;
import owl.ltl.visitors.Visitor;

class SlaveSubFormulaVisitor implements Visitor<Set<UnaryModalOperator>> {

  @Override
  public Set<UnaryModalOperator> visit(BooleanConstant booleanConstant) {
    return new HashSet<>();
  }

  @Override
  public Set<UnaryModalOperator> visit(Conjunction conjunction) {
    return new HashSet<>(conjunction.children.stream()
      .map(formula -> formula.accept(this))
      .reduce(new HashSet<>(), Sets::union));
  }

  @Override
  public Set<UnaryModalOperator> visit(Disjunction disjunction) {
    return new HashSet<>(disjunction.children.stream()
      .map(formula -> formula.accept(this))
      .reduce(new HashSet<>(), Sets::union));
  }

  @Override
  public Set<UnaryModalOperator> visit(FOperator fOperator) {
    Set<UnaryModalOperator> result = fOperator.operand.accept(this);
    result.add(fOperator);
    return result;
  }

  @Override
  public Set<UnaryModalOperator> visit(GOperator gOperator) {
    Set<UnaryModalOperator> result = gOperator.operand.accept(this);
    result.add(gOperator);
    return result;
  }

  @Override
  public Set<UnaryModalOperator> visit(Literal literal) {
    return new HashSet<>();
  }

  @Override
  public Set<UnaryModalOperator> visit(MOperator mOperator) {
    return new HashSet<>();
  }

  @Override
  public Set<UnaryModalOperator> visit(ROperator rOperator) {
    return new HashSet<>();
  }

  @Override
  public Set<UnaryModalOperator> visit(FrequencyG gOperator) {
    Set<UnaryModalOperator> result = gOperator.operand.accept(this);
    result.add(gOperator);
    return result;
  }

  @Override
  public Set<UnaryModalOperator> visit(UOperator uOperator) {
    Set<UnaryModalOperator> result = uOperator.left.accept(this);
    result.addAll(uOperator.right.accept(this));
    return result;
  }

  @Override
  public Set<UnaryModalOperator> visit(WOperator wOperator) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Set<UnaryModalOperator> visit(XOperator xOperator) {
    return xOperator.operand.accept(this);
  }
}
