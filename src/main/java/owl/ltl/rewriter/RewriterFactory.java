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

import java.util.List;
import java.util.function.Function;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;

public final class RewriterFactory {
  private RewriterFactory() {}

  public static Formula apply(RewriterEnum rewriter, Formula formula) {
    return rewriter.getRewriter().apply(formula);
  }

  public static LabelledFormula apply(RewriterEnum rewriter, LabelledFormula formula) {
    return LabelledFormula.create(rewriter.getRewriter().apply(formula.formula), formula.variables);
  }

  public static LabelledFormula apply(LabelledFormula formula, RewriterEnum... rewriters) {
    LabelledFormula result = formula;

    for (RewriterEnum rewriter : rewriters) {
      result = apply(rewriter, result);
    }

    return result;
  }

  public static Formula apply(Formula formula) {
    return RewriterEnum.MODAL_ITERATIVE.getRewriter().apply(formula);
  }

  public enum RewriterEnum {
    MODAL(new ModalSimplifier()),
    PULLUP_X(PullupXVisitor.INSTANCE),
    MODAL_ITERATIVE(new IterativeRewriter(UnaryOperators.chain(List.of(
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
