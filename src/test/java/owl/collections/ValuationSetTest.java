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

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import de.tum.in.naturals.bitset.BitSets;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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
    List<String> aliases = ImmutableList.of("a", "b", "c", "d");
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

  public abstract ValuationSetFactory setUpFactory(List<String> aliases);

  @Test
  public void testComplement() {
    assertEquals(universe.complement(), empty);
    assertEquals(empty.complement(), universe);
    assertEquals(abcd.complement().complement(), abcd);
    assertNotEquals(abcd.complement(), containsA);
  }

  @SuppressWarnings("UseOfClone")
  @Test
  public void testForEach() {
    for (ValuationSet set : ImmutableSet.of(abcd, containsA, empty, universe)) {
      Set<BitSet> forEach = new HashSet<>();
      set.forEach(solution -> forEach.add((BitSet) solution.clone()));

      Set<BitSet> collect = BitSets.powerSet(4).stream()
        .filter(set::contains)
        .map(BitSet::clone).map(BitSet.class::cast)
        .collect(Collectors.toSet());

      assertThat(forEach, is(collect));
    }
  }

  @SuppressWarnings("UseOfClone")
  @Test
  public void testForEachRestrict() {
    BitSet restriction = new BitSet();
    restriction.set(1);
    restriction.set(3);

    for (ValuationSet set : ImmutableSet.of(abcd, containsA, empty, universe)) {
      Set<BitSet> forEach = new HashSet<>();
      set.forEach(restriction, solution -> forEach.add((BitSet) solution.clone()));

      Set<BitSet> collect = BitSets.powerSet(restriction).stream()
        .filter(set::contains)
        .map(BitSet::clone)
        .map(BitSet.class::cast)
        .collect(Collectors.toSet());

      assertThat(forEach, is(collect));
    }
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
    universe.forEach(valuation -> {
      assertTrue(valuation.cardinality() <= 4);
      assertTrue(seen.add(valuation));
    });

    containsA.forEach(valuation -> assertTrue(valuation.get(0)));

    abcd.forEach(valuation ->  {
      assertTrue(valuation.get(0));
      assertTrue(valuation.get(1));
      assertTrue(valuation.get(2));
      assertTrue(valuation.get(3));
    });

    empty.forEach(valuation -> fail("empty should be empty, but it contains " + valuation));
  }
}