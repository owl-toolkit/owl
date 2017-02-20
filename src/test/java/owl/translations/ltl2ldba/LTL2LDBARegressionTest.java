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

package owl.translations.ltl2ldba;

import static org.junit.Assert.assertEquals;

import java.util.EnumSet;
import org.junit.Test;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.ltl.parser.LtlParseResult;
import owl.ltl.parser.LtlParser;
import owl.ltl.parser.ParseException;
import owl.translations.Optimisation;

public class LTL2LDBARegressionTest {

  static void testOutput(String ltl, int size) throws ParseException {
    EnumSet<Optimisation> opts = EnumSet.allOf(Optimisation.class);
    LTL2LDBA translation = new LTL2LDBA(opts);
    LtlParseResult parseResult = LtlParser.parse(ltl);
    LimitDeterministicAutomaton<InitialComponentState, AcceptingComponent.State, BuchiAcceptance,
      InitialComponent<AcceptingComponent.State, RecurringObligations>, AcceptingComponent>
      automaton = translation.apply(parseResult.getFormula());
    automaton.setVariables(parseResult.getVariableMapping());
    String hoaString = automaton.toString();
    assertEquals(hoaString, size, automaton.size());
  }

  @Test
  public void testLivenessRegression() throws ParseException {
    String ltl = "G (F (a & (F b)))";
    testOutput(ltl, 2);
  }

  @Test
  public void testLivenessRegression2() throws ParseException {
    String ltl = "G (F a & F b)";
    testOutput(ltl, 2);
  }

  @Test
  public void testObligationSizeRegression() throws ParseException {
    String ltl = "G F (b & G b)";
    testOutput(ltl, 2);
  }

  @Test
  public void testRegression7() throws ParseException {
    String ltl = "(X(p1)) R (((G(p2)) R (p3)) W (p4))";
    testOutput(ltl, 24);
  }
}