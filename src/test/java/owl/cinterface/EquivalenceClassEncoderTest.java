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

package owl.cinterface;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import owl.collections.ImmutableBitSet;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.Literal;
import owl.ltl.UOperator;
import owl.ltl.parser.LtlParser;
import owl.translations.canonical.DeterministicConstructions;

class EquivalenceClassEncoderTest {

  private static Formula leftNestedU(int depth) {
    assert depth >= 0;
    return depth > 0
      ? UOperator.of(leftNestedU(depth - 1), Literal.of(depth))
      : Literal.of(0);
  }

  @Tag("performance")
  @Test
  void testStateFeaturesExtractionForUTiming() {
    var formula = LabelledFormula.of(leftNestedU(12), IntStream.rangeClosed(0, 12)
      .mapToObj(Integer::toString)
      .toList());
    var encoder = new EquivalenceClassEncoder();
    var automaton = DeterministicConstructions.SafetyCoSafety.of(formula);

    encoder.putAll(automaton.states());

    // Takes ~5 seconds on a MacBook Pro (16-inch, 2019) / 2,6 GHz 6-Core Intel Core i7.
    Assertions.assertTimeout(Duration.ofSeconds(10),
      () -> encoder.disambiguation(automaton.initialState()));

    // Assert that profiles are unique.
    for (var state : automaton.states()) {
      Assertions.assertEquals(0, encoder.disambiguation(state));
    }
  }

  @Test
  void testStateFeaturesExtractionForU() {
    var formula = LtlParser.parse("((a U b) U c) U d");
    var automaton = DeterministicConstructions.SafetyCoSafety.of(formula);
    var encoder = new EquivalenceClassEncoder();

    // Initialise.
    encoder.putAll(automaton.states());

    Set<ImmutableBitSet> allProfiles = new HashSet<>();
    Set<ImmutableBitSet> rejectingProfiles = new HashSet<>();

    // Assert that profiles are unique and load profiles.
    for (var state : automaton.states()) {
      allProfiles.add(encoder.getAllProfile(state));
      rejectingProfiles.add(encoder.getRejectingProfile(state));
      Assertions.assertEquals(0, encoder.disambiguation(state));
    }

    // Assert structure of profiles.
    Assertions.assertEquals(
      Set.of(
        // ((a U b) U c) U d
        ImmutableBitSet.of(0, 1, 2, 3,    5),
        ImmutableBitSet.of(0, 1, 2,    4,    6),
        ImmutableBitSet.of(0, 1, 2,    4, 5),
        ImmutableBitSet.of(0, 1, 2,          6),
        // (a U b) U c
        ImmutableBitSet.of(1, 2, 4, 5),
        ImmutableBitSet.of(1, 2, 4, 6),
        // a U b
        ImmutableBitSet.of(2, 6),
        // True
        ImmutableBitSet.of()),
      allProfiles);

    Assertions.assertEquals(
      Set.of(ImmutableBitSet.of()),
      rejectingProfiles);


  }

  @Test
  void testStateFeaturesExtractionForR() {
    var formula = LtlParser.parse("a R (b R (c R d))");
    var automaton = DeterministicConstructions.SafetyCoSafety.of(formula);
    var encoder = new EquivalenceClassEncoder();

    // Initialise.
    encoder.putAll(automaton.states());

    Set<ImmutableBitSet> allProfiles = new HashSet<>();
    Set<ImmutableBitSet> rejectingProfiles = new HashSet<>();

    // Assert that profiles are unique and load profiles.
    for (var state : automaton.states()) {
      allProfiles.add(encoder.getAllProfile(state));
      rejectingProfiles.add(encoder.getRejectingProfile(state));
      Assertions.assertEquals(0, encoder.disambiguation(state));
    }

    Assertions.assertEquals(
      Set.of(
        ImmutableBitSet.of(),
        ImmutableBitSet.of(2),
        ImmutableBitSet.of(1, 2),
        ImmutableBitSet.of(0, 1, 2)),
      allProfiles);

    Assertions.assertEquals(
      Set.of(ImmutableBitSet.of()),
      rejectingProfiles);
  }

  @Test
  void testStateFeaturesExtractionForGOr() {
    var formula = LtlParser.parse("G ((a & X b) | (c & X d))");
    var automaton = DeterministicConstructions.SafetyCoSafety.of(formula);
    var encoder = new EquivalenceClassEncoder();

    // Initialise.
    encoder.putAll(automaton.states());

    Set<ImmutableBitSet> allProfiles = new HashSet<>();
    Set<ImmutableBitSet> rejectingProfiles = new HashSet<>();

    // Assert that profiles are unique and load profiles.
    for (var state : automaton.states()) {
      allProfiles.add(encoder.getAllProfile(state));
      rejectingProfiles.add(encoder.getRejectingProfile(state));
      Assertions.assertEquals(0, encoder.disambiguation(state));
    }

    Assertions.assertEquals(
      Set.of(
        ImmutableBitSet.of(),
        ImmutableBitSet.of(0),
        ImmutableBitSet.of(1),
        ImmutableBitSet.of(0, 1)),
      allProfiles);

    Assertions.assertEquals(
      Set.of(ImmutableBitSet.of()),
      rejectingProfiles);
  }

  @Test
  void testStateFeaturesExtractionForG() {
    var formula = LtlParser.parse("G (((a U b) U c) U d)");
    var automaton = DeterministicConstructions.SafetyCoSafety.of(formula);
    var encoder = new EquivalenceClassEncoder();

    // Initialise.
    encoder.putAll(automaton.states());

    Set<ImmutableBitSet> allProfiles = new HashSet<>();
    Set<ImmutableBitSet> rejectingProfiles = new HashSet<>();

    // Assert that profiles are unique and load profiles.
    for (var state : automaton.states()) {
      allProfiles.add(encoder.getAllProfile(state));
      rejectingProfiles.add(encoder.getRejectingProfile(state));
      Assertions.assertTrue(encoder.disambiguation(state) <= 1);
    }

    Assertions.assertEquals(
      Set.of(
        ImmutableBitSet.of(1, 0),
        ImmutableBitSet.of(1),
        ImmutableBitSet.of()),
      allProfiles);

    Assertions.assertEquals(
      Set.of(
        ImmutableBitSet.of(0, 1, 2, 4),
        ImmutableBitSet.of(0, 1, 3, 4),
        ImmutableBitSet.of(0, 1, 3, 5),
        ImmutableBitSet.of(0, 1, 5),
        ImmutableBitSet.of(1, 3, 4),
        ImmutableBitSet.of(1, 3, 5),
        ImmutableBitSet.of(5)),
      rejectingProfiles);
  }
}
