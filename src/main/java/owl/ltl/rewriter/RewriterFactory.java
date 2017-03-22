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

import com.google.common.collect.ImmutableList;
import java.util.function.Function;
import owl.ltl.Formula;
import owl.util.UnaryOperators;

public final class RewriterFactory {

  private RewriterFactory() {
  }

  public static Formula apply(RewriterEnum rewriter, Formula formula) {
    return rewriter.getRewriter().apply(formula);
  }

  public enum RewriterEnum {
    MODAL(new ModalSimplifier()),
    PULLUP_X(PullupXVisitor.INSTANCE),
    MODAL_ITERATIVE(
      new IterativeRewriter(
        UnaryOperators.chain(ImmutableList.of(
          PullupXVisitor.INSTANCE,
          NormaliseX.INSTANCE,
          ModalSimplifier.INSTANCE,
          NormaliseX.INSTANCE,
          FairnessSimplifier.INSTANCE)))),
    PUSHDOWN_X(new PushDownXVisitor()),
    FAIRNESS(NormaliseX.INSTANCE.andThen(FairnessSimplifier.INSTANCE));

    private final Function<Formula, Formula> rewriter;

    RewriterEnum(Function<Formula, Formula> rewriter) {
      this.rewriter = rewriter;
    }

    public Function<Formula, Formula> getRewriter() {
      return rewriter;
    }
  }
}