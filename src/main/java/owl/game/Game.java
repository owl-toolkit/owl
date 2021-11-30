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

package owl.game;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import owl.automaton.Automaton;
import owl.automaton.acceptance.EmersonLeiAcceptance;

public interface Game<S, A extends EmersonLeiAcceptance> extends Automaton<S, A> {

  default Set<S> getAttractor(Collection<S> states, Owner owner) {
    // Does not contain the states itself.
    Set<S> attractor = new HashSet<>();

    // Add states that owner controls;
    Set<S> predecessors = new HashSet<>(states.size());

    for (S x : states) {
      predecessors.addAll(predecessors(x));
    }

    for (S predecessor : predecessors) {
      if (owner == owner(predecessor) || states.containsAll(successors(predecessor))) {
        attractor.add(predecessor);
      }
    }

    return attractor;
  }

  default Set<S> getAttractorFixpoint(Set<S> states, Owner owner) {
    Set<S> attractor = new HashSet<>(states);
    boolean continueIteration = true;

    while (continueIteration) {
      continueIteration = attractor.addAll(getAttractor(attractor, owner));
    }

    return attractor;
  }

  Owner owner(S state);

  enum Owner {
    /**
     * This player wants to dissatisfy the acceptance condition.
     */
    PLAYER_1,

    /**
     * This player wants to satisfy the acceptance condition.
     */
    PLAYER_2;

    public Owner opponent() {
      return this == PLAYER_1 ? PLAYER_2 : PLAYER_1;
    }

    public boolean isEven() {
      return PLAYER_2 == this;
    }

    public boolean isOdd() {
      return PLAYER_1 == this;
    }
  }
}
