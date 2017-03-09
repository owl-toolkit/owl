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

package owl.ltl.rewriter;

import java.util.function.Function;
import java.util.function.UnaryOperator;
import owl.ltl.Formula;

class IterativeRewriter implements UnaryOperator<Formula> {
  private static final int MAX_ITERATIONS = 10;
  private final Function<Formula, Formula> rewriter;

  IterativeRewriter(Function<Formula, Formula> rewriter) {
    this.rewriter = rewriter;
  }

  @Override
  public Formula apply(Formula formula) {
    Formula before = null;
    Formula after = formula;

    for (int i = 0; i < MAX_ITERATIONS && !after.equals(before); i++) {
      before = after;
      after = rewriter.apply(before);
    }

    return after;
  }
}
