/*
 * Copyright (C) 2016  (See AUTHORS)
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

package owl.arena;

import com.google.common.collect.Sets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import owl.automaton.Automaton;
import owl.automaton.acceptance.OmegaAcceptance;

public interface Arena<S, A extends OmegaAcceptance> extends Automaton<S, A> {
  // Does not contain the states itself.
  default Set<S> getAttractor(Set<S> states, Owner owner) {
    Set<S> attractor = new HashSet<>();

    // Add states that owner controls;
    for (S predecessor : getPredecessors(states)) {
      if (owner == getOwner(predecessor) || states.containsAll(getSuccessors(predecessor))) {
        attractor.add(predecessor);
      }
    }

    return attractor;
  }

  default Set<S> getAttractorFixpoint(Set<S> states, Owner owner) {
    Set<S> winningStates = new HashSet<>(states);
    boolean continueIteration = true;

    while (continueIteration) {
      continueIteration = winningStates.addAll(getAttractor(winningStates, owner));
    }

    return winningStates;
  }

  Owner getOwner(S state);

  default Set<S> getPredecessors(S state, Owner owner) {
    return getPredecessors(Set.of(state), owner);
  }

  default Set<S> getPredecessors(Set<S> states) {
    Set<S> predecessors = new HashSet<>();
    states.forEach(x -> predecessors.addAll(getPredecessors(x)));
    return predecessors;
  }

  default Set<S> getPredecessors(Set<S> state, Owner owner) {
    return Sets.filter(getPredecessors(state), x -> owner == getOwner(x));
  }

  default Set<S> getSuccessors(S state, Owner owner) {
    return getSuccessors(Set.of(state), owner);
  }

  default Set<S> getSuccessors(Set<S> states) {
    Set<S> successors = new HashSet<>();
    states.forEach(x -> successors.addAll(getSuccessors(x)));
    return successors;
  }

  default Set<S> getSuccessors(Set<S> states, Owner owner) {
    return Sets.filter(getSuccessors(states), x -> owner == getOwner(x));
  }

  default Set<S> getUncontrollablePredecessors(Set<S> states, Owner owner) {
    return Sets.difference(getPredecessors(states), getAttractor(states, owner));
  }

  List<String> getVariables(Owner owner);

  default void toPgSolver() {
    throw new UnsupportedOperationException("Not yet implemented.");
  }

  enum Owner {
    PLAYER_1, PLAYER_2;

    Owner flip() {
      return this == PLAYER_1 ? PLAYER_2 : PLAYER_1;
    }
  }
}
