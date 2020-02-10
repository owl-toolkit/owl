/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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

import de.tum.in.naturals.bitset.BitSets;
import java.util.BitSet;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import owl.automaton.Automaton;
import owl.automaton.UltimatelyPeriodicWord;
import owl.automaton.acceptance.RabinAcceptance;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.run.Environment;
import owl.translations.LTL2DAFunction;
import owl.translations.LTL2NAFunction;

class LanguageMembershipTest {

  private static final Function<LabelledFormula, Automaton<?, ?>> deterministicTranslation
    = new LTL2DAFunction(RabinAcceptance.class, Environment.standard());

  private static final Function<LabelledFormula, Automaton<?, ?>> nondeterministicTranslation
    = new LTL2NAFunction(Environment.standard());

  @Test
  void containsDeterministic() {
    var automaton = deterministicTranslation.apply(LtlParser.parse("F G a | G F b | X X c"));

    var wordA = new UltimatelyPeriodicWord(List.of(new BitSet()), List.of(BitSets.of(0)));
    var wordAandB = new UltimatelyPeriodicWord(List.of(), List.of(BitSets.of(0), BitSets.of(1)));
    var wordC = new UltimatelyPeriodicWord(List.of(), List.of(BitSets.of(2)));
    var wordEmpty = new UltimatelyPeriodicWord(List.of(), List.of(new BitSet()));

    assertTrue(LanguageMembership.contains(automaton, wordA));
    assertTrue(LanguageMembership.contains(automaton, wordAandB));
    assertTrue(LanguageMembership.contains(automaton, wordC));
    assertFalse(LanguageMembership.contains(automaton, wordEmpty));
  }

  @Test
  void containsNondeterministic() {
    var automaton = nondeterministicTranslation.apply(LtlParser.parse("F G a | G F b | X X c"));

    var wordA = new UltimatelyPeriodicWord(List.of(new BitSet()), List.of(BitSets.of(0)));
    var wordAandB = new UltimatelyPeriodicWord(List.of(), List.of(BitSets.of(0), BitSets.of(1)));
    var wordC = new UltimatelyPeriodicWord(List.of(), List.of(BitSets.of(2)));
    var wordEmpty = new UltimatelyPeriodicWord(List.of(), List.of(new BitSet()));

    assertTrue(LanguageMembership.contains(automaton, wordA));
    assertTrue(LanguageMembership.contains(automaton, wordAandB));
    assertTrue(LanguageMembership.contains(automaton, wordC));
    assertFalse(LanguageMembership.contains(automaton, wordEmpty));
  }
}