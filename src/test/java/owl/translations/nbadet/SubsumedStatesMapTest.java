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

import java.util.BitSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import owl.collections.BitSet2;
import owl.collections.Numbering;
import owl.collections.Pair;

public class SubsumedStatesMapTest {

  @Test
  void testSubsumedStates() {
    BitSet bo = new BitSet();
    bo.set(2);
    bo.set(3);
    bo.set(5);

    //test that empty subsumption map does not do anything
    var emptm = SubsumedStatesMap.empty();
    assertTrue(emptm.isEmpty());
    var b = (BitSet)bo.clone();
    for (int i = 0; i < 10; i++) {
      emptm.addSubsumed(i, b);
    }
    assertEquals(bo, b);
    for (int i = 0; i < 10; i++) {
      emptm.removeSubsumed(i, b);
    }
    assertEquals(bo, b);

    //test a non-empty subsumption map

    //identity bimap for states 0-7
    Numbering<Integer> stm = new Numbering<>();
    for (int i = 0; i < 8; i++) {
      stm.lookup((Integer) i);
    }
    //pairs: 0<=1, 1<=0, 2<=3, 5<=4
    Set<Pair<Integer,Integer>> pairs = Set.of(
      Pair.of(0,1), Pair.of(1,0), Pair.of(2,3), Pair.of(5,4)
    );
    var subs = SubsumedStatesMap.of(stm, pairs);
    assertFalse(subs.isEmpty());

    var bs = BitSet2
      .copyOf(stm.asMap().keySet(), stm::lookup);
    var remains = (BitSet)bs.clone();
    var removed = new BitSet();
    bs.stream().forEach(i -> {
      subs.addSubsumed(i, removed);
      subs.removeSubsumed(i, remains);
    });

    assertEquals(BitSet2.copyOf(Set.of(3, 4, 6, 7), stm::lookup), remains);
    assertEquals(BitSet2.copyOf(Set.of(0, 1, 2, 5), stm::lookup), removed);

  }
}
