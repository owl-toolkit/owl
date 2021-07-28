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

import java.util.function.UnaryOperator;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;

public final class SimplifierFactory {

  private SimplifierFactory() {}

  public static Formula apply(Formula formula, Mode mode) {
    switch (mode) {
      case SYNTACTIC:
        return ((UnaryOperator<Formula>) x -> x.substitute(Formula::nnf))
          .andThen(SyntacticSimplifier.INSTANCE)
          .apply(formula);

      case SYNTACTIC_FAIRNESS:
        return ((UnaryOperator<Formula>) x -> x.substitute(Formula::nnf))
          .andThen(SyntacticFairnessSimplifier.NormaliseX.INSTANCE)
          .andThen(SyntacticFairnessSimplifier.INSTANCE)
          .apply(formula);

      case SYNTACTIC_FIXPOINT:
        Formula before = null;
        Formula after = formula.substitute(Formula::nnf);

        for (int i = 0; i < 100 && !after.equals(before); i++) {
          before = after;
          after = SyntacticSimplifier.INSTANCE.apply(before);
          after = PullUpXVisitor.OPERATOR.apply(after);
        }

        return after;

      case PULL_UP_X:
        return PullUpXVisitor.OPERATOR.apply(formula);

      case PUSH_DOWN_X:
        return PushDownXVisitor.OPERATOR.apply(formula);

      case NNF:
        return formula.nnf();

      default:
        throw new AssertionError("unreachable");
    }
  }

  public static LabelledFormula apply(LabelledFormula formula, Mode mode) {
    return formula.wrap(apply(formula.formula(), mode));
  }

  public enum Mode {
    SYNTACTIC,
    SYNTACTIC_FAIRNESS,
    SYNTACTIC_FIXPOINT,
    PULL_UP_X,
    PUSH_DOWN_X,
    NNF
  }
}
