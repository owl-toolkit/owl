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

package owl.ltl.rewriter;

import java.util.HashSet;
import java.util.Set;
import owl.ltl.Conjunction;
import owl.ltl.Formula;
import owl.ltl.SyntacticFragment;
import owl.ltl.UOperator;
import owl.ltl.visitors.Converter;

public class SplitUntilVisitor extends Converter {

  protected SplitUntilVisitor() {
    super(SyntacticFragment.ALL);
  }

  @Override
  public Formula visit(UOperator uOperator) {
    if (uOperator.leftOperand() instanceof Conjunction) {
      Conjunction conjunction = (Conjunction) uOperator.leftOperand().accept(this);
      Set<Formula> newConjuncts = new HashSet<>();
      for (Formula f : conjunction.operands) {
        newConjuncts.add(UOperator.of(f, uOperator.rightOperand().accept(this)));
      }
      return Conjunction.of(newConjuncts);
    }
    return UOperator.of(
      uOperator.leftOperand().accept(this),
      uOperator.rightOperand().accept(this));
  }
}
