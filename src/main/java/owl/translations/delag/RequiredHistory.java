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

import com.google.common.base.Preconditions;
import javax.annotation.Nonnegative;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.Literal;
import owl.ltl.XOperator;
import owl.ltl.visitors.IntVisitor;
import owl.ltl.visitors.XDepthVisitor;

final class RequiredHistory {

  private static final long[] EMPTY = new long[0];

  private RequiredHistory() {}

  @SuppressWarnings("PMD.AvoidArrayLoops")
  static long[] getRequiredHistory(Formula formula) {
    @Nonnegative
    int xDepth = XDepthVisitor.getDepth(formula);

    if (xDepth == 0) {
      return EMPTY;
    }

    Extractor extractor = new Extractor(xDepth);
    formula.accept(extractor);

    long[] history = extractor.past;
    long accumulator = history[0];

    for (int i = 1; i < history.length; i++) {
      history[i] |= accumulator;
      accumulator = history[i];
    }

    return history;
  }

  private static class Extractor implements IntVisitor {
    final long[] past;
    @Nonnegative
    private int index;

    Extractor(int xDepth) {
      this.past = new long[xDepth];
      this.index = 0;
    }

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
    public int visit(Literal literal) {
      // We're in the present.
      if (index == past.length) {
        return 0;
      }

      Preconditions.checkArgument(literal.getAtom() < 64);
      past[index] |= 1L << literal.getAtom();
      return 0;
    }

    @Override
    public int visit(XOperator xOperator) {
      index++;
      xOperator.operand().accept(this);
      index--;
      return 0;
    }
  }
}
