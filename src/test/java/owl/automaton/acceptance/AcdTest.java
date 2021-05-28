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

package owl.automaton.acceptance;

import java.util.EnumSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Function;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import owl.automaton.Automaton;
import owl.automaton.Views;
import owl.automaton.acceptance.transformer.ZielonkaTreeTransformations;
import owl.automaton.acceptance.transformer.ZielonkaTreeTransformations.AlternatingCycleDecomposition;
import owl.automaton.algorithm.LanguageContainment;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.translations.LtlTranslationRepository;
import owl.translations.ltl2dpa.NormalformDPAConstruction;

public class AcdTest {

  // Side-step portfolio to make acceptance conditions more interesting.
  private static Function<LabelledFormula, Automaton<?, ? extends RabinAcceptance>> LTL_TO_DRW
    = LtlTranslationRepository.LtlToDraTranslation.DEFAULT.translation(
    RabinAcceptance.class, EnumSet.noneOf(LtlTranslationRepository.Option.class));

  private static Function<LabelledFormula, Automaton<?, ?>> LTL_TO_DELW
    = LtlTranslationRepository.LtlToDelaTranslation.DEFAULT.translation(
      EnumSet.noneOf(LtlTranslationRepository.Option.class));

  @Test
  void testLtl() {
    // DBW-recognisable.
    var drw1
      = Views.dropStateLabels(LTL_TO_DRW.apply(LtlParser.parse("G F a | G F b | X X c")));
    var drw1Acd = AlternatingCycleDecomposition.of(drw1);
    var dpw1 = ZielonkaTreeTransformations.transform(drw1);

    Assertions.assertTrue(hasParityShape(drw1Acd));
    assertSuccessfulConversion(drw1, dpw1);

    // DBW-recognisable.
    var drw2
      = Views.dropStateLabels(LTL_TO_DRW.apply(
        LtlParser.parse("!(F G a | F b | X X c & F G (b | X X d))")));
    var drw2Acd
      = AlternatingCycleDecomposition.of(drw2);
    var dpw2
      = ZielonkaTreeTransformations.transform(drw2);

    assertSuccessfulConversion(drw2, dpw2);

    for (Integer state : drw2.states()) {
      for (AlternatingCycleDecomposition<Integer> acd : drw2Acd) {
        Assertions.assertTrue(hasParityShape(acd.restriction(state)));

        if (acd.edges().containsKey(state)) {
          Assertions.assertTrue(acd.restrictPathToSubtree(state, acd.leftMostLeaf(state))
            .indices().stream().allMatch(x -> x == 0));
        }
      }
    }

    assertSuccessfulConversion(drw2, dpw2);
  }

  @Test
  void testLtl2() {
    // DBW-recognisable.
    var dela1
      = Views.dropStateLabels(LTL_TO_DELW.apply(LtlParser.parse(
        "F G c & G F a | G F b | X X c & F G (a U b) & a M (a W b) | G (X X a)")));
    var dpw1 = ZielonkaTreeTransformations.transform(dela1);

    Assertions.assertTrue(LanguageContainment.contains(dela1, dpw1));
    Assertions.assertTrue(LanguageContainment.contains(dpw1, dela1));
  }

  @Test
  void testLtlZielonkaTree() {
    var formula = LtlParser.parse("((GF a) & (GF b)) <-> GF c");
    var automaton = new NormalformDPAConstruction(OptionalInt.empty()).apply(formula);

    for (var state : automaton.states()) {
      var acd = (AlternatingCycleDecomposition) automaton.lookup(state);
      acd.restrictPathToSubtree(state.state(), state.path());
    }
  }

  static boolean hasParityShape(
    List<? extends AlternatingCycleDecomposition<?>> acdList) {
    return acdList.stream().allMatch(AcdTest::hasParityShape);
  }

  static <S> boolean hasParityShape(AlternatingCycleDecomposition<S> acd) {
    return acd.children().isEmpty()
      || (acd.children().size() == 1 && hasParityShape(acd.children()));
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

    Assertions.assertEquals(drw.states().size(), dpw.states().size());

    Assertions.assertTrue(LanguageContainment.contains(dpw, drw));
    Assertions.assertTrue(LanguageContainment.contains(drw, dpw));
  }
}
