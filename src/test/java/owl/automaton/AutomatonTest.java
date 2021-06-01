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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.edge.Edge;
import owl.bdd.BddSet;
import owl.collections.BitSet2;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.translations.LtlTranslationRepository;

@SuppressWarnings("PMD.UnusedPrivateMethod")
class AutomatonTest {

  private static final Function<LabelledFormula, Automaton<?, ? extends EmersonLeiAcceptance>>
    TRANSLATOR = LtlTranslationRepository.defaultTranslation(
      LtlTranslationRepository.BranchingMode.DETERMINISTIC,
      EmersonLeiAcceptance.class);

  private static final List<LabelledFormula> FORMULAS = List.of(
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

  private static Stream<Arguments> labelledFormulaProvider() {
    return FORMULAS.stream().map(Arguments::of);
  }

  private static Stream<Arguments> automatonProvider() {
    return Stream.of(Arguments.of(
      new AbstractMemoizingAutomaton.EdgeMapImplementation<>(List.of("a", "b"), Set.of("x"),
        AllAcceptance.INSTANCE) {

        @Override
        public Map<Edge<String>, BddSet> edgeMapImpl(String state) {
          return Map.of(Edge.of("x"), factory.of(true), Edge.of("y"), factory.of(0));
        }
      }));
  }

  @ParameterizedTest
  @MethodSource("labelledFormulaProvider")
  void edgeMapTest(LabelledFormula formula) {
    edgeMapTest(TRANSLATOR.apply(formula));
  }

  @ParameterizedTest
  @MethodSource("automatonProvider")
  <S> void edgeMapTest(Automaton<S, ?> automaton) {
    for (S state : automaton.states()) {
      var actualEdges = automaton.edgeMap(state);
      var expectedEdges = new HashMap<Edge<S>, BddSet>();
      int atomicPropositionsSize = automaton.atomicPropositions().size();

      for (var valuation : BitSet2.powerSet(atomicPropositionsSize)) {
        automaton.edges(state, valuation).forEach(
          x -> expectedEdges.merge(x,
            automaton.factory().of(valuation, atomicPropositionsSize), BddSet::union));
      }

      assertEquals(expectedEdges, actualEdges);
    }
  }

  @ParameterizedTest
  @MethodSource("labelledFormulaProvider")
  void edgeTreeTest(LabelledFormula formula) {
    edgeTreeTest(TRANSLATOR.apply(formula));
  }

  @ParameterizedTest
  @MethodSource("automatonProvider")
  <S> void edgeTreeTest(Automaton<S, ?> automaton) {
    for (S state : automaton.states()) {
      var expectedEdges = automaton.edgeMap(state);
      var actualEdges = automaton.edgeTree(state);

      for (var valuation : BitSet2.powerSet(automaton.atomicPropositions().size())) {
        assertEquals(actualEdges.get(valuation), automaton.edges(state, valuation));
      }

      assertEquals(actualEdges, automaton.factory().toMtBdd(expectedEdges));
    }
  }
}
