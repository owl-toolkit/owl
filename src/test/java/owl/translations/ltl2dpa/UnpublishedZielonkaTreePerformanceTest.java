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

package owl.translations.ltl2dpa;

import static owl.translations.LtlTranslationRepository.LtlToDpaTranslation.UNPUBLISHED_ZIELONKA;
import static owl.translations.LtlTranslationRepository.Option;

import java.time.Duration;
import java.util.EnumSet;
import java.util.OptionalInt;
import java.util.function.Function;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.acceptance.ParityAcceptance;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;

@SuppressWarnings("unchecked")
public class UnpublishedZielonkaTreePerformanceTest {

  private static Function<LabelledFormula, Automaton<?, ? extends ParityAcceptance>>
    TRANSLATION_ACD = UNPUBLISHED_ZIELONKA.translation(
      ParityAcceptance.class, EnumSet.noneOf(Option.class), OptionalInt.empty());

  private static Function<LabelledFormula, Automaton<?, ? extends ParityAcceptance>>
    TRANSLATION_ZLK = UNPUBLISHED_ZIELONKA.translation(
      ParityAcceptance.class, EnumSet.noneOf(Option.class), OptionalInt.of(0));

  private static Function<LabelledFormula, Automaton<?, ? extends ParityAcceptance>>
    TRANSLATION_MIXED_10 = UNPUBLISHED_ZIELONKA.translation(
      ParityAcceptance.class, EnumSet.noneOf(Option.class), OptionalInt.of(10));

  @Tag("performance")
  @RepeatedTest(3)
  void testPerformanceLtl2DbaR8() {
    var formula = LtlParser.parse(
      "((((((((((G (F (\"p_0\"))) || (F (G (\"p_1\")))) && ((G (F (\"p_1\"))) || "
        + "(F(G(\"p_2\"))))) && ((G (F (\"p_2\"))) || (F (G (\"p_3\"))))) && ((G (F (\"p_3\"))) "
        + "|| (F(G(\"p_4\"))))) && ((G (F (\"p_4\"))) || (F (G (\"p_5\"))))) && ((G (F (\"p_5\"))"
        + ") || (F(G(\"p_6\"))))) && ((G (F (\"p_6\"))) || (F (G (\"p_7\"))))) && (G (F (\"p_7\")"
        + "))) <-> (G(F(\"acc\"))))");

    // Takes ~2.5s (warm: ~2s) on a MacBook Pro (16-inch, 2019) / 2,6 GHz 6-Core Intel Core i7.
    Assertions.assertTimeout(Duration.ofSeconds(5), () -> {
      var automaton = (Automaton<Object, ?>) TRANSLATION_ACD.apply(formula);
      Assertions.assertEquals(4, automaton.successors(automaton.initialState()).size());
      Assertions.assertFalse(AutomatonUtil.isLessOrEqual(automaton, 5));
    });
    
    // Takes ~1s (warm: 500ms) on a MacBook Pro (16-inch, 2019) / 2,6 GHz 6-Core Intel Core i7.
    Assertions.assertTimeout(Duration.ofSeconds(2), () -> {
      var automaton = (Automaton<Object, ?>) TRANSLATION_ZLK.apply(formula);
      Assertions.assertEquals(149, automaton.successors(automaton.initialState()).size());
      Assertions.assertFalse(AutomatonUtil.isLessOrEqual(automaton, 150));
    });

    // Takes ~2s (warm: ~1.7s) on a MacBook Pro (16-inch, 2019) / 2,6 GHz 6-Core Intel Core i7.
    Assertions.assertTimeout(Duration.ofSeconds(4), () -> {
      var automaton = (Automaton<Object, ?>) TRANSLATION_MIXED_10.apply(formula);
      Assertions.assertEquals(4, automaton.successors(automaton.initialState()).size());
      Assertions.assertFalse(AutomatonUtil.isLessOrEqual(automaton, 5));
    });
  }

  @Tag("performance")
  @RepeatedTest(3)
  void testPerformanceLtl2DbaQ12() {
    var formula = LtlParser.parse(
      "((((((((((((((F (\"p_0\")) || (G (\"p_1\"))) && ((F (\"p_1\")) || (G (\"p_2\")))) && ((F"
        + " (\"p_2\")) || (G (\"p_3\")))) && ((F (\"p_3\")) || (G (\"p_4\")))) && ((F (\"p_4\")"
        + ") || (G (\"p_5\")))) && ((F (\"p_5\")) || (G (\"p_6\")))) && ((F (\"p_6\")) || (G "
        + "(\"p_7\")))) && ((F (\"p_7\")) || (G (\"p_8\")))) && ((F (\"p_8\")) || (G (\"p_9\"))"
        + ")) && ((F (\"p_9\")) || (G (\"p_10\")))) && ((F (\"p_10\")) || (G (\"p_11\")))) && "
        + "(F (\"p_11\"))) <-> (G (F (\"acc\"))))\n");

    // Takes ~6s (warm: ~5s) on a MacBook Pro (16-inch, 2019) / 2,6 GHz 6-Core Intel Core i7.
    Assertions.assertTimeout(Duration.ofSeconds(10), () -> {
      var automaton = (Automaton<Object, ?>) TRANSLATION_ZLK.apply(formula);
      Assertions.assertEquals(4096, automaton.successors(automaton.initialState()).size());
      Assertions.assertFalse(AutomatonUtil.isLessOrEqual(automaton, 4097));
    });

    // Takes ~6s (warm: 5s) on a MacBook Pro (16-inch, 2019) / 2,6 GHz 6-Core Intel Core i7.
    Assertions.assertTimeout(Duration.ofSeconds(10), () -> {
      var automaton = (Automaton<Object, ?>) TRANSLATION_MIXED_10.apply(formula);
      Assertions.assertEquals(4096, automaton.successors(automaton.initialState()).size());
      Assertions.assertFalse(AutomatonUtil.isLessOrEqual(automaton, 4097));
    });
  }

  @Tag("performance")
  @RepeatedTest(3)
  void testPerformanceAmbaDecomposedLock10() {
    var formula = LtlParser.parse(
      "(((G (((((((! (\"HGRANT_0\")) && (! (\"HGRANT_1\"))) && (! (\"HGRANT_2\"))) && (! "
        + "(\"HGRANT_3\"))) && (! (\"HGRANT_4\"))) && (((((! (\"HGRANT_5\")) && (! "
        + "(\"HGRANT_6\"))) && (! (\"HGRANT_7\"))) && (((! (\"HGRANT_8\")) && (true)) || ("
        + "(true) && (! (\"HGRANT_9\"))))) || (((((! (\"HGRANT_5\")) && (! (\"HGRANT_6\"))) && "
        + "(true)) || ((((! (\"HGRANT_5\")) && (true)) || ((true) && (! (\"HGRANT_6\")))) && (!"
        + " (\"HGRANT_7\")))) && ((! (\"HGRANT_8\")) && (! (\"HGRANT_9\")))))) || ((((((! "
        + "(\"HGRANT_0\")) && (! (\"HGRANT_1\"))) && (! (\"HGRANT_2\"))) && (((! (\"HGRANT_3\")"
        + ") && (true)) || ((true) && (! (\"HGRANT_4\"))))) || (((((! (\"HGRANT_0\")) && (! "
        + "(\"HGRANT_1\"))) && (true)) || ((((! (\"HGRANT_0\")) && (true)) || ((true) && (! "
        + "(\"HGRANT_1\")))) && (! (\"HGRANT_2\")))) && ((! (\"HGRANT_3\")) && (! "
        + "(\"HGRANT_4\"))))) && (((((! (\"HGRANT_5\")) && (! (\"HGRANT_6\"))) && (! "
        + "(\"HGRANT_7\"))) && (! (\"HGRANT_8\"))) && (! (\"HGRANT_9\")))))) && (G ((((((((("
        + "(\"HGRANT_0\") || (\"HGRANT_1\")) || (\"HGRANT_2\")) || (\"HGRANT_3\")) || "
        + "(\"HGRANT_4\")) || (\"HGRANT_5\")) || (\"HGRANT_6\")) || (\"HGRANT_7\")) || "
        + "(\"HGRANT_8\")) || (\"HGRANT_9\")))) -> (G (((((((((((((\"DECIDE\") && (X "
        + "(\"HGRANT_0\"))) -> ((X (\"LOCKED\")) <-> (X (\"HLOCK_0\")))) && (((\"DECIDE\") && "
        + "(X (\"HGRANT_1\"))) -> ((X (\"LOCKED\")) <-> (X (\"HLOCK_1\"))))) && (((\"DECIDE\") "
        + "&& (X (\"HGRANT_2\"))) -> ((X (\"LOCKED\")) <-> (X (\"HLOCK_2\"))))) && (("
        + "(\"DECIDE\") && (X (\"HGRANT_3\"))) -> ((X (\"LOCKED\")) <-> (X (\"HLOCK_3\"))))) &&"
        + " (((\"DECIDE\") && (X (\"HGRANT_4\"))) -> ((X (\"LOCKED\")) <-> (X (\"HLOCK_4\")))))"
        + " && (((\"DECIDE\") && (X (\"HGRANT_5\"))) -> ((X (\"LOCKED\")) <-> (X (\"HLOCK_5\"))"
        + "))) && (((\"DECIDE\") && (X (\"HGRANT_6\"))) -> ((X (\"LOCKED\")) <-> (X "
        + "(\"HLOCK_6\"))))) && (((\"DECIDE\") && (X (\"HGRANT_7\"))) -> ((X (\"LOCKED\")) <-> "
        + "(X (\"HLOCK_7\"))))) && (((\"DECIDE\") && (X (\"HGRANT_8\"))) -> ((X (\"LOCKED\")) "
        + "<-> (X (\"HLOCK_8\"))))) && (((\"DECIDE\") && (X (\"HGRANT_9\"))) -> ((X "
        + "(\"LOCKED\")) <-> (X (\"HLOCK_9\"))))) && ((! (\"DECIDE\")) -> ((X (\"LOCKED\")) <->"
        + " (\"LOCKED\"))))))\n");

    // Takes ~1s (warm: ~250 ms) on a MacBook Pro (16-inch, 2019) / 2,6 GHz 6-Core Intel Core i7.
    Assertions.assertTimeout(Duration.ofSeconds(2), () -> {
      var automaton = (Automaton<Object, ?>) TRANSLATION_ACD.apply(formula);
      Assertions.assertEquals(4, automaton.successors(automaton.initialState()).size());
      Assertions.assertEquals(6, automaton.states().size());
    });

    // Takes ~1s (warm: ~250ms) on a MacBook Pro (16-inch, 2019) / 2,6 GHz 6-Core Intel Core i7.
    Assertions.assertTimeout(Duration.ofSeconds(2), () -> {
      var automaton = (Automaton<Object, ?>) TRANSLATION_ZLK.apply(formula);
      Assertions.assertEquals(4, automaton.successors(automaton.initialState()).size());
      Assertions.assertEquals(6, automaton.states().size());
    });

    // Takes ~1s (warm: ~250ms) on a MacBook Pro (16-inch, 2019) / 2,6 GHz 6-Core Intel Core i7.
    Assertions.assertTimeout(Duration.ofSeconds(2), () -> {
      var automaton = (Automaton<Object, ?>) TRANSLATION_MIXED_10.apply(formula);
      Assertions.assertEquals(4, automaton.successors(automaton.initialState()).size());
      Assertions.assertEquals(6, automaton.states().size());
    });
  }
}
