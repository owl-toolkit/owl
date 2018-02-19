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

package owl.automaton;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import org.junit.Test;
import owl.automaton.Automaton.Property;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.LabelledEdge;
import owl.factories.ValuationSetFactory;
import owl.factories.jbdd.JBddSupplier;

public class HashMapAutomatonTest {
  private static final ValuationSetFactory FACTORY;

  static {
    FACTORY = JBddSupplier.async().getValuationSetFactory(List.of("a"));
  }

  @Test
  public void testEmptyAutomaton() {
    HashMapAutomaton<?, ?> automaton = new HashMapAutomaton<>(FACTORY, AllAcceptance.INSTANCE);
    assertThat(automaton.is(Property.COMPLETE), is(false));
    assertThat(automaton.is(Property.DETERMINISTIC), is(true));
    assertThat(AutomatonUtil.getIncompleteStates(automaton).keySet(), empty());
    assertThat(automaton.getInitialStates(), empty());
    assertThat(automaton.getStates(), empty());
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testRemapAcceptanceDuplicate() {
    // Test that edges which are equal after a remapAcceptance call get merged.

    MutableAutomaton<String, ?> automaton = new HashMapAutomaton<>(FACTORY, AllAcceptance.INSTANCE);

    automaton.addState("1");
    automaton.addState("2");

    BitSet other = new BitSet();
    other.set(0);
    automaton.addEdge("1", new BitSet(), Edge.of("1", 1));
    automaton.addEdge("1", other, Edge.of("2"));
    automaton.addEdge("1", new BitSet(), Edge.of("2", 1));

    automaton.updateEdges((state, edge) -> {
      if (edge.getSuccessor().equals("2")) {
        return Edge.of(edge.getSuccessor());
      }

      if (edge.getSuccessor().equals("1")) {
        return null;
      }

      return edge;
    });

    assertThat(automaton.getLabelledEdges("1"), containsInAnyOrder(
      LabelledEdge.of(Edge.of("2"), FACTORY.universe())));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testSimpleAutomaton() {
    // Test various parts of the implementation on a simple, two-state automaton

    MutableAutomaton<String, ?> automaton =
      new HashMapAutomaton<>(FACTORY, AllAcceptance.INSTANCE);
    automaton.addState("1");
    automaton.addState("2");

    assertThat(automaton.getStates(), containsInAnyOrder("1", "2"));
    assertThat(AutomatonUtil.getReachableStates(automaton), empty());

    automaton.setInitialState("1");
    assertThat(automaton.getInitialState(), is("1"));
    assertThat(automaton.getInitialStates(), containsInAnyOrder("1"));
    assertThat(AutomatonUtil.getReachableStates(automaton), containsInAnyOrder("1"));

    // Add edge
    automaton.addEdge("1", FACTORY.universe(),
      Edge.of("2"));
    assertThat(automaton.getEdge("1", new BitSet()), is(Edge.of("2")));
    assertThat(automaton.getEdges("1", new BitSet()), containsInAnyOrder(Edge.of("2")));
    assertThat(automaton.getLabelledEdges("1"), containsInAnyOrder(
      LabelledEdge.of(Edge.of("2"), FACTORY.universe())));
    assertThat(automaton.getEdge("1", new BitSet()), is(Edge.of("2")));

    assertThat(AutomatonUtil.getReachableStates(automaton), containsInAnyOrder("1", "2"));
    assertThat(automaton.is(Property.DETERMINISTIC), is(true));

    // Add duplicate edge
    automaton.addEdge("1", FACTORY.of(new BitSet()), Edge.of("2"));
    assertThat(Iterators.size(automaton.getLabelledEdges("1").iterator()), is(1));
    assertThat(automaton.getEdge("1", new BitSet()), is(Edge.of("2")));

    assertThat(AutomatonUtil.getReachableStates(automaton), containsInAnyOrder("1", "2"));

    // Add edge with different acceptance
    automaton.addEdge("1", FACTORY.of(new BitSet()), Edge.of("2", 1));
    assertThat(automaton.getEdges("1", new BitSet()),
      containsInAnyOrder(Edge.of("2"), Edge.of("2", 1)));
    assertThat(Iterators.size(automaton.getLabelledEdges("1").iterator()), is(2));

    assertThat(AutomatonUtil.getReachableStates(automaton), containsInAnyOrder("1", "2"));

    automaton.addEdge("1", FACTORY.universe(), Edge.of("1"));
    assertThat(automaton.getEdges("1", new BitSet()),
      containsInAnyOrder(Edge.of("1"), Edge.of("2"), Edge.of("2", 1)));
    assertThat(AutomatonUtil.getReachableStates(automaton), containsInAnyOrder("1", "2"));

    automaton.setInitialStates(Set.of("2"));
    assertThat(automaton.getInitialState(), is("2"));
    assertThat(automaton.getInitialStates(), containsInAnyOrder("2"));
    assertThat(AutomatonUtil.getReachableStates(automaton), containsInAnyOrder("2"));

    assertThat(AutomatonUtil.getIncompleteStates(automaton).keySet(), containsInAnyOrder("2"));

    IntUnaryOperator transformer = key -> 2;
    automaton.updateEdges(automaton.getStates(), (state, edge) ->
      edge.withAcceptance(transformer));
    assertThat(automaton.getEdges("1", new BitSet()),
      containsInAnyOrder(Edge.of("1"), Edge.of("2"), Edge.of("2", 2)));
    assertThat(automaton.getEdges("2", new BitSet()), empty());

    automaton.updateEdges((state, edge) -> Objects.equals(state, "1")
      ? Edge.of(edge.getSuccessor(), 0)
      : Edge.of(edge.getSuccessor(), 1));

    for (LabelledEdge<String> successorEdge : automaton.getLabelledEdges("1")) {
      assertThat(ImmutableList.copyOf(successorEdge.edge.acceptanceSetIterator()),
        containsInAnyOrder(0));
    }

    for (LabelledEdge<String> successorEdge : automaton.getLabelledEdges("2")) {
      assertThat(ImmutableList.copyOf(successorEdge.edge.acceptanceSetIterator()),
        containsInAnyOrder(1));
    }

    assertThat("Automaton is inconsistent.",
      ((HashMapAutomaton<?, ?>) automaton).checkConsistency(), is(true));
  }
}
