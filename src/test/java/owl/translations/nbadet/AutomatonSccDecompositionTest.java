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

package owl.translations.nbadet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.OmegaAcceptanceCast;
import owl.automaton.algorithm.SccDecomposition;
import owl.automaton.hoa.HoaReader;
import owl.bdd.FactorySupplier;
import owl.collections.Pair;
import owl.thirdparty.jhoafparser.parser.generated.ParseException;

public class AutomatonSccDecompositionTest {

  //hand-made automaton that should cover essentially all SCC classification cases
  public static final String HOA_NBA_SCCS = """
    HOA: v1
    name: "SCC Test NBA"
    States: 13
    Start: 0
     AP: 1 "b"
    acc-name: Buchi
    Acceptance: 1 Inf(0)
    properties: trans-labels explicit-labels state-acc
    --BODY--
    State: 0
    [!0] 0
    [!0] 1
    [!0] 3
    [!0] 10
    State: 1
    [!0] 1
    [!0] 2
    State: 2
    [!0] 1
    [!0] 9
    State: 3 {0}
    [!0] 9
    [0] 4
    [0] 7
    State: 4 {0}
    [!0] 5
    State: 5
    [0] 4
    [!0] 6
    [!0] 7
    State: 6
    State: 7
    [!0] 7
    [!0] 8
    State: 8 {0}
    [0] 7
    [!0] 9
    State: 9 {0}
    [t] 9
    State: 10 {0}
    [t] 11
    State: 11
    [!0] 10
    [0] 11
    State: 12
    --END--
    """;

  @Test
  void testNbaSccs() throws ParseException {

    final var supplier = FactorySupplier.defaultSupplier();
    final var parsed = HoaReader.read(
      new StringReader(HOA_NBA_SCCS), supplier::getBddSetFactory, null);
    final var nba = OmegaAcceptanceCast.cast(parsed, BuchiAcceptance.class);

    //ugly hack to access state IDs, relies on fact that toString stringifies State ID from HOA file
    assertEquals(0, nba.initialState());

    var scci = SccDecomposition.of(nba);
    //scci.ids().forEach(i -> System.out.println(i + ": " + scci.sccDecomposition().sccs().get(i)));

    //helper to access SCC ids from HOA state IDs

    //does not contain states not reachable from initial st.
    assertEquals(8, scci.sccs().size());

    //SCC of initial state should be first, and topologically ordered SCC ids
    assertEquals(0, scci.index(nba.initialState()));
    assertTrue(
      scci.index(3) < scci.index(7));
    assertTrue(
      scci.index(
        4) < scci.index(6));

    var expTrv = Set.of(
      scci.index(
        3), scci.index(6));
    assertEquals(expTrv, scci.transientSccs());

    var expBot = Set.of(
      scci.index(
        6), scci.index(
        9), scci.index(10));
    assertEquals(expBot, scci.bottomSccs());

    var expDet = Stream.of(0, 3, 4, 6, 9, 10).map(scci::index).collect(Collectors.toSet());
    assertEquals(expDet, scci.deterministicSccs());

    var expRej = Stream.of(0, 1, 3, 6).map(scci::index).collect(Collectors.toSet());
    assertEquals(expRej, scci.rejectingSccs());

    var expAcc = Set.of(
      scci.index(
        4), scci.index(9));
    assertEquals(expAcc, scci.acceptingSccs());

    //check state reachability, which is implemented using SCC reachability

    //reachMat[p][q] = true iff p reaches q.
    //This encodes relation completely for first 10 states of the NBA
    var reachMat = List.of(
      List.of(true, true, true, true, true, true, true, true, true, true),

      List.of(false, true, true, false, false, false, false, false, false, true),
      List.of(false, true, true, false, false, false, false, false, false, true),

      List.of(false, false, false, false, true, true, true, true, true, true),

      List.of(false, false, false, false, true, true, true, true, true, true),
      List.of(false, false, false, false, true, true, true, true, true, true),

      List.of(false, false, false, false, false, false, false, false, false, false),

      List.of(false, false, false, false, false, false, false, true, true, true),
      List.of(false, false, false, false, false, false, false, true, true, true),

      List.of(false, false, false, false, false, false, false, false, false, true));
    //check that computed reachability is correct
    for (int i = 0; i < 10; i++) {
      for (int j = 0; j < 10; j++) {
        assertEquals(reachMat.get(i).get(j), scci.pathExists(i, j),
          "stateReachable incorrect for " + Pair.of(i,j));
      }
    }
    assertTrue(scci.pathExists(0, 10));
    assertTrue(scci.pathExists(0, 11));
    assertTrue(scci.pathExists(10, 11));
    assertTrue(scci.pathExists(11, 10));
    assertFalse(scci.pathExists(10, 0));
    assertFalse(scci.pathExists(11, 0));
  }

}
