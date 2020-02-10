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

package owl.translations.nba2ldba;

import java.io.StringReader;
import jhoafparser.consumer.HOAConsumerNull;
import jhoafparser.consumer.HOAIntermediateCheckValidity;
import jhoafparser.parser.generated.ParseException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.OmegaAcceptanceCast;
import owl.automaton.hoa.HoaReader;
import owl.automaton.hoa.HoaWriter;
import owl.run.Environment;

class NBA2LDBATest {

  private static final String ALL_ACCEPTANCE = "HOA: v1\n"
    + "States: 1\n"
    + "Start: 0\n"
    + "acc-name: all\n"
    + "Acceptance: 0 t\n"
    + "AP: 1 \"a\"\n"
    + "--BODY--\n"
    + "State: 0\n"
    + " [0]   0\n"
    + "--END--";

  private static final String BUCHI_1 = "HOA: v1\n"
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

  private static final String BUCHI_2 = "HOA: v1\n"
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

  private static final String BUCHI_3 = "HOA: v1\n"
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

  private static final String BUCHI_4 = "HOA: v1\n"
    + "States: 1\n"
    + "Start: 0\n"
    + "acc-name: Buchi\n"
    + "Acceptance: 1 Inf(0)\n"
    + "properties: trans-acc trans-label\n"
    + "AP: 0\n"
    + "--BODY--\n"
    + "State: 0\n"
    + "--END--";

  @ParameterizedTest
  @ValueSource(strings = {ALL_ACCEPTANCE, BUCHI_1, BUCHI_2, BUCHI_3, BUCHI_4})
  void runTest(String hoa) throws ParseException {
    var supplier = Environment.annotated().factorySupplier();
    var nba = OmegaAcceptanceCast
      .cast(HoaReader.read(new StringReader(hoa), supplier::getValuationSetFactory),
        OmegaAcceptance.class);
    var ldba = new NBA2LDBA().apply(nba);
    HoaWriter.write(nba, new HOAIntermediateCheckValidity(new HOAConsumerNull()));
    HoaWriter.write(ldba, new HOAIntermediateCheckValidity(new HOAConsumerNull()));
  }
}
