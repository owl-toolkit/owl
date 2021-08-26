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

package owl.automaton.symbolic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static owl.automaton.Automaton.Property.COMPLETE;
import static owl.automaton.Automaton.Property.LIMIT_DETERMINISTIC;
import static owl.automaton.Automaton.Property.SEMI_DETERMINISTIC;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import owl.automaton.Automaton;
import owl.automaton.EmptyAutomaton;
import owl.automaton.SingletonAutomaton;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.algorithm.LanguageContainment;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.translations.LtlTranslationRepository;
import owl.translations.canonical.DeterministicConstructions;

public class SymbolicAutomatonTest {

  private static final Function<LabelledFormula, Automaton<?, ? extends RabinAcceptance>> LTL_TO_DRA
    = LtlTranslationRepository.LtlToDraTranslation.EKS20.translation(RabinAcceptance.class);

  private static final Function<LabelledFormula, Automaton<?, ? extends BuchiAcceptance>> LTL_TO_NBA
    = LtlTranslationRepository.LtlToNbaTranslation.EKS20.translation(BuchiAcceptance.class);

  @Test
  protected void testEmpty() {
    var emptyAutomaton = EmptyAutomaton.of(
      List.of("a"),
      AllAcceptance.INSTANCE);

    var symbolicEmptyAutomaton = SymbolicAutomaton.of(emptyAutomaton);

    assertSameFields(emptyAutomaton, symbolicEmptyAutomaton.toAutomaton());
  }

  @Test
  protected void testSingleton() {
    var singletonAutomaton = SingletonAutomaton.of(
      List.of("a", "b"),
      new Object(),
      BuchiAcceptance.INSTANCE,
      Set.of(0));

    var symbolicSingletonAutomaton = SymbolicAutomaton.of(singletonAutomaton);

    assertSameFields(singletonAutomaton, symbolicSingletonAutomaton.toAutomaton());
    assertLanguageEquivalence(singletonAutomaton, symbolicSingletonAutomaton.toAutomaton());
  }

  @Test
  protected void testLtl2Dra() {
    var automaton0 = LTL_TO_DRA.apply(LtlParser.parse("a"));
    var symbolic0 = SymbolicAutomaton.of(automaton0);

    assertSameFields(automaton0, symbolic0.toAutomaton());
    assertLanguageEquivalence(automaton0, symbolic0.toAutomaton());

    var automaton1 = LTL_TO_DRA.apply(LtlParser.parse("a | X b"));
    var symbolic1 = SymbolicAutomaton.of(automaton1);

    assertSameFields(automaton1, symbolic1.toAutomaton());
    assertLanguageEquivalence(automaton1, symbolic1.toAutomaton());

    var automaton2 = LTL_TO_DRA.apply(LtlParser.parse("F G c & G F d"));
    var symbolic2 = SymbolicAutomaton.of(automaton2);

    assertSameFields(automaton2, symbolic2.toAutomaton());
    assertLanguageEquivalence(automaton2, symbolic2.toAutomaton());

    var automaton3 = LTL_TO_DRA.apply(LtlParser.parse("a | X b | F G c & G F d"));
    var symbolic3 = SymbolicAutomaton.of(automaton3);

    assertSameFields(automaton3, symbolic3.toAutomaton());
    assertLanguageEquivalence(automaton3, symbolic3.toAutomaton());
  }

  @Test
  protected void testLtl2Nba() {
    var automaton0 = LTL_TO_NBA.apply(LtlParser.parse("a"));
    var symbolic0 = SymbolicAutomaton.of(automaton0);

    assertSameFields(automaton0, symbolic0.toAutomaton());
    assertLanguageEquivalence(automaton0, symbolic0.toAutomaton());

    var automaton1 = LTL_TO_NBA.apply(LtlParser.parse("a | X b"));
    var symbolic1 = SymbolicAutomaton.of(automaton1);

    assertSameFields(automaton1, symbolic1.toAutomaton());
    assertLanguageEquivalence(automaton1, symbolic1.toAutomaton());

    var automaton2 = LTL_TO_NBA.apply(LtlParser.parse("F G c & G F d"));
    var symbolic2 = SymbolicAutomaton.of(automaton2);

    assertSameFields(automaton2, symbolic2.toAutomaton());

    var automaton3 = LTL_TO_NBA.apply(LtlParser.parse("a | X b | F G c & G F d"));
    var symbolic3 = SymbolicAutomaton.of(automaton3);

    assertSameFields(automaton3, symbolic3.toAutomaton());
  }

  @Test
  protected void testSymbolicEncoding() {
    var automaton0 = DeterministicConstructions.SafetyCoSafetyRoundRobin.of(LtlParser.parse("a"));
    var symbolicNumbered0 = SymbolicAutomaton.of(
      automaton0, new NumberingStateEncoderFactory<>());
    var symbolicState0 = SymbolicAutomaton.of(
      automaton0, new BreakpointStateRejectingRoundRobinEncoderFactory());

    assertSameFields(automaton0, symbolicNumbered0.toAutomaton());
    assertLanguageEquivalence(automaton0, symbolicNumbered0.toAutomaton());

    assertSameFields(automaton0, symbolicState0.toAutomaton());
    assertLanguageEquivalence(automaton0, symbolicState0.toAutomaton());

    System.err.println(symbolicNumbered0.bddSize());
    System.err.println(symbolicState0.bddSize());

    var automaton1 = DeterministicConstructions.SafetyCoSafetyRoundRobin.of(LtlParser.parse("a | X b"));
    var symbolicNumbered1 = SymbolicAutomaton.of(
      automaton1, new NumberingStateEncoderFactory<>());
    var symbolicState1 = SymbolicAutomaton.of(
      automaton1, new BreakpointStateRejectingRoundRobinEncoderFactory());

    assertSameFields(automaton1, symbolicNumbered1.toAutomaton());
    assertLanguageEquivalence(automaton1, symbolicNumbered1.toAutomaton());

    assertSameFields(automaton1, symbolicState1.toAutomaton());
    assertLanguageEquivalence(automaton1, symbolicState1.toAutomaton());

    System.err.println(symbolicNumbered1.bddSize());
    System.err.println(symbolicState1.bddSize());

    var automaton2 = DeterministicConstructions.SafetyCoSafetyRoundRobin.of(LtlParser.parse("((((a U b) U c) U d) U e) U f"));
    var symbolicNumbered2 = SymbolicAutomaton.of(
      automaton2, new NumberingStateEncoderFactory<>());
    var symbolicState2 = SymbolicAutomaton.of(
      automaton2, new BreakpointStateRejectingRoundRobinEncoderFactory());

    assertSameFields(automaton2, symbolicNumbered2.toAutomaton());
    assertLanguageEquivalence(automaton2, symbolicNumbered2.toAutomaton());

    assertSameFields(automaton2, symbolicState2.toAutomaton());
    assertLanguageEquivalence(automaton2, symbolicState2.toAutomaton());

    System.err.println(symbolicNumbered2.bddSize());
    System.err.println(symbolicState2.bddSize());
  }

  private static void assertSameFields(Automaton<?, ?> expected, Automaton<?, ?> actual) {
    assertEquals(expected.acceptance(), actual.acceptance());
    assertEquals(expected.atomicPropositions(), actual.atomicPropositions());
    assertEquals(expected.states().size(), actual.states().size());
    assertEquals(expected.initialStates().size(), actual.initialStates().size());
    assertEquals(expected.is(SEMI_DETERMINISTIC), actual.is(SEMI_DETERMINISTIC));
    assertEquals(expected.is(COMPLETE), actual.is(COMPLETE));
    assertEquals(expected.is(LIMIT_DETERMINISTIC), actual.is(LIMIT_DETERMINISTIC));
  }

  private static void assertLanguageEquivalence(Automaton<?, ?> expected, Automaton<?, ?> actual) {
    assertTrue(LanguageContainment.contains(expected, actual));
    assertTrue(LanguageContainment.contains(actual, expected));
  }
}
