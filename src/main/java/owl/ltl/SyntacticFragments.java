/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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

import java.util.function.Function;
import owl.ltl.visitors.UnabbreviateVisitor;
import owl.ltl.visitors.Visitor;

public final class SyntacticFragments {
  private static final Visitor<Formula> UNABBREVIATE_VISITOR =
    new UnabbreviateVisitor(WOperator.class, ROperator.class);

  private SyntacticFragments() {}

  public static boolean isAlmostAll(Formula formula) {
    return formula instanceof FOperator && ((FOperator) formula).operand instanceof GOperator;
  }

  public static boolean isDetBuchiRecognisable(Formula formula) {
    if (formula instanceof XOperator) {
      return isDetCoBuchiRecognisable(((XOperator) formula).operand);
    }

    return formula instanceof GOperator && SyntacticFragment.CO_SAFETY
      .contains(((GOperator) formula).operand);
  }

  public static boolean isDetCoBuchiRecognisable(Formula formula) {
    if (formula instanceof XOperator) {
      return isDetBuchiRecognisable(((XOperator) formula).operand);
    }

    return formula instanceof FOperator && SyntacticFragment.SAFETY
      .contains(((FOperator) formula).operand);
  }

  public static boolean isInfinitelyOften(Formula formula) {
    return formula instanceof GOperator && ((GOperator) formula).operand instanceof FOperator;
  }

  public static boolean isGfCoSafety(Formula formula) {
    if (formula instanceof GOperator) {
      Formula unwrapped = ((GOperator) formula).operand;
      return unwrapped instanceof FOperator && SyntacticFragment.CO_SAFETY.contains(unwrapped);
    }

    return false;
  }

  public static boolean isGCoSafety(Formula formula) {
    if (formula instanceof GOperator) {
      Formula unwrapped = ((GOperator) formula).operand;
      return SyntacticFragment.CO_SAFETY.contains(unwrapped);
    }

    return false;
  }

  public static boolean isFgSafety(Formula formula) {
    if (formula instanceof FOperator) {
      Formula unwrapped = ((FOperator) formula).operand;
      return unwrapped instanceof GOperator && SyntacticFragment.SAFETY.contains(unwrapped);
    }

    return false;
  }

  public static boolean isFSafety(Formula formula) {
    if (formula instanceof FOperator) {
      Formula unwrapped = ((FOperator) formula).operand;
      return SyntacticFragment.SAFETY.contains(unwrapped);
    }

    return false;
  }

  public static boolean isCoSafety(Iterable<? extends Formula> iterable) {
    for (var formula : iterable) {
      if (!SyntacticFragment.CO_SAFETY.contains(formula)) {
        return false;
      }
    }

    return true;
  }

  public static boolean isSafety(Iterable<? extends Formula> iterable) {
    for (var formula : iterable) {
      if (!SyntacticFragment.SAFETY.contains(formula)) {
        return false;
      }
    }

    return true;
  }

  public static boolean isGPast(Formula formula) {
    if (formula instanceof GOperator) {
      Formula unwrapped = ((GOperator) formula).operand;
      return SyntacticFragment.PAST_SAFETY.contains(unwrapped);
    }

    return false;
  }

  public static boolean isFPast(Formula formula) {
    if (formula instanceof FOperator) {
      Formula unwrapped = ((FOperator) formula).operand;
      return SyntacticFragment.PAST_SAFETY.contains(unwrapped);
    }

    return false;
  }

  private static Formula normalize(Formula formula, SyntacticFragment fragment,
    Function<Formula, Formula> normalizer) {
    Formula normalizedFormula = normalizer.apply(formula);

    if (!fragment.contains(normalizedFormula)) {
      throw new IllegalArgumentException("Unsupported formula object found in " + normalizedFormula
        + ". Supported classes are: " + fragment.classes());
    }

    return normalizedFormula;
  }

  public static Formula normalize(Formula formula, SyntacticFragment fragment) {
    switch (fragment) {
      case ALL:
        return formula;

      case NNF:
        return normalize(formula, SyntacticFragment.NNF, Formula::nnf);

      case FGMU:
        return normalize(formula, SyntacticFragment.FGMU,
          x -> x.nnf().accept(UNABBREVIATE_VISITOR));

      default:
        throw new UnsupportedOperationException();
    }
  }

  public static LabelledFormula normalize(LabelledFormula formula, SyntacticFragment fragment) {
    return formula.wrap(normalize(formula.formula(), fragment));
  }
}
