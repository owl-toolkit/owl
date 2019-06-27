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

package owl.automaton.util;

import java.util.Set;
import owl.automaton.Automaton;
import owl.automaton.MutableAutomatonFactory;
import owl.automaton.Views;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.algorithms.SccDecomposition;

public final class AnnotatedStateOptimisation {

  private AnnotatedStateOptimisation() {
  }

  /**
   * Selects a state within the automaton as a new initial state such that the resulting automaton
   * is smaller in size and the new initial state share the same value for
   * {@link AnnotatedState#state()}.
   *
   * @param automaton the automaton
   * @param <S> the type of the states
   * @param <A> the type of the acceptance
   * @return A copy of the automaton with the new initial state set. For performance reasons are
   *     copy is made, but this might change.
   */
  public static <S extends AnnotatedState<?>, A extends OmegaAcceptance>
    Automaton<S, A> optimizeInitialState(Automaton<S, A> automaton) {
    var mutableAutomatonCopy = MutableAutomatonFactory.copy(automaton);

    if (mutableAutomatonCopy.initialStates().isEmpty()) {
      return mutableAutomatonCopy;
    }

    var originalInitialState = mutableAutomatonCopy.onlyInitialState().state();

    S candidateInitialState = null;
    int size = mutableAutomatonCopy.size();

    for (Set<S> scc : SccDecomposition.computeSccs(mutableAutomatonCopy, false)) {
      for (S state : scc) {
        if (!originalInitialState.equals(state.state())) {
          continue;
        }

        int newSize = Views.replaceInitialState(mutableAutomatonCopy, Set.of(state)).size();

        if (newSize < size) {
          candidateInitialState = state;
          size = newSize;
        }
      }
    }

    if (candidateInitialState != null) {
      mutableAutomatonCopy.initialStates(Set.of(candidateInitialState));
      mutableAutomatonCopy.trim();
    }

    return mutableAutomatonCopy;
  }
}
