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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;

import com.google.common.collect.Maps;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.edge.Edge;
import owl.factories.ValuationSetFactory;
import owl.ltl.EquivalenceClass;
import owl.ltl.parser.LtlParser;
import owl.run.DefaultEnvironment;
import owl.translations.LTL2DAFunction;

public class AutomatonFactoryTest {

  @SuppressWarnings("unchecked")
  @Test
  public void testSingleton() {
    ValuationSetFactory factory = DefaultEnvironment.annotated().factorySupplier()
      .getValuationSetFactory(List.of("a"));
    Object singletonState = new Object();
    Automaton<Object, NoneAcceptance> singleton =
      AutomatonFactory.singleton(factory, singletonState, NoneAcceptance.INSTANCE, Set.of());

    assertThat(singleton.states(), contains(singletonState));
    assertThat(singleton.acceptance(), is(NoneAcceptance.INSTANCE));
    assertThat(singleton.edges(singletonState), contains(Edge.of(singletonState)));
    assertThat(AutomatonUtil.getIncompleteStates(singleton), is(Map.of()));
    assertThat(DefaultImplementations.getReachableStates(singleton), contains(singletonState));
  }

  @Test
  public void testUniverse() {
    ValuationSetFactory factory = DefaultEnvironment.annotated().factorySupplier()
      .getValuationSetFactory(List.of("a"));
    Object singletonState = new Object();
    Automaton<Object, AllAcceptance> singleton =
      AutomatonFactory.singleton(factory, singletonState, AllAcceptance.INSTANCE, Set.of());

    assertThat(singleton.states(), contains(singletonState));
    assertThat(singleton.acceptance(), is(AllAcceptance.INSTANCE));
    //noinspection unchecked
    assertThat(singleton.edges(singletonState), contains(Edge.of(singletonState)));

    assertThat(AutomatonUtil.getIncompleteStates(singleton).entrySet(), empty());
    assertThat(DefaultImplementations.getReachableStates(singleton), contains(singletonState));
    assertThat(singleton.labelledEdges(singletonState),
      is(Map.of(Edge.of(singletonState), factory.universe())));
  }

  @Test
  public void create() {
    var automaton = AutomatonUtil.cast((new LTL2DAFunction(DefaultEnvironment.annotated(), true,
      EnumSet.of(LTL2DAFunction.Constructions.SAFETY))).apply(LtlParser.parse("G a | b R c")),
      EquivalenceClass.class, AllAcceptance.class);

    var initialState = automaton.onlyInitialState();
    var labelledEdges = automaton.labelledEdges(automaton.onlyInitialState());

    automaton.factory().forEach(valuation -> {
      var edge = automaton.edge(initialState, valuation);
      var matchingEdges = Maps.filterValues(labelledEdges, x -> x.contains(valuation)).keySet();

      if (edge == null) {
        assertThat(matchingEdges, empty());
      } else {
        assertThat(matchingEdges, contains(edge));
      }
    });
  }
}
