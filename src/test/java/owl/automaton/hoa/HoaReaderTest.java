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

package owl.automaton.hoa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static owl.util.Assertions.assertThat;

import java.time.Duration;
import java.util.BitSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import owl.automaton.Automaton;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.edge.Edge;
import owl.bdd.BddSetFactory;
import owl.thirdparty.jhoafparser.parser.generated.ParseException;

class HoaReaderTest {

  @Test
  void testAll() throws ParseException {
    var automaton = HoaReader.read(HoaExampleRepository.ALL);

    assertEquals(AllAcceptance.class, automaton.acceptance().getClass());
    assertEquals(0, automaton.acceptance().acceptanceSets());

    assertEquals(Set.of(0, 1), automaton.states());
    assertEquals(Set.of(0), automaton.initialStates());
    assertFalse(automaton.is(Automaton.Property.COMPLETE));
    assertTrue(automaton.is(Automaton.Property.DETERMINISTIC));

    var initialState = automaton.initialState();
    assertEquals(initialState, automaton.successor(initialState, createBitSet(true)));

    var successor = automaton.successor(initialState, createBitSet(false));
    assertNotNull(successor);
    assertEquals(1, successor);
    assertEquals(initialState, automaton.successor(successor, createBitSet(false)));
    assertNull(automaton.successor(successor, createBitSet(true)));
  }

  @Test
  void testBuchi() throws ParseException {
    var automaton = HoaReader.read(HoaExampleRepository.BUCHI);

    assertEquals(BuchiAcceptance.class, automaton.acceptance().getClass());
    assertEquals(1, automaton.acceptance().acceptanceSets());

    assertEquals(Set.of(0, 1), automaton.states());
    assertEquals(Set.of(1), automaton.initialStates());
    assertFalse(automaton.is(Automaton.Property.COMPLETE));
    assertTrue(automaton.is(Automaton.Property.DETERMINISTIC));

    BddSetFactory valuationSetFactory = automaton.factory();
    assertEquals(Map.of(Edge.of(0), valuationSetFactory.of(0)), automaton.edgeMap(1));
    assertEquals(Map.of(Edge.of(0, 0), valuationSetFactory.of(true)), automaton.edgeMap(0));
  }

  @Test
  void testGeneralizedBuchi() throws ParseException {
    var automaton = HoaReader.read(HoaExampleRepository.GENERALIZED_BUCHI);

    assertEquals(GeneralizedBuchiAcceptance.class, automaton.acceptance().getClass());
    assertEquals(2, automaton.acceptance().acceptanceSets());

    assertEquals(Set.of(0), automaton.states());
    assertEquals(Set.of(0), automaton.initialStates());
    assertTrue(automaton.is(Automaton.Property.COMPLETE));
    assertTrue(automaton.is(Automaton.Property.DETERMINISTIC));
  }

  @Test
  void testParity() throws ParseException {
    var automaton = HoaReader.read(HoaExampleRepository.PARITY);

    assertEquals(ParityAcceptance.class, automaton.acceptance().getClass());
    assertEquals(3, automaton.acceptance().acceptanceSets());
    assertEquals(ParityAcceptance.Parity.MIN_ODD, ((ParityAcceptance) automaton.acceptance()).parity());

    Integer initialState = automaton.initialState();
    Integer successor = automaton.successor(initialState, createBitSet(false, false));
    assertThat(successor, Objects::nonNull);

    Edge<Integer> initialToSucc = automaton.edge(initialState, createBitSet(false, false));
    assertNotNull(initialToSucc);
    assertThat(initialToSucc.colours().intIterator().nextInt(), x -> x == 2);

    Edge<Integer> succToInitial = automaton.edge(successor, createBitSet(true, false));
    assertNotNull(succToInitial);
    assertThat(succToInitial.colours().intIterator().nextInt(), x -> x == 1);

    Edge<Integer> succToSucc = automaton.edge(successor, createBitSet(false, true));
    assertNotNull(succToSucc);
    assertFalse(succToSucc.colours().intIterator().hasNext());
  }

  @Test
  void testParityWithMultipleColours() throws ParseException {
    var automaton = HoaReader.read(HoaExampleRepository.PARITY_WITH_MULTI_COLOUR);

    assertEquals(ParityAcceptance.class, automaton.acceptance().getClass());
    assertEquals(3, automaton.acceptance().acceptanceSets());

    assertEquals(Set.of(0, 1), automaton.states());
    assertEquals(Set.of(0), automaton.initialStates());
    assertFalse(automaton.is(Automaton.Property.COMPLETE));
    assertTrue(automaton.is(Automaton.Property.DETERMINISTIC));
  }

  @Test
  void testRabin() throws ParseException {
    var automaton = HoaReader.read(HoaExampleRepository.RABIN);

    assertEquals(RabinAcceptance.class, automaton.acceptance().getClass());
    assertEquals(2, automaton.acceptance().acceptanceSets());

    assertEquals(Set.of(0, 1, 2), automaton.states());
    assertEquals(Set.of(0), automaton.initialStates());
    assertTrue(automaton.is(Automaton.Property.COMPLETE));
    assertTrue(automaton.is(Automaton.Property.DETERMINISTIC));
  }

  @Test
  void testRabinWithoutAccName() throws ParseException {
    var automaton = HoaReader.read(HoaExampleRepository.RABIN_WITHOUT_ACC_NAME);

    assertEquals(RabinAcceptance.class, automaton.acceptance().getClass());
    assertEquals(2, automaton.acceptance().acceptanceSets());

    assertEquals(Set.of(0, 1, 2), automaton.states());
    assertEquals(Set.of(0), automaton.initialStates());
    assertTrue(automaton.is(Automaton.Property.COMPLETE));
    assertTrue(automaton.is(Automaton.Property.DETERMINISTIC));
  }

  @Test
  void testGeneralizedRabin() throws ParseException {
    var automaton = HoaReader.read(HoaExampleRepository.GENERALIZED_RABIN);

    assertEquals(GeneralizedRabinAcceptance.class, automaton.acceptance().getClass());
    assertEquals(5, automaton.acceptance().acceptanceSets());

    assertEquals(Set.of(0, 1), automaton.states());
    assertEquals(Set.of(0), automaton.initialStates());
    assertTrue(automaton.is(Automaton.Property.COMPLETE));
    assertTrue(automaton.is(Automaton.Property.DETERMINISTIC));
  }

  @Test
  void testEmersonLei() throws ParseException {
    var automaton = HoaReader.read(HoaExampleRepository.EMERSON_LEI);

    assertEquals(EmersonLeiAcceptance.class, automaton.acceptance().getClass());
    assertEquals(3, automaton.acceptance().acceptanceSets());

    assertEquals(Set.of(0, 1, 2), automaton.states());
    assertEquals(Set.of(0), automaton.initialStates());
    assertTrue(automaton.is(Automaton.Property.COMPLETE));
    assertTrue(automaton.is(Automaton.Property.DETERMINISTIC));
  }

  @Test
  void testLargeAlphabet() {
    // Assert that parsing happens fast enough. On my machine this takes less than 1s.
    assertTimeout(Duration.ofSeconds(4), () -> {
      var automaton = HoaReader.read(HoaExampleRepository.LARGE_ALPHABET);

      assertEquals(125, automaton.states().size());
      assertEquals(213, automaton.atomicPropositions().size());
    });
  }

  @Test
  void testIncomplete() {
    Assertions.assertThrows(ParseException.class, () -> HoaReader.read(HoaExampleRepository.INCOMPLETE));
  }

  @Test
  void readAutomatonMissingAccName() {
    Assertions.assertThrows(ParseException.class, () -> HoaReader.read(HoaExampleRepository.MISSING_ACC_NAME_FIELD));
  }

  private static BitSet createBitSet(boolean... indices) {
    BitSet bitSet = new BitSet(indices.length);
    for (int i = 0; i < indices.length; i++) {
      if (indices[i]) {
        bitSet.set(i);
      }
    }

    return bitSet;
  }
}
