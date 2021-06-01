/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
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

package owl.automaton.algorithm;

import com.google.common.base.Preconditions;
import owl.automaton.Automaton;
import owl.automaton.Automaton.Property;
import owl.automaton.BooleanOperations;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.determinization.Determinization;

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
  public static boolean contains(Automaton<?, ?> automaton1, Automaton<?, ?> automaton2) {
    Preconditions.checkArgument(automaton2.is(Property.DETERMINISTIC),
      "Second argument needs to be deterministic.");

    var automaton2Complement
      = BooleanOperations.deterministicComplement(automaton2, EmersonLeiAcceptance.class);
    var intersection
      = BooleanOperations.intersection(automaton1, automaton2Complement);

    return LanguageEmptiness.isEmpty(intersection);
  }

  public static boolean containsCoBuchi(
    Automaton<?, ? extends CoBuchiAcceptance> automaton1,
    Automaton<?, ? extends CoBuchiAcceptance> automaton2) {

    if (automaton2.is(Property.DETERMINISTIC)) {
      return contains(automaton1, automaton2);
    }

    return contains(automaton1, Determinization.determinizeCoBuchiAcceptance(automaton2));
  }

  public static boolean equalsCoBuchi(
    Automaton<?, ? extends CoBuchiAcceptance> automaton1,
    Automaton<?, ? extends CoBuchiAcceptance> automaton2) {
    return containsCoBuchi(automaton1, automaton2) && containsCoBuchi(automaton2, automaton1);
  }

  public static boolean containsAll(
    Automaton<?, AllAcceptance> automaton1,
    Automaton<?, AllAcceptance> automaton2) {

    if (automaton2.is(Property.DETERMINISTIC)) {
      return contains(automaton1, automaton2);
    }

    return contains(automaton1, Determinization.determinizeAllAcceptance(automaton2));
  }

  public static boolean equalsAll(
    Automaton<?, AllAcceptance> automaton1,
    Automaton<?, AllAcceptance> automaton2) {
    return containsAll(automaton1, automaton2) && containsAll(automaton2, automaton1);
  }

  public static boolean languageEquivalent(Automaton<?, ?> automaton1,
    Automaton<?, ?> automaton2) {
    return LanguageContainment.contains(automaton1, automaton2)
      && LanguageContainment.contains(automaton2, automaton1);
  }
}
