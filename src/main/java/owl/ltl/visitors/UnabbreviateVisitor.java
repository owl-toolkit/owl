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

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.WOperator;

public class UnabbreviateVisitor extends DefaultConverter {

  private final Set<Class<? extends Formula>> classes;

  @SafeVarargs
  public UnabbreviateVisitor(Class<? extends Formula>... classes) {
    this.classes = ImmutableSet.copyOf(classes);
  }

  @Override
  public Formula visit(ROperator rOperator) {
    if (!classes.contains(ROperator.class)) {
      return super.visit(rOperator);
    }

    Formula left = rOperator.left.accept(this);
    Formula right = rOperator.right.accept(this);

    return Disjunction
      .create(GOperator.create(right), UOperator.create(right, Conjunction.create(left, right)));
  }

  @Override
  public Formula visit(WOperator wOperator) {
    if (!classes.contains(WOperator.class)) {
      return super.visit(wOperator);
    }

    Formula left = wOperator.left.accept(this);
    Formula right = wOperator.right.accept(this);

    return Disjunction.create(GOperator.create(left), UOperator.create(left, right));
  }

  @Override
  public Formula visit(MOperator mOperator) {
    if (!classes.contains(MOperator.class)) {
      return super.visit(mOperator);
    }

    Formula left = mOperator.left.accept(this);
    Formula right = mOperator.right.accept(this);

    return UOperator.create(right, Conjunction.create(left, right));
  }
}
