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

package owl.ltl.algorithms;

import java.util.EnumSet;
import java.util.stream.IntStream;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.algorithm.LanguageEmptiness;
import owl.ltl.Biconditional;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.translations.LtlTranslationRepository;

public final class LanguageAnalysis {

  private LanguageAnalysis() {}

  public static boolean isSatisfiable(Formula formula) {
    if (formula instanceof Disjunction) {
      return formula.operands.stream().anyMatch(LanguageAnalysis::isSatisfiable);
    }

    var labelledFormula = attachDummyAlphabet(formula);
    var translation = LtlTranslationRepository.defaultTranslation(
      EnumSet.of(
        LtlTranslationRepository.Option.SIMPLIFY_FORMULA,
        LtlTranslationRepository.Option.USE_PORTFOLIO_FOR_SYNTACTIC_LTL_FRAGMENTS),
      LtlTranslationRepository.BranchingMode.NON_DETERMINISTIC,
      GeneralizedBuchiAcceptance.class);

    return !LanguageEmptiness.isEmpty(translation.apply(labelledFormula));
  }

  public static boolean isUnsatisfiable(Formula formula) {
    return !isSatisfiable(formula);
  }

  public static boolean isUniversal(Formula formula) {
    return isUnsatisfiable(formula.not());
  }

  public static boolean isEqual(Formula formula1, Formula formula2) {
    return !isSatisfiable(Biconditional.of(formula1, formula2.not()));
  }

  private static LabelledFormula attachDummyAlphabet(Formula formula) {
    return LabelledFormula.of(formula, IntStream
      .range(0, formula.atomicPropositions(true).length())
      .mapToObj(Integer::toString)
      .toList());
  }
}
