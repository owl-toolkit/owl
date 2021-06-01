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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.BitSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import owl.automaton.HashMapAutomaton;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.Pair;

class BuchiSimulationTest {

  @Test
  public void directSimulationTest() {

    MutableAutomaton<String, BuchiAcceptance> automaton = HashMapAutomaton.create(
      List.of("a"), BuchiAcceptance.INSTANCE);

    BitSet a = new BitSet();
    a.set(0);
    BitSet notA = new BitSet();

    String initialState = "0";
    String acceptingState = "1";

    automaton.addInitialState(initialState);
    automaton.addState(acceptingState);

    automaton.addEdge(initialState,   a,    Edge.of(initialState));
    automaton.addEdge(initialState,   notA, Edge.of(initialState));
    automaton.addEdge(initialState,   a,    Edge.of(acceptingState, 0));
    automaton.addEdge(acceptingState, a,    Edge.of(acceptingState, 0));

    automaton.trim();

    assertEquals(Set.of(
      Pair.of(initialState, initialState),
      Pair.of(acceptingState, initialState),
      Pair.of(acceptingState, acceptingState)),
      new BuchiSimulation().directSimulation(automaton, automaton, 1)
    );
  }

  @Test
  public void directSimulationTest2() {

    MutableAutomaton<String, BuchiAcceptance> automaton = HashMapAutomaton.create(
      List.of("a", "b"), BuchiAcceptance.INSTANCE);

    String initialState = "0";
    String acceptingState = "1";

    automaton.addInitialState(initialState);
    automaton.addState(acceptingState);

    automaton.addEdge(initialState,   automaton.factory().of(0), Edge.of(initialState));
    automaton.addEdge(initialState,   automaton.factory().of(1), Edge.of(initialState));
    automaton.addEdge(initialState,   automaton.factory().of(1), Edge.of(acceptingState, 0));
    automaton.addEdge(acceptingState, automaton.factory().of(0), Edge.of(acceptingState, 0));

    automaton.trim();

    assertEquals(Set.of(
      Pair.of(initialState, initialState),
      Pair.of(acceptingState, acceptingState)),
      new BuchiSimulation().directSimulation(automaton, automaton, 1)
    );
  }
}