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

package owl.translations.nba2ldba;

import java.util.EnumSet;
import jhoafparser.consumer.HOAConsumerNull;
import jhoafparser.consumer.HOAIntermediateCheckValidity;
import jhoafparser.parser.generated.ParseException;
import org.junit.Test;
import owl.automaton.Automaton;
import owl.automaton.AutomatonReader;
import owl.automaton.AutomatonReader.HoaState;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.ldba.LimitDeterministicAutomatonBuilder.Configuration;
import owl.automaton.output.HoaPrinter;
import owl.run.DefaultEnvironment;

public class NBA2LDBATest {

  private static final String INPUT = "HOA: v1\n"
    + "States: 2\n"
    + "Start: 0\n"
    + "acc-name: Buchi\n"
    + "Acceptance: 1 Inf(0)\n"
    + "AP: 1 \"a\"\n"
    + "--BODY--\n"
    + "State: 0 {0}\n"
    + " [0]   1 \n"
    + "State: 1 \n"
    + " [t]   0 \n"
    + " [!0]  1 \n"
    + "--END--";

  private static final String INPUT2 = "HOA: v1\n"
    + "States: 2\n"
    + "Start: 0\n"
    + "acc-name: Buchi\n"
    + "Acceptance: 1 Inf(0)\n"
    + "AP: 1 \"a\"\n"
    + "--BODY--\n"
    + "State: 0 {0}\n"
    + " [0]   1 \n"
    + "State: 1 \n"
    + " [!0]  0 \n"
    + " [!0]  1 \n"
    + "--END--";

  private static final String INPUT3 = "HOA: v1\n"
    + "States: 3\n"
    + "Start: 0\n"
    + "acc-name: Buchi\n"
    + "Acceptance: 1 Inf(0)\n"
    + "properties: trans-acc trans-label\n"
    + "AP: 2 \"a\" \"b\"\n"
    + "--BODY--\n"
    + "State: 1\n"
    + "[0] 1 {0}\n"
    + "[!0] 2 {0}\n"
    + "State: 0\n"
    + "[0] 1\n"
    + "[t] 0\n"
    + "[!0] 2\n"
    + "State: 2\n"
    + "[0 & 1] 1 {0}\n"
    + "[!0 & 1] 2 {0}\n"
    + "--END--";

  private static final String INPUT4 = "HOA: v1\n"
    + "States: 1\n"
    + "Start: 0\n"
    + "acc-name: Buchi\n"
    + "Acceptance: 1 Inf(0)\n"
    + "properties: trans-acc trans-label\n"
    + "AP: 0\n"
    + "--BODY--\n"
    + "State: 0\n"
    + "--END--";

  @Test
  public void testApply() throws ParseException {
    runTest(INPUT);
  }

  @Test
  public void testApply2() throws ParseException {
    runTest(INPUT2);
  }

  @Test
  public void testApply3() throws ParseException {
    runTest(INPUT3);
  }

  @Test
  public void testApply4() throws ParseException {
    runTest(INPUT4);
  }

  private void runTest(String input) throws ParseException {
    var translation = new NBA2LDBA(false, EnumSet.of(Configuration.REMOVE_EPSILON_TRANSITIONS));

    Automaton<HoaState, GeneralizedBuchiAcceptance> automaton = AutomatonReader.readHoa(input,
      DefaultEnvironment.annotated().factorySupplier(), GeneralizedBuchiAcceptance.class);

    HoaPrinter.feedTo(automaton, new HOAIntermediateCheckValidity(new HOAConsumerNull()));
    HoaPrinter.feedTo(translation.apply(automaton),
      new HOAIntermediateCheckValidity(new HOAConsumerNull()));
  }
}
