/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
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

package owl.collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import jhoafparser.parser.generated.ParseException;
import org.junit.jupiter.api.Test;

import owl.automaton.acceptance.BuchiAcceptance;
import owl.translations.nbadet.AutomatonTestUtil;
import owl.translations.nbadet.NbaDetArgs;
import owl.translations.nbadet.NbaDetConf;
import owl.translations.nbadet.NbaDetState;
import owl.translations.nbadet.NbaSccInfoTest;
import owl.translations.nbadet.RankedSlice;
import owl.translations.nbadet.SmartSucc;
import owl.util.BitSetUtil;

public class TrieMapTest {

  @Test
  void testTrieMap() {
    TrieMap<Integer, String> tm = TrieMap.create();
    assertTrue(tm.isEmpty());
    assertFalse(tm.has(List.of(1,2,3), false));
    assertFalse(tm.has(List.of(1,2,3), true));
    assertEquals(Optional.empty(), tm.get(List.of(1,2,3), false));
    assertEquals(Optional.empty(), tm.get(List.of(1,2,3), true));

    assertEquals(0, tm.size());
    tm.put(List.of(1,2,3), "foo");
    assertFalse(tm.isEmpty());
    tm.put(List.of(1,2,3,4), "bar");
    tm.put(List.of(1), "baz");
    tm.put(List.of(1,5), "qux");
    assertEquals(4, tm.size());

    assertEquals(Optional.of("foo"), tm.get(List.of(1,2,3), false));
    assertEquals(Optional.of("bar"), tm.get(List.of(1,2,3,4), false));
    assertEquals(Optional.of("baz"), tm.get(List.of(1), false));
    assertEquals(Optional.of("qux"), tm.get(List.of(1,5), false));
    assertEquals(Optional.empty(), tm.get(List.of(1,2), false));
    assertEquals(Optional.empty(), tm.get(List.of(2), false));
    assertEquals(Optional.empty(), tm.get(List.of(1,2,3,4,6), false));
    assertEquals(Optional.empty(), tm.get(List.of(), false));

    assertEquals(Optional.empty(), tm.getRootValue());
    assertEquals(Optional.of("foo"), tm.traverse(List.of(1,2,3), false)
                                       .orElse(TrieMap.create()).getRootValue());


    assertEquals(Optional.of("foo"), tm.get(List.of(1,2,3), true));
    assertEquals(Optional.of("bar"), tm.get(List.of(1,2,3,4), true));
    assertEquals(Optional.of("baz"), tm.get(List.of(1), true));
    assertEquals(Optional.of("qux"), tm.get(List.of(1,5), true));
    assertEquals(Optional.of("foo"), tm.get(List.of(1,2), true));
    assertEquals(Optional.empty(), tm.get(List.of(2), true));
    assertEquals(Optional.empty(), tm.get(List.of(1,2,3,4,6), true));
    assertEquals(Optional.of("baz"), tm.get(List.of(), true));


    assertTrue(tm.has(List.of(), true));
    assertFalse(tm.has(List.of(), false));
    assertTrue(tm.has(List.of(1), true));
    assertTrue(tm.has(List.of(1), false));
    assertTrue(tm.has(List.of(1,2), true));
    assertFalse(tm.has(List.of(1,2), false));
    assertTrue(tm.has(List.of(1,2,3), true));
    assertTrue(tm.has(List.of(1,2,3), false));
    assertTrue(tm.has(List.of(1,2,3,4), true));
    assertTrue(tm.has(List.of(1,2,3,4), false));
    assertTrue(tm.has(List.of(1,5), true));
    assertTrue(tm.has(List.of(1,5), false));

    assertFalse(tm.has(List.of(2), true));
    assertFalse(tm.has(List.of(2), false));
    assertFalse(tm.has(List.of(1,2,3,4,6), true));
    assertFalse(tm.has(List.of(1,2,3,4,6), false));
  }

  @Test
  void testTrieEncoding() throws ParseException {
    var nba = AutomatonTestUtil.autFromString(NbaSccInfoTest.HOA_NBA_SCCS, BuchiAcceptance.class);

    var args = NbaDetArgs.getDefault().toBuilder()
      .setSepAcc(false).setSepRej(true).setSepDet(true).setSepMix(true).build();
    var conf = NbaDetConf.prepare(nba, Set.of(), args);
    Function<Set<Integer>, BitSet> toBS = s -> BitSetUtil.fromSet(s, conf.aut().stateMap());

    var state = NbaDetState.of(conf, BitSetUtil.all(conf.aut().stateMap()));
    var expectedEncoding = List.of(
      toBS.apply(Set.of(0,1,2,3,4,5,6,7,8,9,10,11)),
      toBS.apply(Set.of(10,11)),
      toBS.apply(Set.of(4,5)),
      toBS.apply(Set.of(9)),
      toBS.apply(Set.of(7,8))
      );
    assertEquals(expectedEncoding, state.toTrieEncoding());
  }

  BitSet toBS(Set<Integer> s) {
    return BitSetUtil.fromSet(s, x -> x, 32);
  }

  @Test
  void testSmartSuccFuncs() {
    var rs0 = RankedSlice.of(List.of(
      Pair.of(toBS(Set.of(1)), 0),
      Pair.of(toBS(Set.of(2)), 0),
      Pair.of(toBS(Set.of(3)), 0),
      Pair.of(toBS(Set.of(4)), 0),
      Pair.of(toBS(Set.of(5)), 0),
      Pair.of(toBS(Set.of(6)), 0),
      Pair.of(toBS(Set.of(7)), 0),
      Pair.of(toBS(Set.of(8)), 0)
    ));

    var rs1 = RankedSlice.of(List.of(
      Pair.of(toBS(Set.of(1,2)), 0),
      Pair.of(toBS(Set.of(3,4)), 0),
      Pair.of(toBS(Set.of(5,6)), 0),
      Pair.of(toBS(Set.of(7,8)), 0)
      ));
    var rs2 = RankedSlice.of(List.of(
      Pair.of(toBS(Set.of(1,2,3)), 0),
      Pair.of(toBS(Set.of(4)), 0),
      Pair.of(toBS(Set.of(5,6)), 0),
      Pair.of(toBS(Set.of(7,8)), 0)
    ));
    var rs3 = RankedSlice.of(List.of(
      Pair.of(toBS(Set.of(1,2)), 0),
      Pair.of(toBS(Set.of(3,4)), 0),
      Pair.of(toBS(Set.of(5)), 0),
      Pair.of(toBS(Set.of(6,7,8)), 0)
    ));

    var rs4 = RankedSlice.of(List.of(
      Pair.of(toBS(Set.of(1,2,3,4)), 0),
      Pair.of(toBS(Set.of(5,6,7,8)), 0)
    ));
    var rs5 = RankedSlice.of(List.of(
      Pair.of(toBS(Set.of(1,2,3,4,5,6,7,8)), 0)
    ));

    //trivial cases
    assertTrue(SmartSucc.finerOrEqual(RankedSlice.empty(), RankedSlice.empty()));
    assertFalse(SmartSucc.finerOrEqual(RankedSlice.empty(), rs0));
    assertFalse(SmartSucc.finerOrEqual(rs0, RankedSlice.empty()));
    assertTrue(SmartSucc.finerOrEqual(rs0, rs0));
    assertTrue(SmartSucc.finerOrEqual(rs1, rs1));
    assertTrue(SmartSucc.finerOrEqual(rs2, rs2));
    assertTrue(SmartSucc.finerOrEqual(rs3, rs3));
    assertTrue(SmartSucc.finerOrEqual(rs4, rs4));
    assertTrue(SmartSucc.finerOrEqual(rs5, rs5));

    //rs0 finer than rest
    assertTrue(SmartSucc.finerOrEqual(rs0, rs1));
    assertTrue(SmartSucc.finerOrEqual(rs0, rs2));
    assertTrue(SmartSucc.finerOrEqual(rs0, rs3));
    assertTrue(SmartSucc.finerOrEqual(rs0, rs4));
    assertTrue(SmartSucc.finerOrEqual(rs0, rs5));
    assertFalse(SmartSucc.finerOrEqual(rs1, rs0));
    assertFalse(SmartSucc.finerOrEqual(rs2, rs0));
    assertFalse(SmartSucc.finerOrEqual(rs3, rs0));
    assertFalse(SmartSucc.finerOrEqual(rs4, rs0));
    assertFalse(SmartSucc.finerOrEqual(rs5, rs0));

    //rs5 rougher than rest
    assertTrue(SmartSucc.finerOrEqual(rs0, rs5));
    assertTrue(SmartSucc.finerOrEqual(rs1, rs5));
    assertTrue(SmartSucc.finerOrEqual(rs2, rs5));
    assertTrue(SmartSucc.finerOrEqual(rs3, rs5));
    assertTrue(SmartSucc.finerOrEqual(rs4, rs5));
    assertFalse(SmartSucc.finerOrEqual(rs5, rs0));
    assertFalse(SmartSucc.finerOrEqual(rs5, rs1));
    assertFalse(SmartSucc.finerOrEqual(rs5, rs2));
    assertFalse(SmartSucc.finerOrEqual(rs5, rs3));
    assertFalse(SmartSucc.finerOrEqual(rs5, rs4));

    //others
    assertTrue(SmartSucc.finerOrEqual(rs1, rs4));
    assertTrue(SmartSucc.finerOrEqual(rs2, rs4));
    assertTrue(SmartSucc.finerOrEqual(rs3, rs4));
    assertFalse(SmartSucc.finerOrEqual(rs4, rs1));
    assertFalse(SmartSucc.finerOrEqual(rs4, rs2));
    assertFalse(SmartSucc.finerOrEqual(rs4, rs3));

    assertFalse(SmartSucc.finerOrEqual(rs1, rs2));
    assertFalse(SmartSucc.finerOrEqual(rs1, rs3));
    assertFalse(SmartSucc.finerOrEqual(rs2, rs3));
    assertFalse(SmartSucc.finerOrEqual(rs3, rs2));
    assertFalse(SmartSucc.finerOrEqual(rs3, rs1));
    assertFalse(SmartSucc.finerOrEqual(rs2, rs1));
  }

  @Test
  void testNotWorse() {
    var ref = SmartSucc.toTrieEncoding(RankedSlice.of(List.of(
      Pair.of(toBS(Set.of(0,7)), 3),
      Pair.of(toBS(Set.of(1)), 5),
      Pair.of(toBS(Set.of(2)), 2),
      Pair.of(toBS(Set.of(3)), 6),
      Pair.of(toBS(Set.of(4)), 4),
      Pair.of(toBS(Set.of(5,6)), 1)
    )));
    ref.add(0, toBS(Set.of(0,1,2,3,4,5,6,7)));

    var merge1 = SmartSucc.toTrieEncoding(RankedSlice.of(List.of(
      Pair.of(toBS(Set.of(0,7,1)), 3),
      Pair.of(toBS(Set.of(2)), 2),
      Pair.of(toBS(Set.of(3)), 6),
      Pair.of(toBS(Set.of(4)), 4),
      Pair.of(toBS(Set.of(5,6)), 1)
    )));
    merge1.add(0, toBS(Set.of(0,1,2,3,4,5,6,7)));

    var merge2 = SmartSucc.toTrieEncoding(RankedSlice.of(List.of(
      Pair.of(toBS(Set.of(0,7,1,2)), 2),
      Pair.of(toBS(Set.of(3,4)), 4),
      Pair.of(toBS(Set.of(5,6)), 1)
    )));
    merge2.add(0, toBS(Set.of(0,1,2,3,4,5,6,7)));

    //forbidden, as 5 with rank 1 moved down
    var merge3 = SmartSucc.toTrieEncoding(RankedSlice.of(List.of(
      Pair.of(toBS(Set.of(0,7)), 3),
      Pair.of(toBS(Set.of(1)), 5),
      Pair.of(toBS(Set.of(2)), 2),
      Pair.of(toBS(Set.of(3,4,5)), 4),
      Pair.of(toBS(Set.of(6)), 1)
    )));
    merge3.add(0, toBS(Set.of(0,1,2,3,4,5,6,7)));

    //forbidden as 7 with rank 3 moved down
    var merge4 = SmartSucc.toTrieEncoding(RankedSlice.of(List.of(
      Pair.of(toBS(Set.of(0)), 3),
      Pair.of(toBS(Set.of(1)), 5),
      Pair.of(toBS(Set.of(2)), 2),
      Pair.of(toBS(Set.of(3,4,7)), 4),
      Pair.of(toBS(Set.of(5,6)), 1)
    )));
    merge4.add(0, toBS(Set.of(0,1,2,3,4,5,6,7)));

    assertTrue(SmartSucc.notWorse(merge1, ref, 2));
    assertTrue(SmartSucc.notWorse(merge2, ref, 2));
    assertFalse(SmartSucc.notWorse(merge3, ref, 2));
    assertFalse(SmartSucc.notWorse(merge4, ref, 2));
  }
}
