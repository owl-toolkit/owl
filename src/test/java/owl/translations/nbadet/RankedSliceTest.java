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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import owl.collections.BitSet2;
import owl.collections.Numbering;
import owl.collections.Pair;

public class RankedSliceTest {

  @Test
  void testRankedSlices() {
    var rsl = List.of(
      Pair.of(BitSet2.of(0), 3),
      Pair.of(BitSet2.of(1), 5),
      Pair.of(BitSet2.of(2), 2),
      Pair.of(BitSet2.of(3), 6),
      Pair.of(BitSet2.of(4), 4),
      Pair.of(BitSet2.of(5), 1)
    );

    // test creation methods
    var sl = RankedSlice.of(rsl);
    assertSame(rsl, sl.slice());

    var slc = RankedSlice.copy(rsl);
    assertNotSame(rsl, slc.slice());
    assertNotSame(rsl.get(0), slc.slice().get(0));

    var emptyslice = RankedSlice.empty();
    assertEquals(0, emptyslice.slice().size());

    var singl = Pair.of(BitSet2.of(5), 7);
    var singleton = RankedSlice.singleton(singl);
    assertEquals(1, singleton.slice().size());
    assertSame(singl, singleton.slice().get(0));

    // test immutable operations for correct results and immutability
    var slo = RankedSlice.copy(sl.slice());

    var slinc = sl.map(p -> p.mapFst(x ->
      BitSet2.union(x, BitSet2.fromInt(512))).mapSnd(x -> x + 1));
    assertEquals(slo.slice(), sl.slice());
    assertEquals(sl.slice().size(), slinc.slice().size());
    for (int i = 0; i < sl.slice().size(); i++) {
      assertTrue(slinc.slice().get(i).fst().get(9)); //the 512
      assertEquals(sl.slice().get(i).snd() + 1, slinc.slice().get(i).snd());
    }

    var redundant = RankedSlice.of(List.of(
      Pair.of(BitSet2.of(0), 3),
      Pair.of(BitSet2.of(1, 0), 5),
      Pair.of(BitSet2.of(2, 0), 2),
      Pair.of(BitSet2.of(3, 1), 6),
      Pair.of(BitSet2.of(4, 2), 4),
      Pair.of(BitSet2.of(5, 0, 1), 1)
    ));
    var redOrig = RankedSlice.copy(redundant.slice());
    assertEquals(sl, redundant.leftNormalized());
    assertEquals(redOrig, redundant);


    var withEmpty = RankedSlice.of(List.of(
      Pair.of(BitSet2.of(0), 3),
      Pair.of(BitSet2.of(), 8),
      Pair.of(BitSet2.of(1), 5),
      Pair.of(BitSet2.of(), 7),
      Pair.of(BitSet2.of(2), 2),
      Pair.of(BitSet2.of(3), 6),
      Pair.of(BitSet2.of(), 9),
      Pair.of(BitSet2.of(4), 4),
      Pair.of(BitSet2.of(5), 1)
    ));
    var empOrig = RankedSlice.copy(withEmpty.slice());
    assertEquals(sl, withEmpty.withoutEmptySets());
    assertEquals(empOrig, withEmpty);

    Numbering<Integer> idmap = new Numbering<>(32);
    for (int i = 0; i < 32; i++) {
      idmap.lookup((Integer) i);
    }
    SubsumedStatesMap pruneMap = SubsumedStatesMap.of(idmap,
      Set.of(Pair.of(1,0),Pair.of(2,0),Pair.of(4,3),Pair.of(5,3)));
    var withoutUseless = RankedSlice.of(List.of(
      Pair.of(BitSet2.of(0), 3),
      Pair.of(BitSet2.of(), 5),
      Pair.of(BitSet2.of(), 2),
      Pair.of(BitSet2.of(3), 6),
      Pair.of(BitSet2.of(), 4),
      Pair.of(BitSet2.of(), 1)
    ));
    var slOrig = RankedSlice.copy(sl.slice());
    assertEquals(withoutUseless, sl.prunedWithSim(pruneMap));
    assertEquals(slOrig, sl);

    var fullMerged = RankedSlice.of(List.of(
      Pair.of(BitSet2.of(0, 1, 2), 2),
      Pair.of(BitSet2.of(3, 4), 4),
      Pair.of(BitSet2.of(5), 1)
    ));
    assertEquals(fullMerged, sl.fullMerge(2));
    assertEquals(slOrig, sl);
  }

  @Test
  void testTreeAndTrieEnc() {
    //test tree relations
    var forestSlice = RankedSlice.of(List.of(
      Pair.of(BitSet2.of(0), 2), //0
      Pair.of(BitSet2.of(1), 4), //1
      Pair.of(BitSet2.of(2), 1), //2
      Pair.of(BitSet2.of(3), 5), //3
      Pair.of(BitSet2.of(4), 3), //4
      Pair.of(BitSet2.of(5), 0), //5
      Pair.of(BitSet2.of(6), 7), //6
      Pair.of(BitSet2.of(7), 6)  //7
    ));
    var treeRel = forestSlice.getTreeRelations();
    var par = treeRel.fst();
    var lsb = treeRel.snd();
    assertEquals(List.of(2,2,5,4,5,-1,7,-1), par);
    assertEquals(List.of(-1,0,-1,2,2,-1,5,5), lsb);

    //test unprune/prune
    var unprunedExpected = List.of(
      Pair.of(BitSet2.of(0), 2), //0
      Pair.of(BitSet2.of(1), 4), //1
      Pair.of(BitSet2.of(0, 1, 2), 1), //2
      Pair.of(BitSet2.of(3), 5), //3
      Pair.of(BitSet2.of(3, 4), 3), //4
      Pair.of(BitSet2.copyOf(Set.of(0, 1, 2, 3, 4, 5)), 0), //5
      Pair.of(BitSet2.of(6), 7), //6
      Pair.of(BitSet2.of(6, 7), 6)  //7
    );
    var unprComputed = SmartSucc.unprune(forestSlice.slice());
    assertEquals(unprunedExpected, unprComputed);
    assertEquals(forestSlice.slice(), SmartSucc.prune(unprComputed));

    //to and from trie encoding
    var trieencExpected = List.of(
      BitSet2.copyOf(Set.of(0, 1, 2, 3, 4, 5)),
      BitSet2.of(0, 1, 2),
      BitSet2.of(0),
      BitSet2.of(3, 4),
      BitSet2.of(1),
      BitSet2.of(3),
      BitSet2.of(6, 7),
      BitSet2.of(6)
    );
    assertEquals(trieencExpected, SmartSucc.toTrieEncoding(forestSlice));
    assertEquals(forestSlice, SmartSucc.fromTrieEncoding(trieencExpected));
  }

}
