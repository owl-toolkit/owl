/*
 * Copyright (C) 2021, 2022  (Salomon Sickert)
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

package owl.translations;

import static owl.translations.LtlTranslationRepository.BranchingMode;
import static owl.translations.LtlTranslationRepository.defaultTranslation;

import java.util.EnumSet;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import owl.automaton.Automaton;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.hoa.HoaWriter;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.ltl.parser.LtlfParser;
import owl.translations.LtlTranslationRepository.LtlToDraTranslation;

public class LtlTranslationRepositoryTest {

  @Test
  public void testPrismIntegration() {
    Assertions.assertDoesNotThrow(() -> {
      LabelledFormula formula = LtlfParser.parse("! F(p0 & Xp1)");
      Function<LabelledFormula, Automaton<?, ? extends RabinAcceptance>> func
          = defaultTranslation(EnumSet.of(
              LtlTranslationRepository.Option.COMPLETE,
              LtlTranslationRepository.Option.SIMPLIFY_FORMULA,
              LtlTranslationRepository.Option.SIMPLIFY_AUTOMATON,
              LtlTranslationRepository.Option.USE_PORTFOLIO_FOR_SYNTACTIC_LTL_FRAGMENTS),
          BranchingMode.DETERMINISTIC, RabinAcceptance.class);
      Automaton<?, ? extends RabinAcceptance> aut = func.apply(formula);
      Assertions.assertEquals(3, aut.states().size());
      Assertions.assertTrue(aut.states().contains(Optional.empty()));
      HoaWriter.toString(aut);
    });
  }

  @Test
  public void testFormulaRegression() {
    LabelledFormula formula = LtlParser.parse("((Fb) R ((b W a) U b)) W a");

    Assertions.assertDoesNotThrow(() -> {
      LtlToDraTranslation.SE20.translation().apply(formula);
    });
  }
}
