/*
 * Copyright (C) 2016  (See AUTHORS)
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import owl.factories.ValuationSetFactory;

public abstract class ValuationSetTest {
  private ValuationSet abcd;
  private ValuationSet containsA;
  private ValuationSet empty;
  private ValuationSet universe;

  @Before
  public void setUp() {
    BiMap<String, Integer> aliases = ImmutableBiMap.of("a", 0, "b", 1, "c", 2, "d", 3);
    ValuationSetFactory factory = setUpFactory(aliases);

    empty = factory.createEmptyValuationSet();
    universe = factory.createUniverseValuationSet();

    BitSet bs = new BitSet(4);
    bs.flip(0, 4);
    abcd = factory.createValuationSet(bs);

    bs.clear();
    bs.set(0);
    containsA = factory.createValuationSet(bs, bs);
  }

  public abstract ValuationSetFactory setUpFactory(BiMap<String, Integer> aliases);

  @Test
  public void testComplement() {
    assertEquals(universe.complement(), empty);
    assertEquals(empty.complement(), universe);
    assertEquals(abcd.complement().complement(), abcd);
    assertNotEquals(abcd.complement(), containsA);
  }

  @Test
  public void testIsUniverse() {
    assertEquals(16, universe.size());
    assertTrue(universe.isUniverse());

    assertFalse(empty.isUniverse());
    assertFalse(abcd.isUniverse());
    assertFalse(containsA.isUniverse());
  }

  @Test
  public void testIterator() {
    Set<BitSet> seen = new HashSet<>();
    for (BitSet valuation : universe) {
      assertTrue(valuation.cardinality() <= 4);
      assertTrue(seen.add(valuation));
    }

    for (BitSet valuation : containsA) {
      assertTrue(valuation.get(0));
    }

    for (BitSet valuation : abcd) {
      assertTrue(valuation.get(0));
      assertTrue(valuation.get(1));
      assertTrue(valuation.get(2));
      assertTrue(valuation.get(3));
    }

    for (BitSet valuation : empty) {
      fail("empty should be empty, but it contains " + valuation);
    }
  }
}