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
import static org.junit.Assert.assertThat;

import de.tum.in.naturals.bitset.BitSets;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.run.DefaultEnvironment;
import owl.translations.LTL2DAFunction;

@RunWith(Theories.class)
public class AutomatonTest {

  private static LTL2DAFunction translator = new LTL2DAFunction(DefaultEnvironment.standard(),
    true, EnumSet.allOf(LTL2DAFunction.Constructions.class));

  @DataPoints
  public static final List<LabelledFormula> FORMULAS = List.of(
    LtlParser.parse("true"),
    LtlParser.parse("false"),
    LtlParser.parse("a"),

    LtlParser.parse("! a"),
    LtlParser.parse("a & b"),
    LtlParser.parse("a | b"),
    LtlParser.parse("a -> b"),
    LtlParser.parse("a <-> b"),
    LtlParser.parse("a xor b"),

    LtlParser.parse("F a"),
    LtlParser.parse("G a"),
    LtlParser.parse("X a"),

    LtlParser.parse("a U b"),
    LtlParser.parse("a R b"),
    LtlParser.parse("a W b"),
    LtlParser.parse("a M b"),

    LtlParser.parse("F ((a W b) & c)"),
    LtlParser.parse("F ((a R b) & c)"),
    LtlParser.parse("G ((a M b) | c)"),
    LtlParser.parse("G ((a U b) | c)"),

    LtlParser.parse("G (X (a <-> b))"),
    LtlParser.parse("G (X (a xor b))"),
    LtlParser.parse("(a <-> b) xor (c <-> d)"));

  public static final ValuationSetFactory factory = DefaultEnvironment.standard().factorySupplier()
    .getValuationSetFactory(List.of("a", "b"));

  @DataPoint
  public static final Automaton<?, ?> automaton = AutomatonFactory.create(factory, "x",
    NoneAcceptance.INSTANCE, x -> Map.of(
      Edge.of("x"), factory.universe(),
      Edge.of("y"), factory.of(0)));

  @Theory
  public void labelledEdges(LabelledFormula formula) {
    labelledEdges(translator.apply(formula));
  }

  public <S> void labelledEdges(Automaton<S, ?> automaton) {
    for (S state : automaton.states()) {
      var actualEdges = automaton.edgeMap(state);
      var expectedEdges = new HashMap<Edge<S>, ValuationSet>();

      for (var valuation : BitSets.powerSet(automaton.factory().alphabetSize())) {
        automaton.edges(state, valuation).forEach(
          x -> expectedEdges.merge(x, automaton.factory().of(valuation), ValuationSet::union));
      }

      assertThat(actualEdges, is(expectedEdges));
    }
  }

  @Theory
  public void labelledEdges2(LabelledFormula formula) {
    labelledEdges2(translator.apply(formula));
  }

  @Theory
  public void labelledEdges22(Automaton<?, ?> automaton) {
    labelledEdges2(automaton);
  }

  public <S> void labelledEdges2(Automaton<S, ?> automaton) {
    for (S state : automaton.states()) {
      var expectedEdges = automaton.edgeMap(state);
      var actualEdges = automaton.edgeTree(state);

      for (var valuation : BitSets.powerSet(automaton.factory().alphabetSize())) {
        assertThat(Set.copyOf(actualEdges.get(valuation)),
          is(Set.copyOf(automaton.edges(state, valuation))));
      }

      assertThat(actualEdges, is(automaton.factory().inverse(expectedEdges)));
    }
  }
}