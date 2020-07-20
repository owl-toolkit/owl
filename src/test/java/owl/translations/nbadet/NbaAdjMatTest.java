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

package owl.translations.nbadet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.Maps;
import java.util.BitSet;
import java.util.Set;
import java.util.function.Function;
import jhoafparser.parser.generated.ParseException;
import org.junit.jupiter.api.Test;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.collections.Pair;
import owl.util.BitSetUtil;

public class NbaAdjMatTest {

  @Test
  void testAdjMat() throws ParseException {
    var nba = AutomatonTestUtil.autFromString(NbaSccInfoTest.HOA_NBA_SCCS, BuchiAcceptance.class);

    var idmap = ImmutableBiMap.copyOf(Maps.asMap(nba.states(), st -> st));
    Function<BitSet, Set<Integer>> fromBS = bs -> BitSetUtil.toSet(bs, Function.identity());

    var aSinks = NbaLangInclusions.getNbaAccPseudoSinks(nba);
    assertEquals(Set.of(9), aSinks);

    // "fake" state inclusions for state pruning test (we claim 2 <= 3, 7 <= 1, 8 <= 10)
    var subsumed = SubsumedStatesMap.of(idmap, Set.of(Pair.of(2,3), Pair.of(7,1), Pair.of(8,10)));

    var mat = new NbaAdjMat<>(nba, idmap, aSinks, subsumed);

    //sanity check for creation
    assertSame(nba, mat.original());
    assertSame(idmap, mat.stateMap());
    assertEquals(nba.states(), fromBS.apply(mat.states()));

    //test succ
    assertEquals(Set.of(0,1,3,10), fromBS.apply(mat.succ(0, 0).fst()));
    assertEquals(Set.of(4,7), fromBS.apply(mat.succ(3, 1).snd()));

    //test powersucc (which includes optimizations):

    //normal (nothing special)
    var psuc = mat.powerSucc(BitSetUtil.fromSet(Set.of(5,10),idmap), BitSetUtil.fromInt(1));
    assertEquals(Pair.allPairs(Set.of(4,11),Set.of(11)),
                 Pair.allPairs(fromBS.apply(psuc.fst()), fromBS.apply(psuc.snd())));

    //accepting sink reached
    var psuc2 = mat.powerSucc(BitSetUtil.fromSet(Set.of(0,3),idmap), BitSetUtil.fromInt(0));
    assertEquals(Pair.allPairs(Set.of(9),Set.of(9)),
                 Pair.allPairs(fromBS.apply(psuc2.fst()), fromBS.apply(psuc2.snd())));

    //with applied subsumption of states. 2,7,8 should be pruned
    var psuc3 =  mat.powerSucc(BitSetUtil.fromSet(Set.of(0,1,7),idmap), BitSetUtil.fromInt(0));
    assertEquals(Pair.allPairs(Set.of(0,1,3,10),Set.of()),
                 Pair.allPairs(fromBS.apply(psuc3.fst()), fromBS.apply(psuc3.snd())));

    //acc. sink + subsumption -> acc sink dominates
    var psuc4 =  mat.powerSucc(BitSetUtil.fromSet(Set.of(0,1,3,7),idmap), BitSetUtil.fromInt(0));
    assertEquals(psuc2, psuc4);
  }
}
