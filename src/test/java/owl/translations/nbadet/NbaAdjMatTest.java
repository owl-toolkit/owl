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
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.BitSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.collections.BitSet2;
import owl.collections.Numbering;
import owl.collections.Pair;
import owl.thirdparty.jhoafparser.parser.generated.ParseException;

public class NbaAdjMatTest {

  @Test
  void testAdjMat() throws ParseException {
    var nba = AutomatonTestUtil.autFromString(
      AutomatonSccDecompositionTest.HOA_NBA_SCCS, BuchiAcceptance.class);

    var idmap = new Numbering<Integer>();
    for (int i = 0; i < nba.states().size(); i++) {
      idmap.lookup((Integer) i);
    }

    var aSinks = NbaLangInclusions.getNbaAccPseudoSinks(nba);
    assertEquals(Set.of(9), aSinks);

    // "fake" state inclusions for state pruning test (we claim 2 <= 3, 7 <= 1, 8 <= 10)
    var subsumed = SubsumedStatesMap.of(idmap, Set.of(Pair.of(2,3), Pair.of(7,1), Pair.of(8,10)));

    var mat = new NbaAdjMat<>(nba, idmap, aSinks, subsumed);

    //sanity check for creation
    assertSame(nba, mat.original());
    assertSame(idmap, mat.stateMap());

    //test succ
    BitSet bitSet7 = mat.succ(0, 0).fst();
    assertEquals(Set.of(0,1,3,10), BitSet2.asSet(bitSet7));
    BitSet bitSet6 = mat.succ(3, 1).snd();
    assertEquals(Set.of(4,7), BitSet2.asSet(bitSet6));

    //test powersucc (which includes optimizations):

    //normal (nothing special)
    var psuc = mat.powerSucc(BitSet2.of(5, 10), BitSet2.fromInt(1));
    BitSet bitSet4 = psuc.snd();
    BitSet bitSet5 = psuc.fst();
    assertEquals(Pair.allPairs(Set.of(4,11),Set.of(11)),
                 Pair.allPairs(BitSet2.asSet(bitSet5), BitSet2.asSet(bitSet4)));

    //accepting sink reached
    var psuc2 = mat.powerSucc(BitSet2.of(0, 3), BitSet2.fromInt(0));
    BitSet bitSet2 = psuc2.snd();
    BitSet bitSet3 = psuc2.fst();
    assertEquals(Pair.allPairs(Set.of(9),Set.of(9)),
                 Pair.allPairs(BitSet2.asSet(bitSet3), BitSet2.asSet(bitSet2)));

    //with applied subsumption of states. 2,7,8 should be pruned
    var psuc3 =  mat.powerSucc(BitSet2.of(0, 1, 7), BitSet2.fromInt(0));
    BitSet bitSet = psuc3.snd();
    BitSet bitSet1 = psuc3.fst();
    assertEquals(Pair.allPairs(Set.of(0,1,3,10),Set.of()),
                 Pair.allPairs(BitSet2.asSet(bitSet1), BitSet2.asSet(bitSet)));

    //acc. sink + subsumption -> acc sink dominates
    var psuc4 =  mat.powerSucc(
      BitSet2.copyOf(Set.of(0, 1, 3, 7)), BitSet2.fromInt(0));
    assertEquals(psuc2, psuc4);
  }
}
