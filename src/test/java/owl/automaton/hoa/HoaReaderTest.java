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
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static owl.util.Assertions.assertThat;

import java.time.Duration;
import java.util.BitSet;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.OmegaAcceptanceCast;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.ParityAcceptance.Parity;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.edge.Edge;
import owl.bdd.BddSetFactory;
import owl.bdd.FactorySupplier;
import owl.thirdparty.jhoafparser.parser.generated.ParseException;

class HoaReaderTest {

  private static final Supplier<BddSetFactory> FACTORY_SUPPLIER
    = FactorySupplier.defaultSupplier()::getBddSetFactory;

  private static final String HOA_BUCHI = """
    HOA: v1
    States: 2
    Start: 1
    AP: 1 "p0"
    acc-name: Buchi
    Acceptance: 1 Inf(0)
    --BODY--
    State: 0 {0}
    [t] 0
    State: 1
    [0] 0
    --END--
    """;

  private static final String HOA_GENERALIZED_BUCHI = """
    HOA: v1
    name: "G(p0 M Fp1)"
    States: 1
    Start: 0
    AP: 2 "p1" "p0"
    acc-name: generalized-Buchi 2
    Acceptance: 2 Inf(0)&Inf(1)
    properties: trans-labels explicit-labels trans-acc complete
    properties: deterministic stutter-invariant
    --BODY--
    State: 0
    [!0&1] 0 {1}
    [!0&!1] 0
    [0&1] 0 {0 1}
    [0&!1] 0 {0}
    --END--""";

  private static final String HOA_GENERALIZED_RABIN = """
    HOA: v1
    name: "G(Fa && XFb)"
    States: 2
    Start: 0
    acc-name:
    generalized-Rabin 2 1 2
    Acceptance:
    5 (Fin(0)&Inf(1))
    | (Fin(2)&Inf(3)&Inf(4))
    AP: 2 "a" "b"
    --BODY--
    State: 0
    [ 0 & !1] 0 {3}
    [ 0 & 1] 0 {1 3 4}
    [!0 & !1] 1 {0}
    [!0 & 1] 1 {0 4}
    State: 1
    [ 0 & !1] 0 {3}
    [ 0 & 1] 0 {1 3 4}
    [!0 & !1] 1 {0}
    [!0 & 1] 1 {0 4}
    --END--""";

  private static final String HOA_GENERIC = """
    HOA: v1\s
    States: 3\s
    Start: 0\s
    acc-name: xyz 1\s
    Acceptance: 2 (Fin(0) & Inf(1))\s
    AP: 2 "a" "b"\s
    --BODY--\s
    State: 0 "a U b" { 0 }\s
    [!0 & !1]  2  /* !a  & !b */\s
    [ 0 & !1]  0  /*  a  & !b */\s
    [!0 &  1]  1  /* !a  &  b */\s
    [ 0 &  1]  1  /*  a  &  b */\s
    State: 1 { 1 }\s
    [t] 1       /* four transitions on one line */\s
    State: 2 "sink state" { 0 }\s
    [t] 2\s
    --END--""";

  private static final String HOA_INVALID = """
    HOA: v1
    States: 2
    Start: 0
    acc-name: parity min odd 3
    Acceptance: 3 Fin(0) & (Inf(1) | Fin(2))
    --BODY--
    State: 0
    [!0 & !1] 1
    """;

  private static final String HOA_MISSING_ACC_NAME = """
    HOA: v1
    States: 0
    Acceptance: f
    --BODY--
    --END--""";

  private static final String HOA_PARITY = """
    HOA: v1
    States: 2
    Start: 0
    AP: 2 "p0" "p1"
    acc-name: parity min odd 3
    Acceptance: 3 Fin(0) & (Inf(1) | Fin(2))
    --BODY--
    State: 0
    [!0 & !1] 1 {2}
    State: 1
    [0 & !1] 0 {1}
    [!0 & 1] 1
    --END--
    """;

  private static final String HOA_PARITY_WITH_MULTI_COLOUR = """
    HOA: v1
    States: 2
    Start: 0
    AP: 2 "p0" "p1"
    acc-name: parity min odd 3
    Acceptance: 3 Fin(0) & (Inf(1) | Fin(2))
    --BODY--
    State: 0
    [!0 & !1] 1 {2}
    State: 1
    [0 & !1] 0 {0 1}
    [!0 & 1] 1
    --END--
    """;

  private static final String HOA_RABIN = """
    HOA: v1\s
    States: 3\s
    Start: 0\s
    acc-name: Rabin 1\s
    Acceptance: 2 (Fin(0) & Inf(1))\s
    AP: 2 "a" "b"\s
    --BODY--\s
    State: 0 "a U b" { 0 }\s
    [!0 & !1]  2  /* !a  & !b */\s
    [ 0 & !1]  0  /*  a  & !b */\s
    [!0 &  1]  1  /* !a  &  b */\s
    [ 0 &  1]  1  /*  a  &  b */\s
    State: 1 { 1 }\s
     [t]       1\s
    State: 2 "sink state" { 0 }\s
     [t] 2 [t] 2 [t] 2 [t] 2\s
    --END--""";

  private static final String HOA_SIMPLE = """
    HOA: v1
    States: 2
    Start: 0
    AP: 1 "p0"
    acc-name: all
    Acceptance: 0 t
    --BODY--
    State: 0
    [!0] 1
    [0] 0
    State: 1
    [!0] 0
    --END--
    """;

  @Test
  void readAutomatonBuchi() throws ParseException {
    var automaton = OmegaAcceptanceCast.cast(
      HoaReader.read(HOA_BUCHI, FACTORY_SUPPLIER, null), BuchiAcceptance.class);
    assertThat(automaton.states().size(), x -> x == 2);
    BddSetFactory valuationSetFactory = automaton.factory();

    assertEquals(1, automaton.initialState());
    assertThat(automaton.edgeMap(1),
      Map.of(Edge.of(0), valuationSetFactory.of(0))::equals);
    assertThat(automaton.edgeMap(0),
      Map.of(Edge.of(0, 0), valuationSetFactory.of(true))::equals);
  }

  @Test
  void readAutomatonInvalid() {
    Assertions.assertThrows(ParseException.class,
      () -> HoaReader.read(HOA_INVALID, FACTORY_SUPPLIER, null));
  }

  @Test
  void readAutomatonMissingAccName() {
    Assertions.assertThrows(ParseException.class,
      () -> HoaReader.read(HOA_MISSING_ACC_NAME, FACTORY_SUPPLIER, null));
  }

  @Test
  void readAutomatonParity() throws ParseException {
    var automaton
      = HoaReader.read(HOA_PARITY, FACTORY_SUPPLIER, null);

    assertThat(automaton.acceptance(), ParityAcceptance.class::isInstance);

    var acceptance = (ParityAcceptance) automaton.acceptance();
    assertThat(acceptance.acceptanceSets(), x -> x == 3);
    assertThat(acceptance.parity(), Parity.MIN_ODD::equals);

    Integer initialState = automaton.initialState();
    Integer successor = automaton.successor(initialState, createBitSet(false, false));
    assertThat(successor, Objects::nonNull);

    Edge<Integer> initialToSucc = automaton.edge(initialState, createBitSet(false, false));
    assertThat(initialToSucc, Objects::nonNull);
    assertThat(initialToSucc.colours().intIterator().nextInt(), x -> x == 2);

    Edge<Integer> succToInitial = automaton.edge(successor, createBitSet(true, false));
    assertThat(succToInitial, Objects::nonNull);
    assertThat(succToInitial.colours().intIterator().nextInt(), x -> x == 1);

    Edge<Integer> succToSucc = automaton.edge(successor, createBitSet(false, true));
    assertThat(succToSucc, Objects::nonNull);
    assertFalse(succToSucc.colours().intIterator().hasNext());
  }

  @Test
  void readAutomatonSimple() throws ParseException {
    var automaton = HoaReader.read(HOA_SIMPLE, FACTORY_SUPPLIER, null);
    assertThat(automaton.states().size(), x -> x == 2);
    assertThat(automaton.acceptance(), AllAcceptance.class::isInstance);

    var initialState = automaton.initialState();
    assertEquals(0, initialState);
    assertThat(automaton.successor(initialState, createBitSet(true)), initialState::equals);

    var successor = automaton.successor(initialState, createBitSet(false));
    assertThat(successor, Objects::nonNull);
    assertEquals(1, successor);
    assertThat(automaton.successor(successor, createBitSet(false)), initialState::equals);
    assertThat(automaton.successor(successor, createBitSet(true)), Objects::isNull);
  }

  @Test
  void testAcceptanceGeneralizedBuchi() throws ParseException {
    var automaton = HoaReader.read(HOA_GENERALIZED_BUCHI, FACTORY_SUPPLIER, null);

    assertEquals(1, automaton.states().size());
    assertThat(automaton.acceptance(), GeneralizedBuchiAcceptance.class::isInstance);
    assertThat(automaton.acceptance().acceptanceSets(), x -> x == 2);
  }

  @Test
  void testAcceptanceGeneralizedRabin() throws ParseException {
    var automaton = HoaReader.read(HOA_GENERALIZED_RABIN, FACTORY_SUPPLIER, null);

    assertEquals(2, automaton.states().size());
    assertThat(automaton.acceptance(), GeneralizedRabinAcceptance.class::isInstance);
    assertThat(automaton.acceptance().acceptanceSets(), x -> x == 5);
  }

  @Test
  void testAcceptanceGeneric() throws ParseException {
    var automaton = HoaReader.read(HOA_GENERIC, FACTORY_SUPPLIER, null);

    assertEquals(3, automaton.states().size());
    assertThat(automaton.acceptance(), EmersonLeiAcceptance.class::isInstance);
    assertThat(automaton.acceptance().acceptanceSets(), x -> x == 2);
  }

  @Test
  void testAcceptanceParityMulti() throws ParseException {
    var automaton = HoaReader.read(HOA_PARITY_WITH_MULTI_COLOUR, FACTORY_SUPPLIER, null);

    assertEquals(2, automaton.states().size());
    assertThat(automaton.acceptance(), ParityAcceptance.class::isInstance);
    assertEquals(3, automaton.acceptance().acceptanceSets());
  }

  @Test
  void testAcceptanceRabin() throws ParseException {
    var automaton = HoaReader.read(HOA_RABIN, FACTORY_SUPPLIER, null);

    assertEquals(3, automaton.states().size());
    assertThat(automaton.acceptance(), RabinAcceptance.class::isInstance);
    assertThat(automaton.acceptance().acceptanceSets(), x -> x == 2);
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

  @Tag("performance")
  @Test
  void testLargeAlphabet() {
    // Assert that parsing happens fast enough. On my machine this takes less than 2s.
    assertTimeout(Duration.ofSeconds(10), () -> {
      var automaton = HoaReader.read(HoaConstants.LARGE_ALPHABET, FACTORY_SUPPLIER, null);

      assertEquals(125, automaton.states().size());
      assertEquals(213, automaton.atomicPropositions().size());
    });
  }
}
