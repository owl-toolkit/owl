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

package owl.translations.nba2dpa;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jhoafparser.consumer.HOAConsumerNull;
import jhoafparser.consumer.HOAIntermediateCheckValidity;
import jhoafparser.parser.generated.ParseException;
import org.junit.jupiter.api.Test;
import owl.automaton.AutomatonReader;
import owl.automaton.AutomatonUtil;
import owl.automaton.Views;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.algorithms.EmptinessCheck;
import owl.automaton.output.HoaPrinter;
import owl.run.DefaultEnvironment;

class TestHasAcceptingRun {

  private static final String INPUT1 = "HOA: v1\n"
    + "States: 2\n"
    + "Start: 0\n"
    + "acc-name: Buchi\n"
    + "Acceptance: 1 Inf(0)\n"
    + "properties: trans-acc trans-label \n"
    + "AP: 2 \"a\" \"b\"\n"
    + "--BODY--\n"
    + "State: 0 \n"
    + "[t] 0\n"
    + "[!0] 1 {0}"
    + "State: 1\n"
    + "[!1] 0 \n"
    + "[!0 & !1] 1 {0}"
    + "--END--";

  private static final String INPUT2 = "HOA: v1\n"
    + "States: 4\n"
    + "Start: 0\n"
    + "acc-name: Buchi\n"
    + "Acceptance: 1 Inf(0)\n"
    + "properties: trans-acc trans-label \n"
    + "AP: 2 \"a\" \"b\"\n"
    + "--BODY--\n"
    + "State: 0 \n"
    + "[t] 1 {0}\n"
    + "[0] 2 {0}\n"
    + "State: 2 \n"
    + "[0] 2 {0}\n"
    + "State: 3 \n"
    + "[t] 3 {0}\n"
    + "State: 1 \n"
    + "[1] 3 {0}\n"
    + "--END--";

  private static final String INPUT3 = "HOA: v1\n"
    + "States: 2\n"
    + "Start: 0\n"
    + "acc-name: Buchi\n"
    + "Acceptance: 1 Inf(0)\n"
    + "properties: trans-acc trans-label \n"
    + "AP: 1 \"a\" \n"
    + "--BODY--\n"
    + "State: 0 \n"
    + "[t] 1 \n"
    + "State: 1 \n"
    + "[t] 0 \n"
    + "--END--";

  @Test
  void testHasAcceptingRun() throws ParseException {
    testHasAcceptingRun(INPUT1, true, true);
  }

  @Test
  void testHasAcceptingRun2() throws ParseException {
    testHasAcceptingRun(INPUT2, true, true);
  }

  @Test
  void testHasAcceptingRun3() throws ParseException {
    testHasAcceptingRun(INPUT3, false, true);
  }

  private static void testHasAcceptingRun(String input, boolean hasAcceptingRun,
    boolean complementHasAcceptingRun) throws ParseException {
    var nba = AutomatonReader.readHoa(input, DefaultEnvironment.annotated()
      .factorySupplier()::getValuationSetFactory, GeneralizedBuchiAcceptance.class);
    var dpa = new NBA2DPA().apply(nba);

    HoaPrinter.feedTo(dpa, new HOAIntermediateCheckValidity(new HOAConsumerNull()));
    assertEquals(EmptinessCheck.isEmpty(dpa), !hasAcceptingRun);

    var complement = Views.complement(AutomatonUtil.cast(dpa), new Object());
    HoaPrinter.feedTo(complement, new HOAIntermediateCheckValidity(new HOAConsumerNull()));
    assertEquals(EmptinessCheck.isEmpty(complement), !complementHasAcceptingRun);
  }
}
