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

package owl.automaton;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.google.common.collect.Maps;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.BitSet2;
import owl.ltl.parser.LtlParser;
import owl.translations.canonical.DeterministicConstructionsPortfolio;

class AutomatonFactoryTest {

  @Test
  void testCopy() {
    var automaton = HashMapAutomaton.copyOf(
      DeterministicConstructionsPortfolio.safety(LtlParser.parse("G a | b R c")));

    var initialState = automaton.initialState();
    var edgeMap = automaton.edgeMap(automaton.initialState());

    for (BitSet valuation : BitSet2.powerSet(automaton.atomicPropositions().size())) {
      var edge = automaton.edge(initialState, valuation);
      var matchingEdges = Maps.filterValues(edgeMap, x -> x.contains(valuation)).keySet();

      if (edge == null) {
        assertEquals(Set.of(), matchingEdges);
      } else {
        assertEquals(Set.of(edge), matchingEdges);
      }
    }
  }

  @Test
  void testAcceptingSingleton() {
    var state = new Object();
    var states = Set.of(state);
    var edge = Edge.of(state);
    var automaton = SingletonAutomaton.of(List.of("a"), state, AllAcceptance.INSTANCE, Set.of());

    assertAll(
      () -> assertEquals(states, automaton.states()),
      () -> assertEquals(states, automaton.initialStates()),
      () -> assertEquals(states, HashMapAutomatonTest.getReachableStates(automaton)),

      () -> assertEquals(Set.of(edge), automaton.edges(state)),
      () -> assertEquals(Map.of(edge, automaton.factory().of(true)), automaton.edgeMap(state)),
      () -> assertEquals(Map.of(), AutomatonUtil.getIncompleteStates(automaton)),

      () -> assertSame(AllAcceptance.INSTANCE, automaton.acceptance())
    );
  }

  @Test
  void testRejectingSingleton() {
    var state = new Object();
    var states = Set.of(state);
    var edge = Edge.of(state);
    var automaton = SingletonAutomaton.of(List.of("a"), state, AllAcceptance.INSTANCE, Set.of());

    assertAll(
      () -> assertEquals(states, automaton.states()),
      () -> assertEquals(states, automaton.initialStates()),
      () -> assertEquals(states, HashMapAutomatonTest.getReachableStates(automaton)),

      () -> assertEquals(Set.of(edge), automaton.edges(state)),
      () -> assertEquals(Map.of(edge, automaton.factory().of(true)), automaton.edgeMap(state)),
      () -> assertEquals(Map.of(), AutomatonUtil.getIncompleteStates(automaton)),

      () -> assertSame(AllAcceptance.INSTANCE, automaton.acceptance())
    );
  }
}
