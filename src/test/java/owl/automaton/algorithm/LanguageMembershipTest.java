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

package owl.automaton.algorithm;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import owl.automaton.UltimatelyPeriodicWord;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.RabinAcceptance;
import owl.collections.ImmutableBitSet;
import owl.ltl.parser.LtlParser;
import owl.translations.LtlTranslationRepository;

class LanguageMembershipTest {

  @Test
  void containsDeterministic() {
    var automaton = LtlTranslationRepository.defaultTranslation(
      LtlTranslationRepository.BranchingMode.DETERMINISTIC,
      RabinAcceptance.class).apply(LtlParser.parse("F G a | G F b | X X c"));

    var wordA = new UltimatelyPeriodicWord(
      List.of(ImmutableBitSet.of()), List.of(ImmutableBitSet.of(0)));
    var wordAandB = new UltimatelyPeriodicWord(
      List.of(), List.of(ImmutableBitSet.of(0), ImmutableBitSet.of(1)));
    var wordC = new UltimatelyPeriodicWord(
      List.of(), List.of(ImmutableBitSet.of(2)));
    var wordEmpty = new UltimatelyPeriodicWord(
      List.of(), List.of(ImmutableBitSet.of()));

    assertTrue(LanguageMembership.contains(automaton, wordA));
    assertTrue(LanguageMembership.contains(automaton, wordAandB));
    assertTrue(LanguageMembership.contains(automaton, wordC));
    assertFalse(LanguageMembership.contains(automaton, wordEmpty));
  }

  @Test
  void containsNondeterministic() {
    var automaton = LtlTranslationRepository.defaultTranslation(
      LtlTranslationRepository.BranchingMode.NON_DETERMINISTIC,
      GeneralizedBuchiAcceptance.class).apply(LtlParser.parse("F G a | G F b | X X c"));

    var wordA = new UltimatelyPeriodicWord(
      List.of(ImmutableBitSet.of()), List.of(ImmutableBitSet.of(0)));
    var wordAandB = new UltimatelyPeriodicWord(
      List.of(), List.of(ImmutableBitSet.of(0), ImmutableBitSet.of(1)));
    var wordC = new UltimatelyPeriodicWord(
      List.of(), List.of(ImmutableBitSet.of(2)));
    var wordEmpty = new UltimatelyPeriodicWord(
      List.of(), List.of(ImmutableBitSet.of()));

    assertTrue(LanguageMembership.contains(automaton, wordA));
    assertTrue(LanguageMembership.contains(automaton, wordAandB));
    assertTrue(LanguageMembership.contains(automaton, wordC));
    assertFalse(LanguageMembership.contains(automaton, wordEmpty));
  }
}
