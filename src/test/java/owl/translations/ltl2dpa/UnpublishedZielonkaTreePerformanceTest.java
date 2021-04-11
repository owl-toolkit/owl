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

import java.time.Duration;
import java.util.EnumSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import owl.automaton.Automaton;
import owl.ltl.parser.LtlParser;
import owl.translations.LtlTranslationRepository;

public class UnpublishedZielonkaTreePerformanceTest {

  @Test
  void testPerformanceLtl2DbaR8() {
    // Takes ~1.5s on a MacBook Pro (16-inch, 2019) / 2,6 GHz 6-Core Intel Core i7.
    Assertions.assertTimeout(Duration.ofSeconds(3), () -> {
      var formula = LtlParser.parse(
        "((((((((((G (F (\"p_0\"))) || (F (G (\"p_1\")))) && ((G (F (\"p_1\"))) || "
          + "(F(G(\"p_2\"))))) && ((G (F (\"p_2\"))) || (F (G (\"p_3\"))))) && ((G (F (\"p_3\"))) "
          + "|| (F(G(\"p_4\"))))) && ((G (F (\"p_4\"))) || (F (G (\"p_5\"))))) && ((G (F (\"p_5\"))"
          + ") || (F(G(\"p_6\"))))) && ((G (F (\"p_6\"))) || (F (G (\"p_7\"))))) && (G (F (\"p_7\")"
          + "))) <-> (G(F(\"acc\"))))");

      var translation
        = UNPUBLISHED_ZIELONKA.translation(EnumSet.noneOf(LtlTranslationRepository.Option.class));

      Automaton automaton = translation.apply(formula);
      automaton.edgeTree(automaton.onlyInitialState());
    });
  }

  @Test
  void testPerformanceLtl2DbaQ12() {
    // Takes ~18s on a MacBook Pro (16-inch, 2019) / 2,6 GHz 6-Core Intel Core i7.
    Assertions.assertTimeout(Duration.ofSeconds(30), () -> {
      var formula = LtlParser.parse(
        "((((((((((((((F (\"p_0\")) || (G (\"p_1\"))) && ((F (\"p_1\")) || (G (\"p_2\")))) && ((F"
          + " (\"p_2\")) || (G (\"p_3\")))) && ((F (\"p_3\")) || (G (\"p_4\")))) && ((F (\"p_4\")"
          + ") || (G (\"p_5\")))) && ((F (\"p_5\")) || (G (\"p_6\")))) && ((F (\"p_6\")) || (G "
          + "(\"p_7\")))) && ((F (\"p_7\")) || (G (\"p_8\")))) && ((F (\"p_8\")) || (G (\"p_9\"))"
          + ")) && ((F (\"p_9\")) || (G (\"p_10\")))) && ((F (\"p_10\")) || (G (\"p_11\")))) && "
          + "(F (\"p_11\"))) <-> (G (F (\"acc\"))))\n");

      var translation
        = UNPUBLISHED_ZIELONKA.translation(EnumSet.noneOf(LtlTranslationRepository.Option.class));

      Automaton automaton = translation.apply(formula);
      automaton.edgeTree(automaton.onlyInitialState());
    });
  }
}
