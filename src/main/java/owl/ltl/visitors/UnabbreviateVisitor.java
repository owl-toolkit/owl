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

import java.util.Set;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.SyntacticFragment;
import owl.ltl.UOperator;
import owl.ltl.WOperator;

public class UnabbreviateVisitor extends Converter {

  private final Set<Class<? extends Formula>> classes;

  public UnabbreviateVisitor(Set<Class<? extends Formula>> classes) {
    super(SyntacticFragment.NNF);
    this.classes = Set.copyOf(classes);
  }

  @Override
  public Formula visit(ROperator rOperator) {
    if (!classes.contains(ROperator.class)) {
      return super.visit(rOperator);
    }

    Formula left = rOperator.leftOperand().accept(this);
    Formula right = rOperator.rightOperand().accept(this);

    return Disjunction.of(GOperator.of(right), UOperator.of(right, Conjunction.of(left, right)));
  }

  @Override
  public Formula visit(WOperator wOperator) {
    if (!classes.contains(WOperator.class)) {
      return super.visit(wOperator);
    }

    Formula left = wOperator.leftOperand().accept(this);
    Formula right = wOperator.rightOperand().accept(this);

    return Disjunction.of(GOperator.of(left), UOperator.of(left, right));
  }

  @Override
  public Formula visit(MOperator mOperator) {
    if (!classes.contains(MOperator.class)) {
      return super.visit(mOperator);
    }

    Formula left = mOperator.leftOperand().accept(this);
    Formula right = mOperator.rightOperand().accept(this);

    return UOperator.of(right, Conjunction.of(left, right));
  }
}
