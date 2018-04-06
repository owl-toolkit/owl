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

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
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
import owl.run.DefaultEnvironment;

public class HashMapAutomatonTest {
  private static final ValuationSetFactory FACTORY;

  static {
    FACTORY = DefaultEnvironment.annotated().factorySupplier().getValuationSetFactory(List.of("a"));
  }

  @Test
  public void testEmptyAutomaton() {
    HashMapAutomaton<?, ?> automaton = new HashMapAutomaton<>(FACTORY, AllAcceptance.INSTANCE);
    assertThat(automaton.is(Property.COMPLETE), is(false));
    assertThat(automaton.is(Property.DETERMINISTIC), is(true));
    assertThat(AutomatonUtil.getIncompleteStates(automaton).keySet(), empty());
    assertThat(automaton.initialStates(), empty());
    assertThat(automaton.states(), empty());
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
      if (edge.successor().equals("2")) {
        return Edge.of(edge.successor());
      }

      if (edge.successor().equals("1")) {
        return null;
      }

      return edge;
    });

    assertThat(automaton.labelledEdges("1"), containsInAnyOrder(
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

    assertThat(automaton.states(), containsInAnyOrder("1", "2"));
    assertThat(AutomatonUtil.getReachableStates(automaton), empty());

    automaton.initialState("1");
    assertThat(automaton.initialState(), is("1"));
    assertThat(automaton.initialStates(), containsInAnyOrder("1"));
    assertThat(AutomatonUtil.getReachableStates(automaton), containsInAnyOrder("1"));

    // Add edge
    automaton.addEdge("1", FACTORY.universe(),
      Edge.of("2"));
    assertThat(automaton.edge("1", new BitSet()), is(Edge.of("2")));
    assertThat(automaton.edges("1", new BitSet()), containsInAnyOrder(Edge.of("2")));
    assertThat(automaton.labelledEdges("1"), containsInAnyOrder(
      LabelledEdge.of(Edge.of("2"), FACTORY.universe())));
    assertThat(automaton.edge("1", new BitSet()), is(Edge.of("2")));

    assertThat(AutomatonUtil.getReachableStates(automaton), containsInAnyOrder("1", "2"));
    assertThat(automaton.is(Property.DETERMINISTIC), is(true));

    // Add duplicate edge
    automaton.addEdge("1", FACTORY.of(new BitSet()), Edge.of("2"));
    assertThat(Iterators.size(automaton.labelledEdges("1").iterator()), is(1));
    assertThat(automaton.edge("1", new BitSet()), is(Edge.of("2")));

    assertThat(AutomatonUtil.getReachableStates(automaton), containsInAnyOrder("1", "2"));

    // Add edge with different acceptance
    automaton.addEdge("1", FACTORY.of(new BitSet()), Edge.of("2", 1));
    assertThat(automaton.edges("1", new BitSet()),
      containsInAnyOrder(Edge.of("2"), Edge.of("2", 1)));
    assertThat(Iterators.size(automaton.labelledEdges("1").iterator()), is(2));

    assertThat(AutomatonUtil.getReachableStates(automaton), containsInAnyOrder("1", "2"));

    automaton.addEdge("1", FACTORY.universe(), Edge.of("1"));
    assertThat(automaton.edges("1", new BitSet()),
      containsInAnyOrder(Edge.of("1"), Edge.of("2"), Edge.of("2", 1)));
    assertThat(AutomatonUtil.getReachableStates(automaton), containsInAnyOrder("1", "2"));

    automaton.initialStates(Set.of("2"));
    assertThat(automaton.initialState(), is("2"));
    assertThat(automaton.initialStates(), containsInAnyOrder("2"));
    assertThat(AutomatonUtil.getReachableStates(automaton), containsInAnyOrder("2"));

    assertThat(AutomatonUtil.getIncompleteStates(automaton).keySet(), containsInAnyOrder("2"));

    IntUnaryOperator transformer = key -> 2;
    automaton.updateEdges(automaton.states(), (state, edge) ->
      edge.withAcceptance(transformer));
    assertThat(automaton.edges("1", new BitSet()),
      containsInAnyOrder(Edge.of("1"), Edge.of("2"), Edge.of("2", 2)));
    assertThat(automaton.edges("2", new BitSet()), empty());

    automaton.updateEdges((state, edge) -> Objects.equals(state, "1")
      ? Edge.of(edge.successor(), 0)
      : Edge.of(edge.successor(), 1));

    for (LabelledEdge<String> successorEdge : automaton.labelledEdges("1")) {
      assertThat(Lists.newArrayList(successorEdge.edge.acceptanceSetIterator()),
        containsInAnyOrder(0));
    }

    for (LabelledEdge<String> successorEdge : automaton.labelledEdges("2")) {
      assertThat(Lists.newArrayList(successorEdge.edge.acceptanceSetIterator()),
        containsInAnyOrder(1));
    }

    assertThat("Automaton is inconsistent.",
      ((HashMapAutomaton<?, ?>) automaton).checkConsistency(), is(true));
  }
}
