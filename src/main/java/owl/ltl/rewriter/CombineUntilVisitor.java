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

public class CombineUntilVisitor extends Converter {
  protected CombineUntilVisitor() {
    super(SyntacticFragment.ALL);
  }

  @Override
  public Formula visit(Conjunction conjunction) {
    Set<UOperator> candidates = new HashSet<>();
    Set<Formula> combinable = new HashSet<>();
    Set<Formula> newCon = new HashSet<>();
    for (Formula f : conjunction.operands) {
      if (f instanceof UOperator) {
        candidates.add((UOperator) f);
      } else {
        newCon.add(f.accept(this));
      }

    }
    for (UOperator f : candidates) {
      Formula common = f.rightOperand();
      for (UOperator u : candidates) {
        if (u.rightOperand().equals(common)) {
          combinable.add(u.leftOperand().accept(this));
        }
      }
      newCon.add(UOperator.of(Conjunction.of(combinable), common.accept(this)));
      combinable.clear();
    }
    return Conjunction.of(newCon);
  }
}

