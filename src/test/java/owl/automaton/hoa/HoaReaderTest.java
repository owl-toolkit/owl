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

package owl.automaton.hoa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static owl.util.Assertions.assertThat;

import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import jhoafparser.parser.generated.ParseException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import owl.automaton.Automaton;
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
import owl.automaton.hoa.HoaReader.HoaState;
import owl.factories.ValuationSetFactory;
import owl.run.Environment;

class HoaReaderTest {

  private static final Function<List<String>, ValuationSetFactory> FACTORY_SUPPLIER =
    Environment.annotated().factorySupplier()::getValuationSetFactory;

  private static final String HOA_BUCHI = "HOA: v1\n"
    + "States: 2\n"
    + "Start: 1\n"
    + "AP: 1 \"p0\"\n"
    + "acc-name: Buchi\n"
    + "Acceptance: 1 Inf(0)\n"
    + "--BODY--\n"
    + "State: 0 {0}\n"
    + "[t] 0\n"
    + "State: 1\n"
    + "[0] 0\n"
    + "--END--\n";

  private static final String HOA_GENERALIZED_BUCHI = "HOA: v1\n"
    + "name: \"G(p0 M Fp1)\"\n"
    + "States: 1\n"
    + "Start: 0\n"
    + "AP: 2 \"p1\" \"p0\"\n"
    + "acc-name: generalized-Buchi 2\n"
    + "Acceptance: 2 Inf(0)&Inf(1)\n"
    + "properties: trans-labels explicit-labels trans-acc complete\n"
    + "properties: deterministic stutter-invariant\n"
    + "--BODY--\n"
    + "State: 0\n"
    + "[!0&1] 0 {1}\n"
    + "[!0&!1] 0\n"
    + "[0&1] 0 {0 1}\n"
    + "[0&!1] 0 {0}\n"
    + "--END--";

  private static final String HOA_GENERALIZED_RABIN = "HOA: v1\n"
    + "name: \"G(Fa && XFb)\"\n"
    + "States: 2\n"
    + "Start: 0\n"
    + "acc-name:\n"
    + "generalized-Rabin 2 1 2\n"
    + "Acceptance:\n"
    + "5 (Fin(0)&Inf(1))\n"
    + "| (Fin(2)&Inf(3)&Inf(4))\n"
    + "AP: 2 \"a\" \"b\"\n"
    + "--BODY--\n"
    + "State: 0\n"
    + "[ 0 & !1] 0 {3}\n"
    + "[ 0 & 1] 0 {1 3 4}\n"
    + "[!0 & !1] 1 {0}\n"
    + "[!0 & 1] 1 {0 4}\n"
    + "State: 1\n"
    + "[ 0 & !1] 0 {3}\n"
    + "[ 0 & 1] 0 {1 3 4}\n"
    + "[!0 & !1] 1 {0}\n"
    + "[!0 & 1] 1 {0 4}\n"
    + "--END--";

  private static final String HOA_GENERIC = "HOA: v1 \n"
    + "States: 3 \n"
    + "Start: 0 \n"
    + "acc-name: xyz 1 \n"
    + "Acceptance: 2 (Fin(0) & Inf(1)) \n"
    + "AP: 2 \"a\" \"b\" \n" + "--BODY-- \n"
    + "State: 0 \"a U b\" { 0 } \n"
    + "  2  /* !a  & !b */ \n"
    + "  0  /*  a  & !b */ \n"
    + "  1  /* !a  &  b */ \n"
    + "  1  /*  a  &  b */ \n"
    + "State: 1 { 1 } \n"
    + "  1 1 1 1       /* four transitions on one line */ \n"
    + "State: 2 \"sink state\" { 0 } \n"
    + "  2 2 2 2 \n"
    + "--END--";

  private static final String HOA_INVALID = "HOA: v1\n"
    + "States: 2\n"
    + "Start: 0\n"
    + "acc-name: parity min odd 3\n"
    + "Acceptance: 3 Fin(0) & (Inf(1) | Fin(2))\n"
    + "--BODY--\n"
    + "State: 0\n"
    + "[!0 & !1] 1\n";

  private static final String HOA_MISSING_ACC_NAME = "HOA: v1\n"
    + "States: 0\n"
    + "Acceptance: f\n"
    + "--BODY--\n"
    + "--END--";

  private static final String HOA_PARITY = "HOA: v1\n"
    + "States: 2\n"
    + "Start: 0\n"
    + "AP: 2 \"p0\" \"p1\"\n"
    + "acc-name: parity min odd 3\n"
    + "Acceptance: 3 Fin(0) & (Inf(1) | Fin(2))\n"
    + "--BODY--\n"
    + "State: 0\n"
    + "[!0 & !1] 1 {2}\n"
    + "State: 1\n"
    + "[0 & !1] 0 {1}\n"
    + "[!0 & 1] 1\n"
    + "--END--\n";

  private static final String HOA_RABIN = "HOA: v1 \n"
    + "States: 3 \n"
    + "Start: 0 \n"
    + "acc-name: Rabin 1 \n"
    + "Acceptance: 2 (Fin(0) & Inf(1)) \n"
    + "AP: 2 \"a\" \"b\" \n"
    + "--BODY-- \n"
    + "State: 0 \"a U b\" { 0 } \n"
    + "  2  /* !a  & !b */ \n"
    + "  0  /*  a  & !b */ \n"
    + "  1  /* !a  &  b */ \n"
    + "  1  /*  a  &  b */ \n"
    + "State: 1 { 1 } \n"
    + "  1 1 1 1       /* four transitions on one line */ \n"
    + "State: 2 \"sink state\" { 0 } \n"
    + "  2 2 2 2 \n"
    + "--END--";

  private static final String HOA_SIMPLE = "HOA: v1\n"
    + "States: 2\n"
    + "Start: 0\n"
    + "AP: 1 \"p0\"\n"
    + "acc-name: all\n"
    + "Acceptance: 0 t\n"
    + "--BODY--\n"
    + "State: 0\n"
    + "[!0] 1\n"
    + "[0] 0\n"
    + "State: 1\n"
    + "[!0] 0\n"
    + "--END--\n";

  private static Map<Integer, HoaState> getStates(Automaton<HoaState, ?> automaton) {
    Map<Integer, HoaState> states = new LinkedHashMap<>();
    automaton.states().forEach(state ->  {
      int stateId = state.id;
      assertFalse(states.containsKey(stateId));
      states.put(stateId, state);
    });
    assertEquals(states.size(), automaton.size());
    return states;
  }

  @Test
  void readAutomatonBuchi() throws ParseException {
    Automaton<HoaState, BuchiAcceptance> automaton = OmegaAcceptanceCast.cast(
      HoaReader.read(HOA_BUCHI, FACTORY_SUPPLIER),
      BuchiAcceptance.class);
    assertThat(automaton.size(), x -> x == 2);
    var stateMap = getStates(automaton);
    ValuationSetFactory valuationSetFactory = automaton.factory();

    assertThat(automaton.onlyInitialState(), stateMap.get(1)::equals);
    assertThat(automaton.edgeMap(stateMap.get(1)),
      Map.of(Edge.of(stateMap.get(0)), valuationSetFactory.of(0))::equals);
    assertThat(automaton.edgeMap(stateMap.get(0)),
      Map.of(Edge.of(stateMap.get(0), 0), valuationSetFactory.universe())::equals);
  }

  @Test
  void readAutomatonInvalid() {
    Assertions.assertThrows(ParseException.class,
      () -> HoaReader.read(HOA_INVALID, FACTORY_SUPPLIER));
  }

  @Test
  void readAutomatonMissingAccName() {
    Assertions.assertThrows(ParseException.class,
      () -> HoaReader.read(HOA_MISSING_ACC_NAME, FACTORY_SUPPLIER));
  }

  @Test
  void readAutomatonParity() throws ParseException {
    var automaton = HoaReader.read(HOA_PARITY, FACTORY_SUPPLIER);

    assertThat(automaton.acceptance(), ParityAcceptance.class::isInstance);

    var acceptance = (ParityAcceptance) automaton.acceptance();
    assertThat(acceptance.acceptanceSets(), x -> x == 3);
    assertThat(acceptance.parity(), Parity.MIN_ODD::equals);

    HoaState initialState = automaton.onlyInitialState();
    HoaState successor = automaton.successor(initialState, createBitSet(false, false));
    assertThat(successor, Objects::nonNull);

    Edge<HoaState> initialToSucc = automaton.edge(initialState, createBitSet(false, false));
    assertThat(initialToSucc, Objects::nonNull);
    assertThat(initialToSucc.acceptanceSetIterator().nextInt(), x -> x == 2);

    Edge<HoaState> succToInitial = automaton.edge(successor, createBitSet(true, false));
    assertThat(succToInitial, Objects::nonNull);
    assertThat(succToInitial.acceptanceSetIterator().nextInt(), x -> x == 1);

    Edge<HoaState> succToSucc = automaton.edge(successor, createBitSet(false, true));
    assertThat(succToSucc, Objects::nonNull);
    assertFalse(succToSucc.acceptanceSetIterator().hasNext());
  }

  @Test
  void readAutomatonSimple() throws ParseException {
    var automaton = HoaReader.read(HOA_SIMPLE, FACTORY_SUPPLIER);
    assertThat(automaton.size(), x -> x == 2);
    assertThat(automaton.acceptance(), AllAcceptance.class::isInstance);

    var initialState = automaton.onlyInitialState();
    assertThat(initialState.id, x -> x == 0);
    assertThat(automaton.successor(initialState, createBitSet(true)), initialState::equals);

    var successor = automaton.successor(initialState, createBitSet(false));
    assertThat(successor, Objects::nonNull);
    assertThat(successor.id, x -> x == 1);
    assertThat(automaton.successor(successor, createBitSet(false)), initialState::equals);
    assertThat(automaton.successor(successor, createBitSet(true)), Objects::isNull);
  }

  @Test
  void testAcceptanceGeneralizedBuchi() throws ParseException {
    var automaton = HoaReader.read(HOA_GENERALIZED_BUCHI, FACTORY_SUPPLIER);

    assertThat(automaton.size(), x -> x == 1);
    assertThat(automaton.acceptance(), GeneralizedBuchiAcceptance.class::isInstance);
    assertThat(automaton.acceptance().acceptanceSets(), x -> x == 2);
  }

  @Test
  void testAcceptanceGeneralizedRabin() throws ParseException {
    var automaton = HoaReader.read(HOA_GENERALIZED_RABIN, FACTORY_SUPPLIER);

    assertThat(automaton.size(), x -> x == 2);
    assertThat(automaton.acceptance(), GeneralizedRabinAcceptance.class::isInstance);
    assertThat(automaton.acceptance().acceptanceSets(), x -> x == 5);
  }

  @Test
  void testAcceptanceGeneric() throws ParseException {
    var automaton = HoaReader.read(HOA_GENERIC, FACTORY_SUPPLIER);

    assertThat(automaton.size(), x -> x == 3);
    assertThat(automaton.acceptance(), EmersonLeiAcceptance.class::isInstance);
    assertThat(automaton.acceptance().acceptanceSets(), x -> x == 2);
  }

  @Test
  void testAcceptanceRabin() throws ParseException {
    var automaton = HoaReader.read(HOA_RABIN, FACTORY_SUPPLIER);

    assertThat(automaton.size(), x -> x == 3);
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
}
