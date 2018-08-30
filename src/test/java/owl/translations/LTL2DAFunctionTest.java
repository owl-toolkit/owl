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

package owl.translations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static owl.util.Assertions.assertThat;

import de.tum.in.naturals.bitset.BitSets;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import owl.automaton.Automaton;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.output.HoaPrinter;
import owl.ltl.EquivalenceClass;
import owl.ltl.parser.LtlParser;
import owl.run.DefaultEnvironment;
import owl.run.Environment;

@SuppressWarnings("PMD.UnusedPrivateMethod")
class LTL2DAFunctionTest {
  private static final Environment environment = DefaultEnvironment.standard();

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
      var automaton = LTL2DAFunction.coSafety(environment, labelledFormula);
      assertEquals(expectedSize, automaton.size(), () -> HoaPrinter.toString(automaton));
      assertEdgeConsistency(automaton, false);
      assertThat(automaton.states(), x -> x.stream().noneMatch(EquivalenceClass::isFalse));
      assertThat(automaton.acceptance(), BuchiAcceptance.class::isInstance);
    }

    @Test
    void testThrows() {
      assertThrows(IllegalArgumentException.class,
        () -> LTL2DAFunction.coSafety(environment, LtlParser.parse("G a")));
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
      var automaton = LTL2DAFunction.fgSafety(environment, labelledFormula);
      assertEquals(expectedSize, automaton.size(), () -> HoaPrinter.toString(automaton));
      assertEdgeConsistency(automaton, true);
      assertThat(automaton.states(), x -> x.stream().noneMatch(EquivalenceClass::isFalse));
      assertThat(automaton.states(), x -> x.stream().noneMatch(EquivalenceClass::isTrue));
      assertThat(automaton.acceptance(), CoBuchiAcceptance.class::isInstance);
    }

    @Test
    void testThrows() {
      assertThrows(IllegalArgumentException.class,
        () -> LTL2DAFunction.fgSafety(environment, LtlParser.parse("F a")));
      assertThrows(IllegalArgumentException.class,
        () -> LTL2DAFunction.fgSafety(environment, LtlParser.parse("F ((G a) & (G b))")));
      assertThrows(IllegalArgumentException.class,
        () -> LTL2DAFunction.fgSafety(environment, LtlParser.parse("F (G (F a))")));
    }
  }

  @TestInstance(PER_CLASS)
  @Nested
  class GCoSafety {
    private List<Arguments> provider() {
      return List.of(
        Arguments.of("G (a U b)", 1),
        Arguments.of("G (a M b | a U b | F a)", 2),
        Arguments.of("G ((a & X b) | (c & X F c))", 8),
        Arguments.of("G ((a & X X b) | (b & X X c) | (c & X X F d))", 228),
        Arguments.of("G ((a & X X b) | (c & X X d) | (e & X X F f))", 228)
      );
    }

    @ParameterizedTest
    @MethodSource("provider")
    void test(String formula, int expectedSize) {
      var labelledFormula = LtlParser.parse(formula).nnf();
      var automaton = LTL2DAFunction.gCoSafety(environment, labelledFormula);
      assertEquals(expectedSize, automaton.size(), () -> HoaPrinter.toString(automaton));
      assertEdgeConsistency(automaton, false);
      assertThat(automaton.states(), x -> x.stream().noneMatch(
        y -> y.current().isFalse() || y.next().isFalse()));
      assertThat(automaton.states(), x -> x.stream().noneMatch(y -> y.current().isTrue()));
      assertThat(automaton.acceptance(), BuchiAcceptance.class::isInstance);
    }

    @Test
    void testThrows() {
      assertThrows(IllegalArgumentException.class,
        () -> LTL2DAFunction.gCoSafety(environment, LtlParser.parse("G a")));
      assertThrows(IllegalArgumentException.class,
        () -> LTL2DAFunction.gCoSafety(environment, LtlParser.parse("G F a")));
      assertThrows(IllegalArgumentException.class,
        () -> LTL2DAFunction.gCoSafety(environment, LtlParser.parse("G (a U (b R c))")));
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
      var automaton = LTL2DAFunction.gfCoSafety(environment, labelledFormula);
      assertEquals(expectedSize, automaton.size(), () -> HoaPrinter.toString(automaton));
      assertEdgeConsistency(automaton, true);
      assertThat(automaton.states(), x -> x.stream().noneMatch(EquivalenceClass::isFalse));
      assertThat(automaton.states(), x -> x.stream().noneMatch(EquivalenceClass::isTrue));
      assertThat(automaton.acceptance(), BuchiAcceptance.class::isInstance);
    }

    @Test
    void testThrows() {
      assertThrows(IllegalArgumentException.class,
        () -> LTL2DAFunction.gfCoSafety(environment, LtlParser.parse("G a")));
      assertThrows(IllegalArgumentException.class,
        () -> LTL2DAFunction.gfCoSafety(environment, LtlParser.parse("G ((F a) | (F b))")));
      assertThrows(IllegalArgumentException.class,
        () -> LTL2DAFunction.gfCoSafety(environment, LtlParser.parse("G (F (G a))")));
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
      var automaton = LTL2DAFunction.safety(environment, labelledFormula);
      assertEquals(expectedSize, automaton.size(), () -> HoaPrinter.toString(automaton));
      assertEdgeConsistency(automaton, false);
      assertThat(automaton.states(), x -> x.stream().noneMatch(EquivalenceClass::isFalse));
      assertThat(automaton.acceptance(), AllAcceptance.class::isInstance);
    }

    @Test
    void testThrows() {
      assertThrows(IllegalArgumentException.class,
        () -> LTL2DAFunction.safety(environment, LtlParser.parse("F a")));
    }
  }

  private static <S> void assertEdgeConsistency(Automaton<S, ?> automaton, boolean complete) {
    assertThat(automaton, x -> x.is(Automaton.Property.DETERMINISTIC));

    if (complete) {
      assertThat(automaton, x -> x.is(Automaton.Property.COMPLETE));
    }

    for (S state : automaton.states()) {
      var expectedEdges = automaton.edgeTree(state);

      for (var valuation : BitSets.powerSet(automaton.factory().alphabetSize())) {
        assertEquals(expectedEdges.get(valuation), automaton.edges(state, valuation));
      }
    }
  }
}