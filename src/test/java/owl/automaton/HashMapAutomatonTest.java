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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static owl.util.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;
import owl.automaton.Automaton.Property;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.edge.Edge;

class HashMapAutomatonTest {

  @Test
  void testEmptyAutomaton() {
    HashMapAutomaton<?, ?> automaton
      = HashMapAutomaton.create(List.of("a"), AllAcceptance.INSTANCE);
    assertFalse(automaton.is(Property.COMPLETE));
    assertTrue(automaton.is(Property.DETERMINISTIC));
    assertEquals(Set.of(), AutomatonUtil.getIncompleteStates(automaton).keySet());
    assertEquals(Set.of(), automaton.initialStates());
    assertEquals(Set.of(), automaton.states());
  }

  @Test
  void testRemapAcceptanceDuplicate() {
    // Test that edges which are equal after a remapAcceptance call get merged.

    MutableAutomaton<String, ?> automaton
      = HashMapAutomaton.create(List.of("a"), AllAcceptance.INSTANCE);

    automaton.addInitialState("1");
    automaton.addInitialState("2");

    BitSet other = new BitSet();
    other.set(0);
    automaton.addEdge("1", new BitSet(), Edge.of("1", 1));
    automaton.addEdge("1", other, Edge.of("2"));
    automaton.addEdge("1", new BitSet(), Edge.of("2", 1));
    automaton.updateEdges((state, edge) -> switch (edge.successor()) {
      case "1" -> null;
      case "2" -> Edge.of(edge.successor());
      default -> edge;
    });
    automaton.trim();

    assertThat(automaton.edgeMap("1").entrySet(),
      x -> x.contains(Map.entry(Edge.of("2"), automaton.factory().of(true))));
  }

  @Test
  void testSimpleAutomaton() {
    // Test various parts of the implementation on a simple, two-state automaton
    var automaton
      = HashMapAutomaton.create(List.of("a"), BuchiAcceptance.INSTANCE);

    automaton.addState("1");
    automaton.addState("2");
    automaton.trim();

    assertAll(
      () -> assertEquals(Set.of(), automaton.states()),
      () -> assertEquals(Set.of(), getReachableStates(automaton))
    );

    automaton.addInitialState("1");
    automaton.trim();

    assertAll(
      () -> assertEquals("1", automaton.initialState()),
      () -> assertEquals(Set.of("1"), automaton.initialStates()),
      () -> assertEquals(Set.of("1"), automaton.states()),
      () -> assertEquals(Set.of("1"), getReachableStates(automaton))
    );

    // Add edge
    var edge = Edge.of("2");
    automaton.addEdge("1", automaton.factory().of(true), edge);
    automaton.trim();

    assertAll(
      () -> assertEquals(edge, automaton.edge("1", new BitSet())),
      () -> assertEquals(Set.of(edge), automaton.edges("1", new BitSet())),
      () -> assertEquals(Map.of(edge, automaton.factory().of(true)), automaton.edgeMap("1")),

      () -> assertEquals(Set.of("1", "2"), automaton.states()),
      () -> assertEquals(Set.of("1", "2"), getReachableStates(automaton)),

      () -> assertTrue(automaton.is(Property.DETERMINISTIC)),
      () -> assertTrue(automaton.is(Property.SEMI_DETERMINISTIC)),
      () -> assertTrue(automaton.is(Property.LIMIT_DETERMINISTIC))
    );

    // Add duplicate edge
    automaton.addEdge("1", automaton.factory().of(new BitSet(), 1), edge);
    automaton.trim();

    assertAll(
      () -> assertEquals(edge, automaton.edge("1", new BitSet())),
      () -> assertEquals(Set.of(edge), automaton.edges("1", new BitSet())),
      () -> assertEquals(Map.of(edge, automaton.factory().of(true)), automaton.edgeMap("1")),

      () -> assertEquals(Set.of("1", "2"), automaton.states()),
      () -> assertEquals(Set.of("1", "2"), getReachableStates(automaton)),

      () -> assertTrue(automaton.is(Property.DETERMINISTIC)),
      () -> assertTrue(automaton.is(Property.SEMI_DETERMINISTIC)),
      () -> assertTrue(automaton.is(Property.LIMIT_DETERMINISTIC))
    );

    // Add edge with different acceptance
    var edgeWithAcceptance = edge.withAcceptance(0);
    automaton.addEdge("1", automaton.factory().of(new BitSet(), 1), edgeWithAcceptance);
    automaton.trim();

    assertAll(
      () -> assertEquals(Set.of(edge, edgeWithAcceptance), automaton.edges("1", new BitSet())),
      () -> assertEquals(
        Map.of(
          edge, automaton.factory().of(true),
          edgeWithAcceptance, automaton.factory().of(new BitSet(), 1)),
        automaton.edgeMap("1")),

      () -> assertEquals(Set.of("1", "2"), automaton.states()),
      () -> assertEquals(Set.of("1", "2"), getReachableStates(automaton)),

      () -> assertFalse(automaton.is(Property.DETERMINISTIC)),
      () -> assertFalse(automaton.is(Property.SEMI_DETERMINISTIC)),
      () -> assertTrue(automaton.is(Property.LIMIT_DETERMINISTIC))
    );

    // Add loop edge
    var loop = Edge.of("1", 0);
    automaton.addEdge("1", automaton.factory().of(true), loop);
    automaton.trim();

    assertAll(
      () -> assertEquals(
        Set.of(edge, edgeWithAcceptance, loop),
        automaton.edges("1", new BitSet())),

      () -> assertEquals(Set.of("1", "2"), automaton.states()),
      () -> assertEquals(Set.of("1", "2"), getReachableStates(automaton)),

      () -> assertFalse(automaton.is(Property.DETERMINISTIC)),
      () -> assertFalse(automaton.is(Property.SEMI_DETERMINISTIC)),
      () -> assertFalse(automaton.is(Property.LIMIT_DETERMINISTIC))
    );

    // Add another initial state
    automaton.addInitialState("2");

    assertAll(
      () -> assertThrows(IllegalStateException.class, automaton::initialState),
      () -> assertEquals(Set.of("1", "2"), automaton.initialStates()),
      () -> assertEquals(Set.of("1", "2"), automaton.states()),
      () -> assertEquals(Set.of("1", "2"), getReachableStates(automaton)),
      () -> assertEquals(Set.of("2"), AutomatonUtil.getIncompleteStates(automaton).keySet()),

      () -> assertFalse(automaton.is(Property.DETERMINISTIC)),
      () -> assertFalse(automaton.is(Property.SEMI_DETERMINISTIC)),
      () -> assertFalse(automaton.is(Property.LIMIT_DETERMINISTIC))
    );

    // Relabel (1)
    automaton.updateEdges(automaton.states(), (state, oldEdge) -> oldEdge.mapAcceptance(key -> 2));
    automaton.trim();

    assertAll(
      () -> assertEquals(
        Set.of(Edge.of("1", 2), Edge.of("2"), Edge.of("2", 2)),
        automaton.edges("1", new BitSet())),
      () -> assertEquals(Set.of(), automaton.edges("2", new BitSet()))
    );

    // Relabel (2)
    automaton.updateEdges((state, oldEdge) -> Objects.equals(state, "1")
      ? Edge.of(oldEdge.successor(), 0)
      : Edge.of(oldEdge.successor(), 1));
    automaton.trim();

    assertAll(
      () -> automaton.edgeMap("1").forEach(
        (x, y) -> x.colours().forEach((int z) -> assertEquals(0, z))),
      () -> automaton.edgeMap("2").forEach(
        (x, y) -> x.colours().forEach((int z) -> assertEquals(1, z)))
    );

    assertTrue(automaton.checkConsistency(), "Automaton is inconsistent.");
  }

  /**
   * Returns all states reachable from the initial states.
   *
   * @param automaton
   *     The automaton.
   *
   * @return All from the initial states reachable states.
   */
  static <S> Set<S> getReachableStates(Automaton<S, ?> automaton) {
    Set<S> reachableStates = new HashSet<>(automaton.initialStates());
    Deque<S> workQueue = new ArrayDeque<>(reachableStates);

    while (!workQueue.isEmpty()) {
      for (S successor : automaton.successors(workQueue.remove())) {
        if (reachableStates.add(successor)) {
          workQueue.add(successor);
        }
      }
    }

    return reachableStates;
  }
}
