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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static owl.automaton.DefaultImplementations.getReachableStates;
import static owl.util.Assertions.assertThat;

import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;
import owl.automaton.Automaton.Property;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.edge.Edge;
import owl.factories.ValuationSetFactory;
import owl.run.Environment;

class HashMapAutomatonTest {
  private static final ValuationSetFactory FACTORY;

  static {
    FACTORY = Environment.annotated().factorySupplier().getValuationSetFactory(List.of("a"));
  }

  @Test
  void testEmptyAutomaton() {
    HashMapAutomaton<?, ?> automaton = new HashMapAutomaton<>(FACTORY, AllAcceptance.INSTANCE);
    assertFalse(automaton.is(Property.COMPLETE));
    assertTrue(automaton.is(Property.DETERMINISTIC));
    assertThat(AutomatonUtil.getIncompleteStates(automaton).keySet(), Collection::isEmpty);
    assertThat(automaton.initialStates(), Collection::isEmpty);
    assertThat(automaton.states(), Collection::isEmpty);
  }

  @Test
  void testRemapAcceptanceDuplicate() {
    // Test that edges which are equal after a remapAcceptance call get merged.

    MutableAutomaton<String, ?> automaton = new HashMapAutomaton<>(FACTORY, AllAcceptance.INSTANCE);

    automaton.addInitialState("1");
    automaton.addInitialState("2");

    BitSet other = new BitSet();
    other.set(0);
    automaton.addEdge("1", new BitSet(), Edge.of("1", 1));
    automaton.addEdge("1", other, Edge.of("2"));
    automaton.addEdge("1", new BitSet(), Edge.of("2", 1));
    automaton.updateEdges((state, edge) -> {
      if (edge.successor().equals("2")) {
        return Edge.of(edge.successor());
      }

      if (edge.successor().equals("1")) {
        return null;
      }

      return edge;
    });
    automaton.trim();

    assertThat(automaton.edgeMap("1").entrySet(),
      x -> x.contains(Map.entry(Edge.of("2"), FACTORY.universe())));
  }

  @Test
  void testSimpleAutomaton() {
    // Test various parts of the implementation on a simple, two-state automaton
    var automaton = new HashMapAutomaton<>(FACTORY, BuchiAcceptance.INSTANCE);

    automaton.addState("1");
    automaton.addState("2");
    automaton.trim();

    assertAll(
      () -> assertEquals(Set.of(), automaton.states()),
      () -> assertEquals(Set.of(), getReachableStates(automaton))
    );

    automaton.addInitialState("1");

    assertAll(
      () -> assertEquals("1", automaton.onlyInitialState()),
      () -> assertEquals(Set.of("1"), automaton.initialStates()),
      () -> assertEquals(Set.of("1"), automaton.states()),
      () -> assertEquals(Set.of("1"), getReachableStates(automaton))
    );

    // Add edge
    var edge = Edge.of("2");
    automaton.addEdge("1", FACTORY.universe(), edge);

    assertAll(
      () -> assertEquals(edge, automaton.edge("1", new BitSet())),
      () -> assertEquals(Set.of(edge), automaton.edges("1", new BitSet())),
      () -> assertEquals(Map.of(edge, FACTORY.universe()), automaton.edgeMap("1")),

      () -> assertEquals(Set.of("1", "2"), automaton.states()),
      () -> assertEquals(Set.of("1", "2"), getReachableStates(automaton)),

      () -> assertTrue(automaton.is(Property.DETERMINISTIC)),
      () -> assertTrue(automaton.is(Property.SEMI_DETERMINISTIC)),
      () -> assertTrue(automaton.is(Property.LIMIT_DETERMINISTIC))
    );

    // Add duplicate edge
    automaton.addEdge("1", FACTORY.of(new BitSet()), edge);

    assertAll(
      () -> assertEquals(edge, automaton.edge("1", new BitSet())),
      () -> assertEquals(Set.of(edge), automaton.edges("1", new BitSet())),
      () -> assertEquals(Map.of(edge, FACTORY.universe()), automaton.edgeMap("1")),

      () -> assertEquals(Set.of("1", "2"), automaton.states()),
      () -> assertEquals(Set.of("1", "2"), getReachableStates(automaton)),

      () -> assertTrue(automaton.is(Property.DETERMINISTIC)),
      () -> assertTrue(automaton.is(Property.SEMI_DETERMINISTIC)),
      () -> assertTrue(automaton.is(Property.LIMIT_DETERMINISTIC))
    );

    // Add edge with different acceptance
    var edgeWithAcceptance = edge.withAcceptance(0);
    automaton.addEdge("1", FACTORY.of(new BitSet()), edgeWithAcceptance);

    assertAll(
      () -> assertEquals(Set.of(edge, edgeWithAcceptance), automaton.edges("1", new BitSet())),
      () -> assertEquals(
        Map.of(edge, FACTORY.universe(), edgeWithAcceptance, FACTORY.of(new BitSet())),
        automaton.edgeMap("1")),

      () -> assertEquals(Set.of("1", "2"), automaton.states()),
      () -> assertEquals(Set.of("1", "2"), getReachableStates(automaton)),

      () -> assertFalse(automaton.is(Property.DETERMINISTIC)),
      () -> assertFalse(automaton.is(Property.SEMI_DETERMINISTIC)),
      () -> assertTrue(automaton.is(Property.LIMIT_DETERMINISTIC))
    );

    // Add loop edge
    var loop = Edge.of("1", 0);
    automaton.addEdge("1", FACTORY.universe(), loop);

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
      () -> assertThrows(IllegalStateException.class, automaton::onlyInitialState),
      () -> assertEquals(Set.of("1", "2"), automaton.initialStates()),
      () -> assertEquals(Set.of("1", "2"), automaton.states()),
      () -> assertEquals(Set.of("1", "2"), getReachableStates(automaton)),
      () -> assertEquals(Set.of("2"), AutomatonUtil.getIncompleteStates(automaton).keySet()),

      () -> assertFalse(automaton.is(Property.DETERMINISTIC)),
      () -> assertFalse(automaton.is(Property.SEMI_DETERMINISTIC)),
      () -> assertFalse(automaton.is(Property.LIMIT_DETERMINISTIC))
    );

    // Relabel (1)
    automaton.updateEdges(automaton.states(), (state, oldEdge) -> oldEdge.withAcceptance(key -> 2));
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
      () -> automaton.edgeMap("1").forEach((x, y)
      -> x.acceptanceSetIterator().forEachRemaining((int z) -> assertEquals(0, z))),
      () -> automaton.edgeMap("2").forEach((x, y)
      -> x.acceptanceSetIterator().forEachRemaining((int z) -> assertEquals(1, z)))
    );

    assertTrue(automaton.checkConsistency(), "Automaton is inconsistent.");
  }
}
