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

package owl.translations.fgx2generic;

import com.google.common.collect.Lists;
import java.util.BitSet;
import java.util.List;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.Literal;
import owl.ltl.XOperator;
import owl.ltl.visitors.DefaultBinaryVisitor;
import owl.ltl.visitors.XDepthVisitor;

class SatisfactionRelation {

  private static final Evaluator INSTANCE = new Evaluator();

  private SatisfactionRelation() {
  }

  /**
   * @param history
   *     0 -> now, 1 -> one step before, 2 -> two steps before...
   * @param formula
   *     the formula
   *
   * @return true if history |= formula.
   */
  static boolean models(List<BitSet> history, Formula formula) {
    int requiredHistory =
      XDepthVisitor.getDepth(formula) + (formula.anyMatch(Literal.class::isInstance) ? 1 : 0);

    if (requiredHistory > history.size()) {
      return false;
    }

    return formula.accept(INSTANCE, history.subList(0, requiredHistory));
  }

  private static class Evaluator extends DefaultBinaryVisitor<List<BitSet>, Boolean> {
    @Override
    public Boolean visit(BooleanConstant booleanConstant, List<BitSet> history) {
      return booleanConstant.value ? Boolean.TRUE : Boolean.FALSE;
    }

    @Override
    public Boolean visit(Conjunction conjunction, List<BitSet> history) {
      return conjunction.children.stream().allMatch(x -> x.accept(this, history));
    }

    @Override
    public Boolean visit(Disjunction disjunction, List<BitSet> history) {
      return disjunction.children.stream().anyMatch(x -> x.accept(this, history));
    }

    @Override
    public Boolean visit(Literal literal, List<BitSet> history) {
      if (history.isEmpty()) {
        return Boolean.FALSE;
      }

      // Access the last position in history record.
      return Lists.reverse(history).get(0).get(literal.getAtom()) ^ literal.isNegated();
    }

    @Override
    public Boolean visit(XOperator xOperator, List<BitSet> history) {
      if (history.isEmpty()) {
        return Boolean.FALSE;
      }

      // Drop last element of history. We're moving into the future.
      return xOperator.operand.accept(this, history.subList(0, history.size() - 1));
    }
  }
}
