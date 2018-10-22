/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.edge.Edge;
import owl.factories.ValuationSetFactory;
import owl.ltl.EquivalenceClass;
import owl.ltl.parser.LtlParser;
import owl.run.DefaultEnvironment;
import owl.translations.LTL2DAFunction;

class AutomatonFactoryTest {

  private static final ValuationSetFactory factory = DefaultEnvironment.annotated()
    .factorySupplier().getValuationSetFactory(List.of("a"));

  @Test
  void testCopy() {
    var automaton =
      MutableAutomatonFactory.copy(AutomatonUtil.cast(
        (new LTL2DAFunction(DefaultEnvironment.annotated(), true, EnumSet.of(
          LTL2DAFunction.Constructions.SAFETY))).apply(LtlParser.parse("G a | b R c")),
        EquivalenceClass.class, AllAcceptance.class));

    var initialState = automaton.onlyInitialState();
    var edgeMap = automaton.edgeMap(automaton.onlyInitialState());

    automaton.factory().forEach(valuation -> {
      var edge = automaton.edge(initialState, valuation);
      var matchingEdges = Maps.filterValues(edgeMap, x -> x.contains(valuation)).keySet();

      if (edge == null) {
        assertEquals(Set.of(), matchingEdges);
      } else {
        assertEquals(Set.of(edge), matchingEdges);
      }
    });
  }

  @Test
  void testAcceptingSingleton() {
    var state = new Object();
    var states = Set.of(state);
    var edge = Edge.of(state);
    var automaton = AutomatonFactory.singleton(factory, state, AllAcceptance.INSTANCE, Set.of());

    assertAll(
      () -> assertEquals(states, automaton.states()),
      () -> assertEquals(states, automaton.initialStates()),
      () -> assertEquals(states, DefaultImplementations.getReachableStates(automaton)),

      () -> assertEquals(Set.of(edge), automaton.edges(state)),
      () -> assertEquals(Map.of(edge, factory.universe()), automaton.edgeMap(state)),
      () -> assertEquals(Map.of(), AutomatonUtil.getIncompleteStates(automaton)),

      () -> assertSame(AllAcceptance.INSTANCE, automaton.acceptance())
    );
  }

  @Test
  void testRejectingSingleton() {
    var state = new Object();
    var states = Set.of(state);
    var edge = Edge.of(state);
    var automaton = AutomatonFactory.singleton(factory, state, NoneAcceptance.INSTANCE, Set.of());

    assertAll(
      () -> assertEquals(states, automaton.states()),
      () -> assertEquals(states, automaton.initialStates()),
      () -> assertEquals(states, DefaultImplementations.getReachableStates(automaton)),

      () -> assertEquals(Set.of(edge), automaton.edges(state)),
      () -> assertEquals(Map.of(edge, factory.universe()), automaton.edgeMap(state)),
      () -> assertEquals(Map.of(), AutomatonUtil.getIncompleteStates(automaton)),

      () -> assertSame(NoneAcceptance.INSTANCE, automaton.acceptance())
    );
  }
}
