/*
 * Copyright (C) 2022  (Salomon Sickert)
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

import static com.google.common.base.Verify.verify;
import static owl.automaton.algorithm.LanguageContainment.equalsCoBuchi;

import owl.automaton.Automaton;
import owl.automaton.Automaton.Property;
import owl.automaton.Views;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.determinization.Determinization;

public class DcwMinimization {

  /**
   * Computes a minimal tDCW for the language given by the tNCW.
   *
   * @param ncw the language for which a minimal tDCW should be computed.
   * @return an equivalent, deterministic, complete and minimal tDCW.
   */
  public static Automaton<Integer, CoBuchiAcceptance> minimize(
      Automaton<?, ? extends CoBuchiAcceptance> ncw) {

    var canonicalGfgNcw = GfgNcwMinimization.minimize(ncw);

    // Short-cut for languages that are weak-type.
    if (canonicalGfgNcw.alphaMaximalUpToHomogenityGfgNcw.is(Automaton.Property.DETERMINISTIC)) {
      return canonicalGfgNcw.alphaMaximalUpToHomogenityGfgNcw;
    }

    // Short-cut in case we already constructed a minimal tDCW.
    if (canonicalGfgNcw.alphaMaximalGfgNcw.states().size() == canonicalGfgNcw.dcw.states().size()) {
      return canonicalGfgNcw.dcw;
    }

    // Phase 1: Is the language GFG-helpful?
    var dcwByPruning = DcwMinimizationForNonGfgLanguages.prune(canonicalGfgNcw);

    if (dcwByPruning.isPresent()) {
      return dcwByPruning.get();
    }

    // Phase 2: We need to explore the space between the canonical GFG-tNCW and the candidate tDCW.
    System.err.println("GFG-helpful language.");

    var upperBoundDcw = Views.dropStateLabels(
        Determinization.determinizeCanonicalGfgNcw(canonicalGfgNcw)).automaton();

    if (canonicalGfgNcw.dcw.states().size() < upperBoundDcw.states().size()) {
      upperBoundDcw = canonicalGfgNcw.dcw;
    }

    int lowerBoundInclusive = canonicalGfgNcw.alphaMaximalGfgNcw.states().size() + 1;

    while (lowerBoundInclusive < upperBoundDcw.states().size()) {
      int upperBoundInclusive = upperBoundDcw.states().size() - 1;
      int middle = (lowerBoundInclusive + upperBoundInclusive) / 2;

      assert lowerBoundInclusive <= upperBoundInclusive;
      assert lowerBoundInclusive <= middle;
      assert middle <= upperBoundInclusive;

      var dcwByGuessing = DcwMinimizationForGfgLanguages.guess(canonicalGfgNcw, middle);

      if (dcwByGuessing.isPresent()) {
        upperBoundDcw = dcwByGuessing.get();
      } else {
        lowerBoundInclusive = middle + 1;
      }
    }

    verify(upperBoundDcw.is(Property.COMPLETE));
    verify(upperBoundDcw.is(Property.DETERMINISTIC));
    verify(equalsCoBuchi(canonicalGfgNcw.alphaMaximalUpToHomogenityGfgNcw, upperBoundDcw));

    return upperBoundDcw;
  }
}
