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

package owl.automaton.algorithms;

import com.google.common.base.Preconditions;
import java.util.List;
import owl.automaton.Automaton;
import owl.automaton.Automaton.Property;
import owl.automaton.AutomatonOperations;
import owl.automaton.Views;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.OmegaAcceptanceCast;

public final class LanguageContainment {

  private LanguageContainment() {}

  /**
   * Checks if the first the language of the first automaton is included in the language of the
   * second automaton.
   *
   * @param automaton1
   *     The first automaton, whose language is tested for inclusion of the second language
   * @param automaton2
   *     The second automaton
   *
   * @return true if L_1 is contained in L_2.
   */
  public static boolean contains(Automaton<?, BuchiAcceptance> automaton1,
    Automaton<?, BuchiAcceptance> automaton2) {
    Preconditions.checkArgument(automaton1.is(Property.DETERMINISTIC),
      "First argument needs to be deterministic.");
    Preconditions.checkArgument(automaton2.is(Property.DETERMINISTIC),
      "Second argument needs to be deterministic.");

    var casted1 = OmegaAcceptanceCast.cast(
      (Automaton<Object, ?>) automaton1, BuchiAcceptance.class);
    var casted2 = OmegaAcceptanceCast.cast(
      (Automaton<Object, ?>) automaton2, BuchiAcceptance.class);

    return LanguageEmptiness.isEmpty(AutomatonOperations.intersection(List.of(casted1,
      Views.complement(casted2, new Object(), CoBuchiAcceptance.class))));
  }
}
