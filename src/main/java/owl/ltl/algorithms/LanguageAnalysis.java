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

package owl.ltl.algorithms;

import static owl.translations.LTL2DAFunction.Constructions.BUCHI;
import static owl.translations.LTL2DAFunction.Constructions.CO_BUCHI;
import static owl.translations.LTL2DAFunction.Constructions.CO_SAFETY;
import static owl.translations.LTL2DAFunction.Constructions.GENERALIZED_RABIN;
import static owl.translations.LTL2DAFunction.Constructions.SAFETY;

import java.util.EnumSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import owl.automaton.algorithms.LanguageEmptiness;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.run.DefaultEnvironment;
import owl.translations.LTL2DAFunction;

public final class LanguageAnalysis {

  private LanguageAnalysis() {}

  public static boolean isSatisfiable(Formula formula) {
    if (formula instanceof Disjunction) {
      return ((Disjunction) formula).children.stream().anyMatch(LanguageAnalysis::isSatisfiable);
    }

    var labelledFormula = attachDummyAlphabet(formula);
    var translation = new LTL2DAFunction(DefaultEnvironment.of(false), true,
      EnumSet.of(SAFETY, CO_SAFETY, BUCHI, CO_BUCHI, GENERALIZED_RABIN));
    return !LanguageEmptiness.isEmpty(translation.apply(labelledFormula));
  }

  public static boolean isUnsatisfiable(Formula formula) {
    return !isSatisfiable(formula);
  }

  public static boolean isUniversal(Formula formula) {
    return isUnsatisfiable(formula.not());
  }

  private static LabelledFormula attachDummyAlphabet(Formula formula) {
    return LabelledFormula.of(formula, IntStream
      .range(0, formula.atomicPropositions(true).length())
      .mapToObj(i -> "p" + i)
      .collect(Collectors.toUnmodifiableList()));
  }
}
