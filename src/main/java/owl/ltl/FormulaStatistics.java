/*
 * Copyright (C) 2022  (Salomon Sickert)
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

package owl.ltl;

import java.util.HashMap;
import java.util.Map;
import owl.ltl.Formula.TemporalOperator;
import owl.ltl.visitors.PropositionalIntVisitor;

public final class FormulaStatistics {

  private FormulaStatistics() {
  }

  public static Map<TemporalOperator, Integer> countTemporalOperators(Formula formula) {

    var visitor = new PropositionalIntVisitor() {

      final Map<TemporalOperator, Integer> count = new HashMap<>();

      @Override
      public int visit(BooleanConstant booleanConstant) {
        return 0;
      }

      @Override
      public int visit(Conjunction conjunction) {
        conjunction.operands.forEach(x -> x.accept(this));
        return 0;
      }

      @Override
      public int visit(Disjunction disjunction) {
        disjunction.operands.forEach(x -> x.accept(this));
        return 0;
      }

      @Override
      protected int visit(TemporalOperator formula) {
        formula.operands.forEach(x -> x.accept(this));
        count.compute(formula, (key, oldValue) -> oldValue == null ? 1 : oldValue + 1);
        return 0;
      }

      @Override
      public int visit(Literal literal) {
        return 0;
      }
    };

    formula.accept(visitor);
    return visitor.count;
  }
}
