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

import java.util.Set;
import owl.automaton.Automaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.collections.Pair;
import owl.run.RunUtil;

/**
 * Interface that all pluggable language inclusion / simulation algorithms should implement.
 *
 * @param <T> type of parsed CLI Argument
 */
public interface NbaSimAlgorithm<S,T> {

  /**
   * This method should parse the provided argument into the right type (e.g. as Integer).
   * @param arg Argument provided to the simulation.
   * @return An argument of the actual type that is required.
   * @throws IllegalArgumentException if the argument can not be meaningfully parsed.
   */
  T parseArg(String arg);

  /**
   * This method should be the actual main entry point into the algorithm.
   * @param aut Input Büchi automaton
   * @param parsedArg Argument provided to the simulation
   * @return Set of obtained language inclusions (i.e., (a,b) means L(a) subset of L(b))
   */
  Set<Pair<S,S>> compute(Automaton<S,BuchiAcceptance> aut, T parsedArg);

  /**
   * This method just parses the argument provided by the user and runs the algorithm.
   * @param aut input Büchi automaton
   * @param cliArg provided argument from the user (as String, unparsed)
   */
  default Set<Pair<S,S>> run(Automaton<S,BuchiAcceptance> aut, String cliArg) {
    T arg = null;

    try {
      arg = parseArg(cliArg);
    } catch (IllegalArgumentException e) {
      RunUtil.failWithMessage("ERROR: failed parsing provided argument: " + cliArg);
    }

    assert arg != null;
    return compute(aut, arg);
  }

}
