/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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
import owl.automaton.AutomatonUtil;
import owl.automaton.Views;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;

public final class LanguageAnalysis {

  private LanguageAnalysis() {}

  /**
   * Checks if the first the language of the first automaton is included in the language of the
   * second automaton.
   *
   * @param automaton1
   *     The first automaton, whose language is tested for inclusion of the second language
   * @param automaton2
   *     The second automaton
   * @param <S>
   *     The type of the state.
   *
   * @return true if L_1 is contained in L_2.
   */
  public static <S> boolean contains(Automaton<S, BuchiAcceptance> automaton1,
    Automaton<S, BuchiAcceptance> automaton2) {
    Preconditions.checkArgument(automaton1.is(Property.DETERMINISTIC),
      "First argument needs to be deterministic.");
    Preconditions.checkArgument(automaton2.is(Property.DETERMINISTIC),
      "Second argument needs to be deterministic.");

    var casted1 = AutomatonUtil.cast(automaton1, Object.class, BuchiAcceptance.class);
    var casted2 = AutomatonUtil.cast(automaton2, Object.class, BuchiAcceptance.class);

    return EmptinessCheck.isEmpty(AutomatonOperations.intersection(List.of(casted1,
      AutomatonUtil.cast(Views.complement(casted2, new Object()), CoBuchiAcceptance.class))));
  }
}
