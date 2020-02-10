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

package owl.translations.nba2dpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static owl.util.Assertions.assertThat;

import java.util.stream.Stream;
import jhoafparser.consumer.HOAConsumerNull;
import jhoafparser.consumer.HOAIntermediateCheckValidity;
import jhoafparser.parser.generated.ParseException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.OmegaAcceptanceCast;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.hoa.HoaReader;
import owl.automaton.hoa.HoaWriter;
import owl.run.Environment;

@SuppressWarnings("PMD.UnusedPrivateMethod")
class NBA2DPATest {

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
    + "States: 1\n"
    + "Start: 0\n"
    + "acc-name: Buchi\n"
    + "Acceptance: 1 Inf(0)\n"
    + "AP: 1 \"a\"\n"
    + "--BODY--\n"
    + "State: 0\n"
    + "[t] 0 {0}\n"
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
    + "[!1] 2\n"
    + "State: 2\n"
    + "[0 & 1] 1 {0}\n"
    + "[!0 & 1] 2 {0}\n"
    + "--END--";

  private static final String INPUT4 = "HOA: v1\n"
    + "States: 2\n"
    + "Start: 0\n"
    + "acc-name: Buchi\n"
    + "Acceptance: 1 Inf(0)\n"
    + "properties: trans-acc trans-label\n"
    + "AP: 1 \"a\"\n"
    + "--BODY--\n"
    + "State: 0\n"
    + "[!0] 0\n"
    + "[0] 1\n"
    + "State: 1\n"
    + "[t] 1 {0}\n"
    + "--END--";

  private static final String INPUT5 = "HOA: v1\n"
    + "States: 2\n"
    + "Start: 0\n"
    + "acc-name: Buchi\n"
    + "Acceptance: 1 Inf(0)\n"
    + "properties: trans-acc trans-label \n"
    + "AP: 1 \"a\"\n"
    + "--BODY--\n"
    + "State: 1 \"0\"\n"
    + "[0] 1 {0}\n"
    + "State: 0 \"1\"\n"
    + "[0] 1\n"
    + "[t] 0\n"
    + "--END--";

  private static final String INPUT6 = "HOA: v1\n"
    + "States: 1\n"
    + "Start: 0\n"
    + "acc-name: Buchi\n"
    + "Acceptance: 1 Inf(0)\n"
    + "properties: trans-acc trans-label \n"
    + "AP: 1 \"a\"\n"
    + "--BODY--\n"
    + "State: 0 \n"
    + "[0] 0\n"
    + "[!0] 0 {0}\n"
    + "--END--";

  private static final String INPUT7 = "HOA: v1\n"
    + "States: 2\n"
    + "Start: 0\n"
    + "acc-name: Buchi\n"
    + "Acceptance: 1 Inf(0)\n"
    + "properties: trans-acc trans-label \n"
    + "AP: 2 \"a\" \"b\"\n"
    + "--BODY--\n"
    + "State: 0 \n"
    + "[t] 1\n"
    + "State: 1\n"
    + "[!1] 1 {0}\n"
    + "[!0 & 1] 1"
    + "--END--";

  private static final String INPUT8 = "HOA: v1\n"
    + "States: 1\n"
    + "Start: 0\n"
    + "acc-name: generalized-Buchi 3\n"
    + "Acceptance: 3 Inf(0) & Inf(1) & Inf(2)\n"
    + "properties: trans-acc trans-label \n"
    + "properties: deterministic \n"
    + "AP: 3 \"a\" \"b\" \"c\"\n"
    + "--BODY--\n"
    + "State: 0\n"
    + "[0 & 1 & 2] 0\n"
    + "[!0 & 1 & 2] 0 {0}\n"
    + "[0 & !1 & 2] 0 {1}\n"
    + "[!0 & !1 & 2] 0 {0 1}\n"
    + "[0 & 1 & !2] 0 {2}\n"
    + "[!0 & 1 & !2] 0 {0 2}\n"
    + "[0 & !1 & !2] 0 {1 2}\n"
    + "[!0 & !1 & !2] 0 {0 1 2}\n"
    + "--END--";

  private static final String INPUT9 = "HOA: v1\n"
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

  private static final String INPUT10 = "HOA: v1\n"
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

  private static final String INPUT15 = "HOA: v1\n"
    + "tool: \"Owl\" \"* *\"\n"
    + "States: 4\n"
    + "Start: 0\n"
    + "acc-name: Buchi\n"
    + "Acceptance: 1 Inf(0)\n"
    + "properties: trans-acc trans-label \n"
    + "AP: 3 \"a\" \"b\" \"c\"\n"
    + "--BODY--\n"
    + "State: 1\n"
    + "[0 & 2] 2 {0}\n"
    + "[!0 & 1 & 2] 1 {0}\n"
    + "[!0 & 2] 3\n"
    + "State: 0\n"
    + "[t] 0\n"
    + "[0] 2\n"
    + "[!0 & 1] 1\n"
    + "State: 3\n"
    + "[1] 1 {0}\n"
    + "[t] 3\n"
    + "State: 2\n"
    + "[0] 2 {0}\n"
    + "[!0 & 1] 1 {0}\n"
    + "[!0] 3\n"
    + "--END--";

  private static final String INPUT16 = "HOA: v1\n"
    + "tool: \"Owl\" \"* *\"\n"
    + "States: 5\n"
    + "Start: 0\n"
    + "acc-name: Buchi\n"
    + "Acceptance: 1 Inf(0)\n"
    + "properties: trans-acc trans-label \n"
    + "AP: 3 \"c\" \"a\" \"b\"\n"
    + "--BODY--\n"
    + "State: 1\n"
    + "[0 & 1 & !2] 2 {0}\n"
    + "[!0 & 1 & !2] 1 {0}\n"
    + "[!0 & 1 & !2] 3 {0}\n"
    + "[!0 & 1 & !2] 4\n"
    + "State: 0\n"
    + "[t] 0\n"
    + "[0] 2\n"
    + "[!0] 1\n"
    + "[!0] 3\n"
    + "State: 3\n"
    + "[0 & 2] 2 {0}\n"
    + "[!0 & 2] 1 {0}\n"
    + "[!0 & 2] 3 {0}\n"
    + "[!0 & 2] 4\n"
    + "State: 4\n"
    + "[0 & !2] 2 {0}\n"
    + "[!0 & !2] 3 {0}\n"
    + "[!0 & !2] 4\n"
    + "State: 2\n"
    + "[0] 2 {0}\n"
    + "[!0] 1 {0}\n"
    + "[!0] 3 {0}\n"
    + "[!0] 4\n"
    + "--END--";

  private static final String INPUT17 = "HOA: v1\n"
    + "tool: \"Owl\" \"* *\"\n"
    + "States: 2\n"
    + "Start: 0\n"
    + "acc-name: Buchi\n"
    + "Acceptance: 1 Inf(0)\n"
    + "properties: trans-acc trans-label \n"
    + "AP: 2 \"a\" \"b\"\n"
    + "--BODY--\n"
    + "State: 1\n"
    + "[0 & 1] 1 {0}\n"
    + "State: 0\n"
    + "[t] 0\n"
    + "[0 & 1] 1\n"
    + "--END--";

  private static Stream<Arguments> testCases() {
    return Stream.of(
      Arguments.of(INPUT, 5),
      Arguments.of(INPUT2, 1),
      Arguments.of(INPUT3, 4),
      Arguments.of(INPUT4, 2),
      Arguments.of(INPUT5, 2),
      Arguments.of(INPUT6, 1),
      Arguments.of(INPUT7, 2),
      Arguments.of(INPUT8, 7),
      Arguments.of(INPUT9, 3),
      Arguments.of(INPUT10, 6),
      Arguments.of(INPUT15, 12),
      Arguments.of(INPUT16, 7),
      Arguments.of(INPUT17, 2));
  }

  @ParameterizedTest
  @MethodSource("testCases")
  void runTest(String input, int size) throws ParseException {
    var nba = OmegaAcceptanceCast.cast(HoaReader
        .read(input, Environment.standard().factorySupplier()::getValuationSetFactory),
      GeneralizedBuchiAcceptance.class);
    var dpa = new NBA2DPA().apply(nba);

    assertThat(dpa.acceptance(), ParityAcceptance.class::isInstance);
    assertEquals(size, dpa.size());
    HoaWriter.write(dpa, new HOAIntermediateCheckValidity(new HOAConsumerNull()));
  }
}
