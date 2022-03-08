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

package owl.collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.Iterables;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.command.AutomatonConversionCommands;
import owl.thirdparty.jhoafparser.parser.generated.ParseException;
import owl.translations.nbadet.AutomatonSccDecompositionTest;
import owl.translations.nbadet.AutomatonTestUtil;
import owl.translations.nbadet.NbaDetConf;
import owl.translations.nbadet.NbaDetState;
import owl.translations.nbadet.RankedSlice;
import owl.translations.nbadet.SmartSucc;

public class HashTrieMapTest {

  @Test
  void testTrieMap() {
    HashTrieMap<Integer, String> tm = new HashTrieMap<>();
    assertTrue(tm.isEmpty());
    List<Integer> ks17 = List.of(1,2,3);
    assertNull(tm.get(ks17));
    List<Integer> ks16 = List.of(1,2,3);
    assertFalse(tm.containsKeyWithPrefix(ks16));
    List<Integer> ks35 = List.of(1,2,3);
    assertNull(tm.get(ks35));
    List<Integer> ks34 = List.of(1,2,3);
    assertNull(Iterables.getFirst(tm.subTrie(ks34).values(), null));

    assertEquals(0, tm.size());
    tm.put(List.of(1,2,3), "foo");
    assertFalse(tm.isEmpty());
    tm.put(List.of(1,2,3,4), "bar");
    tm.put(List.of(1), "baz");
    tm.put(List.of(1,5), "qux");
    assertEquals(4, tm.size());

    List<Integer> ks33 = List.of(1,2,3);
    assertEquals("foo", tm.get(ks33));
    List<Integer> ks32 = List.of(1,2,3,4);
    assertEquals("bar", tm.get(ks32));
    List<Integer> ks31 = List.of(1);
    assertEquals("baz", tm.get(ks31));
    List<Integer> ks30 = List.of(1,5);
    assertEquals("qux", tm.get(ks30));
    List<Integer> ks29 = List.of(1,2);
    assertNull(tm.get(ks29));
    List<Integer> ks28 = List.of(2);
    assertNull(tm.get(ks28));
    List<Integer> ks27 = List.of(1,2,3,4,6);
    assertNull(tm.get(ks27));
    List<Integer> ks26 = List.of();
    assertNull(tm.get(ks26));

    assertNull(tm.get(List.of()));
    assertEquals("foo", tm.subTrie(List.of(1, 2, 3)).get(List.of()));


    List<Integer> ks25 = List.of(1,2,3);
    assertEquals("foo", Iterables.getFirst(tm.subTrie(ks25).values(), null));
    List<Integer> ks24 = List.of(1,2,3,4);
    assertEquals("bar", Iterables.getFirst(tm.subTrie(ks24).values(), null));
    List<Integer> ks23 = List.of(1);
    assertEquals("baz", Iterables.getFirst(tm.subTrie(ks23).values(), null));
    List<Integer> ks22 = List.of(1,5);
    assertEquals("qux", Iterables.getFirst(tm.subTrie(ks22).values(), null));
    List<Integer> ks21 = List.of(1,2);
    assertEquals("foo", Iterables.getFirst(tm.subTrie(ks21).values(), null));
    List<Integer> ks20 = List.of(2);
    assertNull(Iterables.getFirst(tm.subTrie(ks20).values(), null));
    List<Integer> ks19 = List.of(1,2,3,4,6);
    assertNull(Iterables.getFirst(tm.subTrie(ks19).values(), null));
    List<Integer> ks18 = List.of();
    assertEquals("baz", Iterables.getFirst(tm.subTrie(ks18).values(), null));


    assertTrue(tm.containsKeyWithPrefix(List.<Integer>of()));
    assertNull(tm.get(List.<Integer>of()));

    assertTrue(tm.containsKeyWithPrefix(List.of(1)));
    assertNotNull(tm.get(List.of(1)));

    assertTrue(tm.containsKeyWithPrefix(List.of(1,2)));
    assertNull(tm.get(List.of(1,2)));

    assertTrue(tm.containsKeyWithPrefix(List.of(1,2,3)));
    assertNotNull(tm.get(List.of(1,2,3)));
    List<Integer> ks7 = List.of(1,2,3,4);
    assertTrue(tm.containsKeyWithPrefix(ks7));
    List<Integer> ks6 = List.of(1,2,3,4);
    assertNotNull(tm.get(ks6));
    List<Integer> ks5 = List.of(1,5);
    assertTrue(tm.containsKeyWithPrefix(ks5));
    List<Integer> ks4 = List.of(1,5);
    assertNotNull(tm.get(ks4));

    List<Integer> ks3 = List.of(2);
    assertFalse(tm.containsKeyWithPrefix(ks3));
    List<Integer> ks2 = List.of(2);
    assertNull(tm.get(ks2));
    List<Integer> ks1 = List.of(1,2,3,4,6);
    assertFalse(tm.containsKeyWithPrefix(ks1));
    List<Integer> ks = List.of(1,2,3,4,6);
    assertNull(tm.get(ks));
  }

  @Test
  void testTrieEncoding() throws ParseException {
    var nba = AutomatonTestUtil.autFromString(
      AutomatonSccDecompositionTest.HOA_NBA_SCCS, BuchiAcceptance.class);

    var args = new AutomatonConversionCommands.Nba2DpaCommand();

    var conf = NbaDetConf.prepare(nba, Set.of(), args);
    var state = NbaDetState.of(conf, BitSet2.copyOf(conf.aut().stateMap().asMap().values()));

    assertEquals(Set.of(
        BitSet2.copyOf(Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11), conf.aut().stateMap()::lookup),
        BitSet2.copyOf(Set.of(10, 11), conf.aut().stateMap()::lookup),
        BitSet2.copyOf(Set.of(4, 5), conf.aut().stateMap()::lookup),
        BitSet2.copyOf(Set.of(9), conf.aut().stateMap()::lookup),
        BitSet2.copyOf(Set.of(7, 8), conf.aut().stateMap()::lookup)
      ),
      Set.copyOf(state.toTrieEncoding()));
  }

  @Test
  void testSmartSuccFuncs() {
    var rs0 = RankedSlice.of(List.of(
      Pair.of(BitSet2.of(1), 0),
      Pair.of(BitSet2.of(2), 0),
      Pair.of(BitSet2.of(3), 0),
      Pair.of(BitSet2.of(4), 0),
      Pair.of(BitSet2.of(5), 0),
      Pair.of(BitSet2.of(6), 0),
      Pair.of(BitSet2.of(7), 0),
      Pair.of(BitSet2.of(8), 0)
    ));

    var rs1 = RankedSlice.of(List.of(
      Pair.of(BitSet2.of(1, 2), 0),
      Pair.of(BitSet2.of(3, 4), 0),
      Pair.of(BitSet2.of(5, 6), 0),
      Pair.of(BitSet2.of(7, 8), 0)
      ));
    var rs2 = RankedSlice.of(List.of(
      Pair.of(BitSet2.of(1, 2, 3), 0),
      Pair.of(BitSet2.of(4), 0),
      Pair.of(BitSet2.of(5, 6), 0),
      Pair.of(BitSet2.of(7, 8), 0)
    ));
    var rs3 = RankedSlice.of(List.of(
      Pair.of(BitSet2.of(1, 2), 0),
      Pair.of(BitSet2.of(3, 4), 0),
      Pair.of(BitSet2.of(5), 0),
      Pair.of(BitSet2.of(6, 7, 8), 0)
    ));

    var rs4 = RankedSlice.of(List.of(
      Pair.of(BitSet2.of(1, 2, 3, 4), 0),
      Pair.of(BitSet2.of(5, 6, 7, 8), 0)
    ));
    var rs5 = RankedSlice.of(List.of(
      Pair.of(BitSet2.of(1, 2, 3, 4, 5, 6, 7, 8), 0)
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
      Pair.of(BitSet2.of(0, 7), 3),
      Pair.of(BitSet2.of(1), 5),
      Pair.of(BitSet2.of(2), 2),
      Pair.of(BitSet2.of(3), 6),
      Pair.of(BitSet2.of(4), 4),
      Pair.of(BitSet2.of(5, 6), 1)
    )));
    ref.add(0, BitSet2.copyOf(Set.of(0, 1, 2, 3, 4, 5, 6, 7)));

    var merge1 = SmartSucc.toTrieEncoding(RankedSlice.of(List.of(
      Pair.of(BitSet2.of(0, 7, 1), 3),
      Pair.of(BitSet2.of(2), 2),
      Pair.of(BitSet2.of(3), 6),
      Pair.of(BitSet2.of(4), 4),
      Pair.of(BitSet2.of(5, 6), 1)
    )));
    merge1.add(0, BitSet2.copyOf(Set.of(0, 1, 2, 3, 4, 5, 6, 7)));

    var merge2 = SmartSucc.toTrieEncoding(RankedSlice.of(List.of(
      Pair.of(BitSet2.copyOf(Set.of(0, 7, 1, 2)), 2),
      Pair.of(BitSet2.of(3, 4), 4),
      Pair.of(BitSet2.of(5, 6), 1)
    )));
    merge2.add(0, BitSet2.copyOf(Set.of(0, 1, 2, 3, 4, 5, 6, 7)));

    //forbidden, as 5 with rank 1 moved down
    var merge3 = SmartSucc.toTrieEncoding(RankedSlice.of(List.of(
      Pair.of(BitSet2.of(0, 7), 3),
      Pair.of(BitSet2.of(1), 5),
      Pair.of(BitSet2.of(2), 2),
      Pair.of(BitSet2.of(3, 4, 5), 4),
      Pair.of(BitSet2.of(6), 1)
    )));
    merge3.add(0, BitSet2.copyOf(Set.of(0, 1, 2, 3, 4, 5, 6, 7)));

    //forbidden as 7 with rank 3 moved down
    var merge4 = SmartSucc.toTrieEncoding(RankedSlice.of(List.of(
      Pair.of(BitSet2.of(0), 3),
      Pair.of(BitSet2.of(1), 5),
      Pair.of(BitSet2.of(2), 2),
      Pair.of(BitSet2.of(3, 4, 7), 4),
      Pair.of(BitSet2.of(5, 6), 1)
    )));
    merge4.add(0, BitSet2.copyOf(Set.of(0, 1, 2, 3, 4, 5, 6, 7)));

    assertTrue(SmartSucc.notWorse(merge1, ref, 2));
    assertTrue(SmartSucc.notWorse(merge2, ref, 2));
    assertFalse(SmartSucc.notWorse(merge3, ref, 2));
    assertFalse(SmartSucc.notWorse(merge4, ref, 2));
  }
}
