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

package owl.translations.canonical;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static owl.translations.canonical.DeterministicConstructionsPortfolio.coSafety;
import static owl.translations.canonical.DeterministicConstructionsPortfolio.fgSafety;
import static owl.translations.canonical.DeterministicConstructionsPortfolio.gfCoSafety;
import static owl.translations.canonical.DeterministicConstructionsPortfolio.safety;
import static owl.translations.canonical.DeterministicConstructionsPortfolio.safetyCoSafety;
import static owl.util.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import owl.automaton.Automaton;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.hoa.HoaWriter;
import owl.collections.BitSet2;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.GOperator;
import owl.ltl.LabelledFormula;
import owl.ltl.Literal;
import owl.ltl.UOperator;
import owl.ltl.XOperator;
import owl.ltl.parser.LtlParser;

@SuppressWarnings("PMD.UnusedPrivateMethod")
class DeterministicConstructionsPortfolioTest {

  @TestInstance(PER_CLASS)
  @Nested
  class CoSafety {
    private List<Arguments> provider() {
      return List.of(
        Arguments.of("a", 2),
        Arguments.of("F a", 2),
        Arguments.of("a U b", 2),
        Arguments.of("a M b", 2),
        Arguments.of("a | X (F b)", 3)
      );
    }

    @ParameterizedTest
    @MethodSource("provider")
    void test(String formula, int expectedSize) {
      var labelledFormula = LtlParser.parse(formula);
      var automaton = coSafety(labelledFormula);
      assertEquals(expectedSize, automaton.states().size(), () -> HoaWriter.toString(automaton));
      assertEdgeConsistency(automaton, false);
      assertThat(automaton.states(), x -> x.stream().noneMatch(EquivalenceClass::isFalse));
      assertThat(automaton.acceptance(), BuchiAcceptance.class::isInstance);
    }

    @Test
    void testThrows() {
      assertThrows(IllegalArgumentException.class,
        () -> coSafety(LtlParser.parse("G a")));
    }
  }

  @TestInstance(PER_CLASS)
  @Nested
  class FgSafety {
    private List<Arguments> provider() {
      return List.of(
        Arguments.of("F G a", 1),
        Arguments.of("F G ((a & X !a) | (!a & X a))", 2),
        Arguments.of("F G ((a & X !b) | (!a & X b))", 2),
        Arguments.of("F G (a | (X a & X X b))", 3)
      );
    }

    @ParameterizedTest
    @MethodSource("provider")
    void test(String formula, int expectedSize) {
      var labelledFormula = LtlParser.parse(formula).nnf();
      var automaton = fgSafety(labelledFormula, false);
      assertEquals(expectedSize, automaton.states().size(), () -> HoaWriter.toString(automaton));
      assertEdgeConsistency(automaton, true);
      assertThat(automaton.states(), x -> x.stream().noneMatch(y -> y.state().isFalse()));
      assertThat(automaton.states(), x -> x.stream().noneMatch(y -> y.state().isTrue()));
      assertThat(automaton.acceptance(), CoBuchiAcceptance.class::isInstance);
    }

    @Test
    void testThrows() {
      assertThrows(IllegalArgumentException.class,
        () -> fgSafety(LtlParser.parse("F a"), false));
      assertThrows(IllegalArgumentException.class,
        () -> fgSafety(LtlParser.parse("F ((G a) & (G b))"), false));
      assertThrows(IllegalArgumentException.class,
        () -> fgSafety(LtlParser.parse("F (G (F a))"), false));
    }
  }

  @TestInstance(PER_CLASS)
  @Nested
  class GCoSafety {
    private List<Arguments> provider() {
      return List.of(
        Arguments.of("G (a U b)", 1),
        Arguments.of("G (a M b | a U b | F a)", 2),
        Arguments.of("G ((a & X b) | (c & X F c))", 5),
        Arguments.of("G ((a & X X b) | (b & X X c) | (c & X X F d))", 78),
        Arguments.of("G ((a & X X b) | (c & X X d) | (e & X X F f))", 78)
      );
    }

    @ParameterizedTest
    @MethodSource("provider")
    void test(String formula, int expectedSize) {
      var labelledFormula = LtlParser.parse(formula).nnf();
      var automaton = safetyCoSafety(labelledFormula);
      assertEquals(expectedSize, automaton.states().size(), () -> HoaWriter.toString(automaton));
      assertEdgeConsistency(automaton, false);
      assertThat(automaton.states(), x -> x.stream().noneMatch(y -> y.all().isFalse()));
      assertThat(automaton.states(), x -> x.stream().noneMatch(y -> y.all().isTrue()));
      assertThat(automaton.acceptance(), BuchiAcceptance.class::isInstance);
    }

    @Test
    void testThrows() {
      assertThrows(IllegalArgumentException.class,
        () -> safetyCoSafety(LtlParser.parse("G (a U (b R c))")));
    }
  }

  @TestInstance(PER_CLASS)
  @Nested
  class GfCoSafety {
    private List<Arguments> provider() {
      return List.of(
        Arguments.of("G F a", 1),
        Arguments.of("G F ((a | X !a) & (!a | X a))", 2),
        Arguments.of("G F ((a | X !b) & (!a | X b))", 2),
        Arguments.of("G F (a & (X a | X X b))", 3)
      );
    }

    @ParameterizedTest
    @MethodSource("provider")
    void test(String formula, int expectedSize) {
      var labelledFormula = LtlParser.parse(formula).nnf();
      var automaton = gfCoSafety(labelledFormula, false);
      assertEquals(expectedSize, automaton.states().size(), () -> HoaWriter.toString(automaton));
      assertEdgeConsistency(automaton, true);
      assertThat(automaton.states(), x -> x.stream().noneMatch(y -> y.state().isFalse()));
      assertThat(automaton.states(), x -> x.stream().noneMatch(y -> y.state().isTrue()));
      assertThat(automaton.acceptance(), BuchiAcceptance.class::isInstance);
    }

    @Test
    void testThrows() {
      assertThrows(IllegalArgumentException.class,
        () -> gfCoSafety(LtlParser.parse("G a"), false));
      assertThrows(IllegalArgumentException.class,
        () -> gfCoSafety(LtlParser.parse("G ((F a) | (F b))"), false));
      assertThrows(IllegalArgumentException.class,
        () -> gfCoSafety(LtlParser.parse("G (F (G a))"), false));
    }
  }

  @TestInstance(PER_CLASS)
  @Nested
  class Safety {
    private List<Arguments> provider() {
      return List.of(
        Arguments.of("a", 2),
        Arguments.of("G a", 1),
        Arguments.of("a W b", 2),
        Arguments.of("a R b", 2),
        Arguments.of("a | X (G b)", 3)
      );
    }

    @ParameterizedTest
    @MethodSource("provider")
    void test(String formula, int expectedSize) {
      var labelledFormula = LtlParser.parse(formula);
      var automaton = safety(labelledFormula);
      assertEquals(expectedSize, automaton.states().size(), () -> HoaWriter.toString(automaton));
      assertEdgeConsistency(automaton, false);
      assertThat(automaton.states(), x -> x.stream().noneMatch(EquivalenceClass::isFalse));
      assertThat(automaton.acceptance(), AllAcceptance.class::isInstance);
    }

    @Test
    void testThrows() {
      assertThrows(IllegalArgumentException.class,
        () -> safety(LtlParser.parse("F a")));
    }
  }

  private static <S> void assertEdgeConsistency(Automaton<S, ?> automaton, boolean complete) {
    assertThat(automaton, x -> x.is(Automaton.Property.DETERMINISTIC));

    if (complete) {
      assertThat(automaton, x -> x.is(Automaton.Property.COMPLETE));
    }

    for (S state : automaton.states()) {
      var expectedEdges = automaton.edgeTree(state);

      for (var valuation : BitSet2.powerSet(automaton.atomicPropositions().size())) {
        assertEquals(expectedEdges.get(valuation), automaton.edges(state, valuation));
      }
    }
  }

  @Disabled
  @ParameterizedTest
  @ValueSource(
    ints = {8, 10, 12, 14, 16})
  void coSafetyDeepNesting(int k) {
    var formula = LabelledFormula.of(
      leftNestedU(k),
      IntStream.range(0, k + 1).mapToObj(Integer::toString).toList());
    var automaton = coSafety(formula);
    automaton.states();
  }

  @Disabled
  @ParameterizedTest
  @ValueSource(
    ints = {8, 16, 32, 64})
  void safetyLargeAlphabet(int k) {
    var automaton = safety(largeAlphabetSafety(k));
    automaton.edgeTree(automaton.initialState());
  }

  @Disabled
  @ParameterizedTest
  @ValueSource(
    ints = {8, 10, 12, 14, 16})
  void safetyLargeStateSpace(int k) {
    var automaton = safety(largeStateSpaceSafety(k));
    automaton.states();
  }

  LabelledFormula largeAlphabetSafety(int k) {
    var conjunction = Conjunction.of(
      IntStream.range(0, k)
        .mapToObj(x -> Disjunction.of(Literal.of(2 * x), Literal.of(2 * x + 1))));
    return LabelledFormula.of(
      GOperator.of(conjunction),
      IntStream.range(0, 2 * k).mapToObj(i -> "a" + i).toList());
  }

  LabelledFormula largeStateSpaceSafety(int k) {
    var disjunction = Disjunction.of(
      IntStream.range(0, k)
        .mapToObj(x -> Conjunction.of(Literal.of(2 * x), XOperator.of(Literal.of(2 * x + 1)))));
    return LabelledFormula.of(
      new GOperator(disjunction),
      IntStream.range(0, 2 * k)
        .mapToObj(i -> "a" + i).toList());
  }

  UOperator leftNestedU(int i) {
    if (i <= 1) {
      return new UOperator(Literal.of(0), Literal.of(1));
    }

    return new UOperator(leftNestedU(i - 1), Literal.of(i));
  }
}
