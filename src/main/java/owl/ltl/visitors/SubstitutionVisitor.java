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

import java.util.function.Function;
import owl.ltl.Biconditional;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;

public final class SubstitutionVisitor extends PropositionalVisitor<Formula> {
  private final Function<? super Formula, ? extends Formula> substitutionFunction;

  public SubstitutionVisitor(Function<? super Formula, ? extends Formula> substitutionFunction) {
    this.substitutionFunction = substitutionFunction;
  }

  @Override
  protected Formula modalOperatorAction(Formula formula) {
    return substitutionFunction.apply(formula);
  }

  @Override
  public Formula visit(BooleanConstant booleanConstant) {
    return booleanConstant;
  }

  @Override
  public Formula visit(Biconditional biconditional) {
    return Biconditional.of(biconditional.left.accept(this), biconditional.right.accept(this));
  }

  @Override
  public Formula visit(Conjunction conjunction) {
    return Conjunction.of(conjunction.children.stream().map(x -> x.accept(this)));
  }

  @Override
  public Formula visit(Disjunction disjunction) {
    return Disjunction.of(disjunction.children.stream().map(x -> x.accept(this)));
  }
}
