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

package owl.automaton;

final class Properties {
  private Properties() {}

  /**
   * Determines whether the automaton is complete, i.e. every state has at least one successor for
   * each valuation.
   *
   * @param automaton the automaton
   *
   * @return Whether the automaton is complete.
   *
   */
  static <S> boolean isComplete(Automaton<S, ?> automaton) {
    return automaton.size() >= 1
      && AutomatonUtil.getIncompleteStates(automaton).isEmpty();
  }

  /**
   * Determines whether the automaton is deterministic, i.e. there is at most one initial state and
   * every state has at most one successor under each valuation.
   *
   * @param automaton the automaton
   *
   * @return Whether the automaton is deterministic.
   */
  static <S> boolean isDeterministic(Automaton<S, ?> automaton) {
    return automaton.initialStates().size() <= 1
      && automaton.is(Automaton.Property.SEMI_DETERMINISTIC);
  }

  static <S> boolean isSemiDeterministic(Automaton<S, ?> automaton) {
    return AutomatonUtil.getNondeterministicStates(automaton).isEmpty();
  }
}
