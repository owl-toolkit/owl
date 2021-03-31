/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
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

package owl.automaton.typeness;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import jhoafparser.parser.generated.ParseException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import owl.automaton.Automaton;
import owl.automaton.acceptance.OmegaAcceptanceCast;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.algorithm.LanguageContainment;
import owl.automaton.hoa.HoaReader;
import owl.bdd.FactorySupplier;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.translations.LtlTranslationRepository;

class ParityTypenessTest {

  // Side-step portfolio to make acceptance conditions more interesting.
  private static Function<LabelledFormula, Automaton<?, ? extends RabinAcceptance>> LTL_TO_DRW
    = LtlTranslationRepository.LtlToDraTranslation.DEFAULT.translation(
      RabinAcceptance.class, EnumSet.noneOf(LtlTranslationRepository.Option.class));

  private static final String AUTOMATON_1 = "HOA: v1\n"
    + "tool: \"owl\" \"development\"\n"
    + "Start: 0\n"
    + "acc-name: Rabin 1\n"
    + "Acceptance: 2 Fin(0) & Inf(1)\n"
    + "properties: deterministic \n"
    + "properties: trans-acc trans-label \n"
    + "AP: 1 \"a\"\n"
    + "--BODY--\n"
    + "State: 0 \n"
    + "[t] 1\n"
    + "State: 2 \n"
    + "[0] 2 {1}\n"
    + "State: 1 \n"
    + "[t] 2\n"
    + "--END--";

  private static final String AUTOMATON_2 = "HOA: v1\n"
    + "tool: \"owl\" \"development\"\n"
    + "Start: 0\n"
    + "acc-name: Rabin 1\n"
    + "Acceptance: 2 Fin(0) & Inf(1)\n"
    + "properties: deterministic \n"
    + "properties: trans-acc trans-label \n"
    + "AP: 3 \"a\" \"b\" \"c\"\n"
    + "--BODY--\n"
    + "State: 0\n"
    + "[!0] 1\n"
    + "[0 & !1] 0\n"
    + "[0 & 1] 2\n"
    + "State: 1 \"1\" \n"
    + "[0] 1 \n"
    + "[!0 & 1] 3 {0}\n"
    + "State: 2\n"
    + "[!0] 1\n"
    + "[0 & !1 & !2] 0\n"
    + "[0 & 1 & !2] 2\n"
    + "[0 & !1 & 2] 0 {1}\n"
    + "[0 & 1 & 2] 2 {1}\n"
    + "State: 3 \"3\" \n"
    + "[!0 & !1 & !2] 1\n"
    + "[0] 1 {0}\n"
    + "[!0 & 1 & !2] 3\n"
    + "[!0 & !1 & 2] 1 {1}\n"
    + "[!0 & 1 & 2] 3 {1}\n"
    + "--END--";

  @Test
  void hopelessStates() throws ParseException {

    var automaton1 = OmegaAcceptanceCast.cast(
      HoaReader.read(AUTOMATON_1, FactorySupplier.defaultSupplier()::getBddSetFactory, null),
      RabinAcceptance.class);

    var hopelessStates1 = ParityTypeness.hopelessStates(automaton1)
      .stream().map(x -> x.id).collect(Collectors.toSet());

    Assertions.assertEquals(Set.of(0, 1), hopelessStates1);

    var automaton2 = OmegaAcceptanceCast.cast(
      HoaReader.read(AUTOMATON_2, FactorySupplier.defaultSupplier()::getBddSetFactory, null),
      RabinAcceptance.class);

    var hopelessStates2 = ParityTypeness.hopelessStates(automaton2)
      .stream().map(x -> x.id).collect(Collectors.toSet());

    // TODO: check transition relation.
    Assertions.assertEquals(Set.of(0, 1, 2, 3), hopelessStates2);
  }

  @Test
  void testLtl() {
    // DBW-recognisable.
    var drw1 = LTL_TO_DRW.apply(LtlParser.parse("G F a | G F b | X X c"));
    var dpw1 = ParityTypeness.apply(drw1).orElseThrow();

    assertSuccessfulConversion(drw1, dpw1);

    // DCW-recognisable.
    var drw2 = LTL_TO_DRW.apply(LtlParser.parse("F G a | F b | X X c & F G (b | X X d)"));
    var dpw2 = ParityTypeness.apply(drw2).orElseThrow();

    assertSuccessfulConversion(drw2, dpw2);
  }

  void assertSuccessfulConversion(
    Automaton<?, ? extends RabinAcceptance> drw,
    Automaton<?, ? extends ParityAcceptance> dpw) {

    Assertions.assertTrue(dpw.acceptance() instanceof ParityAcceptance);
    Assertions.assertTrue(dpw.acceptance().isWellFormedAutomaton(dpw));

    Assertions.assertTrue(drw.is(Automaton.Property.DETERMINISTIC));
    Assertions.assertTrue(dpw.is(Automaton.Property.DETERMINISTIC));

    Assertions.assertEquals(
      drw.is(Automaton.Property.COMPLETE),
      dpw.is(Automaton.Property.COMPLETE));

    Assertions.assertEquals(drw.states(), dpw.states());

    Assertions.assertTrue(LanguageContainment.contains(drw, dpw));
    Assertions.assertTrue(LanguageContainment.contains(dpw, drw));
  }
}