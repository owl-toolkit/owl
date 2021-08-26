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

public enum SimplifierRepository {

  SYNTACTIC,
  SYNTACTIC_FAIRNESS,
  SYNTACTIC_FIXPOINT,
  PULL_UP_X,
  PUSH_DOWN_X,
  NNF;

  public Formula apply(Formula formula) {
    switch (this) {
      case SYNTACTIC:
        return ((UnaryOperator<Formula>) x -> x.substitute(Formula::nnf))
          .andThen(PropositionalSimplifier.INSTANCE)
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
          before = after.accept(PropositionalSimplifier.INSTANCE);
          after = SyntacticSimplifier.INSTANCE.apply(before)
            .accept(PullUpXVisitor.INSTANCE).toFormula();
        }

        return after;

      case PULL_UP_X:
        return formula.accept(PullUpXVisitor.INSTANCE).toFormula();

      case PUSH_DOWN_X:
        return formula.accept(PushDownXVisitor.INSTANCE, 0);

      case NNF:
        return formula.nnf();

      default:
        throw new AssertionError("unreachable");
    }
  }

  public LabelledFormula apply(LabelledFormula formula) {
    return formula.wrap(apply(formula.formula()));
  }
}
