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

import static owl.automaton.BooleanOperations.deterministicComplement;
import static owl.automaton.BooleanOperations.deterministicComplementOfCompleteAutomaton;

import owl.automaton.Automaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.OmegaAcceptanceCast;

public class DbwMinimization {

  public static Automaton<Integer, BuchiAcceptance> minimize(
      Automaton<?, ? extends BuchiAcceptance> dbw) {
    var minimalDcw
        = DcwMinimization.minimize(deterministicComplement(dbw, CoBuchiAcceptance.class));
    return OmegaAcceptanceCast.castExact(
        deterministicComplementOfCompleteAutomaton(minimalDcw, BuchiAcceptance.class),
        BuchiAcceptance.class);
  }
}
