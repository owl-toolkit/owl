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

package owl.translations.nbadet;

import java.io.StringReader;
import java.util.HashMap;
import owl.automaton.Automaton;
import owl.automaton.Views;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.acceptance.OmegaAcceptanceCast;
import owl.automaton.hoa.HoaReader;
import owl.bdd.FactorySupplier;
import owl.thirdparty.jhoafparser.parser.generated.ParseException;

public final class AutomatonTestUtil {

  private AutomatonTestUtil() {}

  /** Read from HOA string and transform such that
   * state object corresponds to HOA state number in string. */
  public static <A extends EmersonLeiAcceptance> Automaton<Integer, ? extends A> autFromString(
      String hoa, Class<A> acc) throws ParseException {

    var parsed = HoaReader.read(
      new StringReader(hoa), FactorySupplier.defaultSupplier()::getBddSetFactory, null);
    var aut = OmegaAcceptanceCast.cast(parsed, acc);

    var stateMap = new HashMap<Integer, Integer>();
    aut.states().forEach(st -> stateMap.put(st, Integer.valueOf(st.toString())));
    return Views.quotientAutomaton(aut, stateMap::get);
  }
}
