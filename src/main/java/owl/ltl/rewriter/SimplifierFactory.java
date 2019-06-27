/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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

import java.util.function.Function;
import java.util.function.UnaryOperator;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;

public final class SimplifierFactory {
  private static final UnaryOperator<Formula> nnfLight = x -> x.substitute(Formula::nnf);

  private SimplifierFactory() {}

  public static Formula apply(Formula formula, Mode mode) {
    return mode.operation.apply(formula);
  }

  public static Formula apply(Formula formula, Mode... modes) {
    Formula result = formula;

    for (Mode mode : modes) {
      result = apply(result, mode);
    }

    return result;
  }

  public static LabelledFormula apply(LabelledFormula formula, Mode mode) {
    return formula.wrap(apply(formula.formula(), mode));
  }

  public static LabelledFormula apply(LabelledFormula formula, Mode... modes) {
    return formula.wrap(apply(formula.formula(), modes));
  }

  // Deliberately go against the advice from
  // https://github.com/google/error-prone/blob/master/docs/bugpattern/ImmutableEnumChecker.md
  // We only need Formula -> Formula here
  @SuppressWarnings("ImmutableEnumChecker")
  public enum Mode {
    SYNTACTIC(nnfLight
      .andThen(SyntacticSimplifier.INSTANCE)),

    SYNTACTIC_FAIRNESS(nnfLight
      .andThen(SyntacticFairnessSimplifier.NormaliseX.INSTANCE)
      .andThen(SyntacticFairnessSimplifier.INSTANCE)),

    SYNTACTIC_FIXPOINT(nnfLight
      .andThen(new Fixpoint(SyntacticSimplifier.INSTANCE, PullUpXVisitor.OPERATOR))),

    PULL_UP_X(PullUpXVisitor.OPERATOR),

    PUSH_DOWN_X(PushDownXVisitor.OPERATOR),

    NNF(Formula::nnf),

    NNF_LIGHT(nnfLight);

    private final Function<Formula, Formula> operation;

    Mode(Function<Formula, Formula> operation) {
      this.operation = operation;
    }
  }
}
