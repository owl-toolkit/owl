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

package owl.automaton.algorithms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import owl.automaton.AutomatonUtil;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.run.Environment;
import owl.translations.LTL2DAFunction;

class LanguageContainmentTest {

  @Test
  void contains() {
    LabelledFormula formula1 = LtlParser.parse("G F (a & b)");
    LabelledFormula formula2 = LtlParser.parse("G F a");
    LabelledFormula formula3 = LtlParser.parse("G F (X a & (a U X b))");

    var translation = new LTL2DAFunction(Environment.annotated(), EmersonLeiAcceptance.class);
    var infOftAandB
      = AutomatonUtil.cast(translation.apply(formula1), Object.class, BuchiAcceptance.class);
    var infOftA
      = AutomatonUtil.cast(translation.apply(formula2), Object.class, BuchiAcceptance.class);
    var infOftComplex
      = AutomatonUtil.cast(translation.apply(formula3), Object.class, BuchiAcceptance.class);

    assertTrue(LanguageContainment.contains(infOftAandB, infOftA));
    assertFalse(LanguageContainment.contains(infOftA, infOftAandB));

    assertTrue(LanguageContainment.contains(infOftComplex, infOftA));
    assertFalse(LanguageContainment.contains(infOftA, infOftComplex));

    assertTrue(LanguageContainment.contains(infOftAandB, infOftComplex));
    assertFalse(LanguageContainment.contains(infOftComplex, infOftAandB));
  }
}
