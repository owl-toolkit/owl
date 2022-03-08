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

package owl.translations.nba2ldba;

import java.io.StringReader;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.acceptance.OmegaAcceptanceCast;
import owl.automaton.hoa.HoaReader;
import owl.automaton.hoa.HoaWriter;
import owl.bdd.FactorySupplier;
import owl.thirdparty.jhoafparser.consumer.HOAConsumerNull;
import owl.thirdparty.jhoafparser.consumer.HOAIntermediateCheckValidity;
import owl.thirdparty.jhoafparser.parser.generated.ParseException;

class NBA2LDBATest {

  private static final String ALL_ACCEPTANCE = """
    HOA: v1
    States: 1
    Start: 0
    acc-name: all
    Acceptance: 0 t
    AP: 1 "a"
    --BODY--
    State: 0
     [0]   0
    --END--""";

  private static final String BUCHI_1 = """
    HOA: v1
    States: 2
    Start: 0
    acc-name: Buchi
    Acceptance: 1 Inf(0)
    AP: 1 "a"
    --BODY--
    State: 0 {0}
     [0]   1\s
    State: 1\s
     [t]   0\s
     [!0]  1\s
    --END--""";

  private static final String BUCHI_2 = """
    HOA: v1
    States: 2
    Start: 0
    acc-name: Buchi
    Acceptance: 1 Inf(0)
    AP: 1 "a"
    --BODY--
    State: 0 {0}
     [0]   1\s
    State: 1\s
     [!0]  0\s
     [!0]  1\s
    --END--""";

  private static final String BUCHI_3 = """
    HOA: v1
    States: 3
    Start: 0
    acc-name: Buchi
    Acceptance: 1 Inf(0)
    properties: trans-acc trans-label
    AP: 2 "a" "b"
    --BODY--
    State: 1
    [0] 1 {0}
    [!0] 2 {0}
    State: 0
    [0] 1
    [t] 0
    [!0] 2
    State: 2
    [0 & 1] 1 {0}
    [!0 & 1] 2 {0}
    --END--""";

  private static final String BUCHI_4 = """
    HOA: v1
    States: 1
    Start: 0
    acc-name: Buchi
    Acceptance: 1 Inf(0)
    properties: trans-acc trans-label
    AP: 0
    --BODY--
    State: 0
    --END--""";

  @ParameterizedTest
  @ValueSource(strings = {ALL_ACCEPTANCE, BUCHI_1, BUCHI_2, BUCHI_3, BUCHI_4})
  void runTest(String hoa) throws ParseException {

    var supplier = FactorySupplier.defaultSupplier();
    var nba = OmegaAcceptanceCast
      .cast(HoaReader.read(new StringReader(hoa), supplier::getBddSetFactory, null),
        EmersonLeiAcceptance.class);
    var ldba = new NBA2LDBA().apply(nba);

    HoaWriter.write(
      nba, new HOAIntermediateCheckValidity(new HOAConsumerNull()), true);

    HoaWriter.write(
      ldba, new HOAIntermediateCheckValidity(new HOAConsumerNull()), true);
  }
}
