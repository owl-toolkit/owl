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

package owl.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.HashBiMap;

import java.util.BitSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import owl.collections.Pair;

public class MiscUtilTest {

  @Test
  void testPair() {
    Pair<Integer,Integer> p1 = Pair.of(1,2);
    Pair<Integer,Integer> p1b = Pair.of(1,2);

    assertEquals("(1,2)", p1.toString());

    var p2 = p1b.swap();
    var p1c = p2.swap();

    assertEquals(p1, p1b);
    assertEquals(p1, p1c);
    assertEquals(p1.snd(), p2.fst());
    assertEquals(p2.fst(), p1.snd());

    var p3 = p1b.mapFst(x -> x + 1).mapSnd(x -> x + 2);
    assertEquals(2, p3.fst());
    assertEquals(4, p3.snd());
  }

  @Test
  void bitsetImmutableSetOps() {
    // a = {1,2}, b = {2,3}
    BitSet ao = new BitSet();
    BitSet bo = new BitSet();
    ao.set(1);
    ao.set(2);
    bo.set(2);
    bo.set(3);

    //take copies to work with
    BitSet a = (BitSet)ao.clone();
    BitSet b = (BitSet)bo.clone();

    //test set operations and check that they do not change original bitset
    var c = BitSetUtil.intersection(a,b);
    assertTrue(!c.get(1) && c.get(2) && !c.get(3));
    assertEquals(ao, a);
    assertEquals(bo, b);

    var d = BitSetUtil.union(a,b);
    assertTrue(d.get(1) && d.get(2) && d.get(3));
    assertEquals(ao, a);
    assertEquals(bo, b);


    var e = BitSetUtil.without(a,b);
    assertTrue(e.get(1) && !e.get(2) && !e.get(3));
    assertEquals(ao, a);
    assertEquals(bo, b);


    var f = BitSetUtil.without(b,a);
    assertTrue(!f.get(1) && !f.get(2) && f.get(3));
    assertEquals(ao, a);
    assertEquals(bo, b);
  }

  @Test
  void bitsetEncodeDecode() {
    //empty and all
    assertEquals(new BitSet(), BitSetUtil.empty());

    HashBiMap<Integer,Integer> smap = HashBiMap.create();
    smap.put(1,2);
    smap.put(2,4);
    smap.put(3,6);
    smap.put(4,8);

    //set all bits according to provided mapping
    var bsAll = BitSetUtil.all(smap);
    assertTrue(!bsAll.get(0) && !bsAll.get(1) && !bsAll.get(3)
      && !bsAll.get(5) && !bsAll.get(7));
    assertTrue(bsAll.get(2) && bsAll.get(4) && bsAll.get(6) && bsAll.get(8));

    //fromSet and toSet
    Set<Integer> subset = Set.of(2,4);
    var bsSubset = BitSetUtil.fromSet(subset, smap);
    assertTrue(bsSubset.get(4) && bsSubset.get(8) && !bsSubset.get(1)
      && !bsSubset.get(2) && !bsSubset.get(6));
    assertEquals(subset, BitSetUtil.toSet(bsSubset, smap.inverse()::get));

    //fromInt and toInt
    int intSet = 2 + 8 + 32;
    var bsFI = BitSetUtil.fromInt(intSet);
    assertTrue(!bsFI.get(0) && !bsFI.get(2) && !bsFI.get(4)  && !bsFI.get(6));
    assertTrue(bsFI.get(1) && bsFI.get(3) && bsFI.get(5));
    assertEquals(intSet, BitSetUtil.toInt(bsFI));
  }

}
