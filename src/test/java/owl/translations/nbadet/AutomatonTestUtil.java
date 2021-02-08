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

package owl.translations.nbadet;

import java.io.StringReader;
import java.util.HashMap;
import jhoafparser.parser.generated.ParseException;
import owl.automaton.Automaton;
import owl.automaton.Views;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.OmegaAcceptanceCast;
import owl.automaton.hoa.HoaReader;
import owl.bdd.FactorySupplier;

public final class AutomatonTestUtil {

  // make PMD silent.
  private AutomatonTestUtil() {}

  /** Read from HOA string and transform such that
   * state object corresponds to HOA state number in string. */
  public static <A extends OmegaAcceptance> Automaton<Integer, A> autFromString(
      String hoa, Class<A> acc) throws ParseException {

    final var supplier = FactorySupplier.defaultSupplier();
    final var parsed = HoaReader.read(new StringReader(hoa), supplier::getValuationSetFactory);
    final var aut = OmegaAcceptanceCast.cast(parsed, acc);

    var stateMap = new HashMap<HoaReader.HoaState, Integer>();
    aut.states().forEach(st -> stateMap.put(st, Integer.valueOf(st.toString())));
    return Views.quotientAutomaton(aut, stateMap::get);
  }
}
