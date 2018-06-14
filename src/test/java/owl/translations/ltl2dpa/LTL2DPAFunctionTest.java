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

package owl.translations.ltl2dpa;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import org.hamcrest.Matchers;
import org.junit.Test;
import owl.automaton.Automaton;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.output.HoaPrinter;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.run.DefaultEnvironment;

public class LTL2DPAFunctionTest {

  private static void testOutput(String ltl, int size, int accSize) {
    LabelledFormula parseResult = LtlParser.parse(ltl);
    LTL2DPAFunction translation = new LTL2DPAFunction(DefaultEnvironment.annotated(),
      LTL2DPAFunction.RECOMMENDED_ASYMMETRIC_CONFIG);
    Automaton<?, ParityAcceptance> automaton = translation.apply(parseResult);
    String automatonString = HoaPrinter.toString(automaton);
    assertEquals(automatonString, size, automaton.size());
    assertThat(automatonString, automaton.acceptance().acceptanceSets(),
      Matchers.lessThanOrEqualTo(accSize));
  }

  @Test
  public void testRegression1() {
    String ltl = "G (F (a & (a U b)))";
    testOutput(ltl, 2, 3);
    testOutput("! " + ltl, 2, 4);
  }

  @Test
  public void testRegression2() {
    String ltl = "G (F (a & X (F b)))";
    testOutput(ltl, 2, 3);
    testOutput("! " + ltl, 2, 2);
  }

  @Test
  public void testRegression3() {
    String ltl = "F ((a | (G b)) & (c | (G d)) & (e | (G f)))";
    testOutput(ltl, 213, 3);
  }
}
