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

package owl.automaton.algorithm.simulations;

import static owl.automaton.algorithm.simulations.CommonAutomata.predecessorAutomaton;

import java.util.BitSet;
import java.util.List;
import org.junit.jupiter.api.Test;

public class TransitionTest {
  @Test
  void validityTest() {
    var aut = predecessorAutomaton();
    var factory = aut.factory();

    BitSet a = factory.of(0).iterator(aut.atomicPropositions().size()).next();

    assert Transition.of(a, 2, false).isValid(1, aut);
    assert !Transition.of(a, 3, true).isValid(1, aut);
    assert Transition.of(a, 3, false).isValid(1, aut);

    assert SimulationStates.LookaheadSimulationState.of(
      1, 1,
      List.of(Transition.of(a, 2, false))
    ).isValid(aut);

    assert SimulationStates.LookaheadSimulationState.of(
      1, 1,
      List.of(Transition.of(a, 2, false), Transition.of(a, 4, false))
    ).isValid(aut);

    var longTransition = SimulationStates.LookaheadSimulationState.of(
      1, 1,
      List.of(
        Transition.of(a, 3, false),
        Transition.of(a, 4, false),
        Transition.of(a, 4, true)));
    assert longTransition.isValid(aut);
    assert longTransition.flag();

    assert !SimulationStates.LookaheadSimulationState.of(
      1, 1,
      List.of(Transition.of(a, 4, false))
    ).isValid(aut);
  }
}
