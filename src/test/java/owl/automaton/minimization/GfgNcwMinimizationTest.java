/*
 * Copyright (C) 2016 - 2022  (See AUTHORS)
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

package owl.automaton.minimization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import owl.automaton.Automaton;
import owl.automaton.HashMapAutomaton;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.OmegaAcceptanceCast;
import owl.automaton.acceptance.optimization.AcceptanceOptimizations;
import owl.automaton.algorithm.LanguageContainment;
import owl.automaton.determinization.Determinization;
import owl.automaton.hoa.HoaWriter;
import owl.ltl.parser.LtlParser;
import owl.translations.canonical.DeterministicConstructionsPortfolio;

class GfgNcwMinimizationTest {

  private static final DeterministicConstructionsPortfolio<CoBuchiAcceptance> coBuchiPortfolio
      = new DeterministicConstructionsPortfolio<>(CoBuchiAcceptance.class);

  @Test
  void testMinimize1() {
    var minimizedAutomaton = GfgNcwMinimization.minimize(
        OmegaAcceptanceCast.castExact(coBuchiPortfolio.apply(
                LtlParser.parse("F G a")).orElseThrow(),
            CoBuchiAcceptance.class)).alphaMaximalUpToHomogenityGfgNcw;

    assertEquals(1, minimizedAutomaton.states().size());
    assertTrue(minimizedAutomaton.is(Automaton.Property.DETERMINISTIC));
  }

  @Test
  void testMinimize2() {
    var minimizedAutomaton = GfgNcwMinimization.minimize(
        OmegaAcceptanceCast.castExact(coBuchiPortfolio.apply(
                LtlParser.parse("F G ((G a & G b & G !c) | (G a & G !b & G c))")).orElseThrow(),
            CoBuchiAcceptance.class)).alphaMaximalUpToHomogenityGfgNcw;

    assertEquals(2, minimizedAutomaton.states().size());
  }

  @Test
  void testPermutationMinimize() {
    int n = 3;

    var gfgAutomaton = DcwRepository.permutationLanguage(n);
    var automaton2 = Determinization.determinizeCoBuchiAcceptance(gfgAutomaton);

    var minimizedAutomaton = HashMapAutomaton.copyOf(
        GfgNcwMinimization.minimize(automaton2).alphaMaximalUpToHomogenityGfgNcw);

    var minimizedAutomaton24 = Determinization.determinizeCanonicalGfgNcw(
        GfgNcwMinimization.minimize(automaton2));
    AcceptanceOptimizations.removeDeadStates(minimizedAutomaton);
    assertEquals(gfgAutomaton.states().size(), minimizedAutomaton.states().size(),
        HoaWriter.toString(minimizedAutomaton));

    var minimizedAutomaton2 = HashMapAutomaton.copyOf(
        GfgNcwMinimization.minimize(
            DcwRepository.permutationLanguage(n)).alphaMaximalUpToHomogenityGfgNcw);
    AcceptanceOptimizations.removeDeadStates(minimizedAutomaton2);

    assertEquals(gfgAutomaton.states().size(), minimizedAutomaton2.states().size(),
        HoaWriter.toString(minimizedAutomaton2));

    assertTrue(LanguageContainment.equalsCoBuchi(minimizedAutomaton24, minimizedAutomaton2));
  }

}
