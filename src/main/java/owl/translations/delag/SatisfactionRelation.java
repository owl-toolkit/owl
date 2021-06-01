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

package owl.translations.delag;

import java.util.BitSet;
import javax.annotation.Nonnegative;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.Literal;
import owl.ltl.XOperator;
import owl.ltl.visitors.IntVisitor;
import owl.ltl.visitors.XDepthVisitor;

final class SatisfactionRelation {

  private SatisfactionRelation() {
  }

  /**
   * Determines if the {@code past} is a model for the {@code formula}.
   *
   * @param formula
   *     the formula
   *
   * @return true if past |= formula.
   */
  static boolean models(History past, BitSet present, Formula formula) {
    @Nonnegative
    int xDepth = XDepthVisitor.getDepth(formula);

    return xDepth <= past.size() && formula.accept(new Evaluator(past, present, xDepth)) == 1;
  }

  private static class Evaluator implements IntVisitor {

    private final History past;
    private final BitSet present;
    private int offset;

    Evaluator(History past, BitSet present, int requiredHistory) {
      this.past = past;
      this.present = present;
      this.offset = past.size() - requiredHistory;
    }

    @Override
    public int visit(BooleanConstant booleanConstant) {
      return booleanConstant.value ? 1 : 0;
    }

    @Override
    public int visit(Conjunction conjunction) {
      for (Formula child : conjunction.operands) {
        if (child.accept(this) == 0) {
          return 0;
        }
      }

      return 1;
    }

    @Override
    public int visit(Disjunction disjunction) {
      for (Formula child : disjunction.operands) {
        if (child.accept(this) == 1) {
          return 1;
        }
      }

      return 0;
    }

    @Override
    public int visit(Literal literal) {
      boolean models;

      if (offset == past.size()) {
        models = present.get(literal.getAtom()) ^ literal.isNegated();
      } else {
        models = past.get(offset, literal);
      }

      return models ? 1 : 0;
    }

    @Override
    public int visit(XOperator xOperator) {
      offset++;
      int models = xOperator.operand().accept(this);
      offset--;
      return models;
    }
  }
}
