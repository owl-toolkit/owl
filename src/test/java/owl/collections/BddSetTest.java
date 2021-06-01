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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static owl.util.Assertions.assertThat;

import java.time.Duration;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import owl.bdd.BddSet;
import owl.bdd.BddSetFactory;
import owl.bdd.MtBdd;

public abstract class BddSetTest {

  private BddSet abcd;
  private BddSet containsA;
  private BddSet empty;
  private BddSet universe;

  @BeforeEach
  void beforeEach() {
    BddSetFactory factory = factory();

    empty = factory.of(false);
    universe = factory.of(true);

    BitSet bs = new BitSet(4);
    bs.flip(0, 4);
    abcd = factory.of(bs, 4);

    bs.clear();
    bs.set(0);
    containsA = factory.of(bs, bs);
  }

  protected abstract BddSetFactory factory();

  @Test
  void testComplement() {
    assertEquals(universe.complement(), empty);
    assertEquals(empty.complement(), universe);
    assertEquals(abcd.complement().complement(), abcd);
    assertNotEquals(abcd.complement(), containsA);
  }

  @Test
  void testForEach() {
    for (BddSet set : List.of(abcd, containsA, empty, universe)) {
      Set<BitSet> forEach = new HashSet<>();
      set.iterator(4).forEachRemaining(
        solution -> forEach.add(BitSet2.copyOf(solution)));

      Set<BitSet> collect = new HashSet<>();

      for (BitSet bitSet : BitSet2.powerSet(4)) {
        if (set.contains(bitSet)) {
          collect.add(BitSet2.copyOf(bitSet));
        }
      }

      assertEquals(collect, forEach);
    }
  }

  @Test
  void testIsUniverse() {
    assertTrue(universe.isUniverse());
    assertFalse(empty.isUniverse());
    assertFalse(abcd.isUniverse());
    assertFalse(containsA.isUniverse());
  }

  @Test
  void testIterator() {
    Set<BitSet> seen = new HashSet<>();
    universe.iterator(4).forEachRemaining(valuation3 -> {
      assertTrue(valuation3.cardinality() <= 4);
      assertTrue(seen.add(valuation3));
    });

    containsA.iterator(4).forEachRemaining(valuation2 -> assertTrue(valuation2.get(0)));

    abcd.iterator(4).forEachRemaining(valuation1 -> {
      assertTrue(valuation1.get(0));
      assertTrue(valuation1.get(1));
      assertTrue(valuation1.get(2));
      assertTrue(valuation1.get(3));
    });

    empty.iterator(4).forEachRemaining(valuation -> fail(
      "empty should be empty, but it contains " + valuation));
  }


  @Test
  void testCreateEmptyValuationSet() {
    var factory = factory();
    assertThat(factory.of(false), BddSet::isEmpty);
  }

  @Test
  void testCreateUniverseValuationSet() {
    var factory = factory();
    var empty = factory.of(false);

    for (BitSet element : BitSet2.powerSet(6)) {
      assertTrue(factory.of(true).contains(element));
      empty = empty.union(factory.of(element, 6));
    }

    assertEquals(factory.of(true), empty);
  }

  @Test
  void testRelabel() {
    var factory = factory();

    BitSet valuation1 = bitSetOf(false, true, true, false, false, true);
    BitSet valuation2 = bitSetOf(false, true, false, true, false, true);
    BddSet valuationSetBefore = factory.of(valuation1, valuation2);

    BitSet valuation3 = bitSetOf(true, true, false, false, false, true);
    BitSet valuation4 = bitSetOf(true, false, true, false, false, true);
    BddSet valuationSetAfter = factory.of(valuation3, valuation4);

    IntUnaryOperator mapping = i -> {
      Objects.checkIndex(i, 6);

      if (i == 0) {
        return 3;
      }

      if (i <= 3) {
        return i - 1;
      }

      return -1;
    };

    assertEquals(factory.of(false), factory.of(false).relabel(mapping));
    assertEquals(valuationSetAfter, valuationSetBefore.relabel(mapping));
    assertEquals(factory.of(true), factory.of(true).relabel(mapping));
  }

  @Test
  void testRelabelThrows() {
    var factory = factory();

    assertThrows(IllegalArgumentException.class, () -> {
      factory.of(1).relabel(i -> -2);
    });

    // A new variable should be inserted.
    assertEquals(
      factory.of(1).relabel(i -> 10),
      factory.of(10));
  }

  @Test
  void testFilter() {
    var factory = factory();

    var tree = MtBdd.of(Set.of(true));
    var filter = factory.of(0);

    assertEquals(
      MtBdd.of(0, MtBdd.of(Set.of(true)), MtBdd.of()),
      filter.intersection(tree));
  }

  // Inefficient, but simple.
  private static BitSet bitSetOf(boolean... bits) {
    BitSet bitSet = new BitSet();

    for (int i = 0; i < bits.length; i++) {
      bitSet.set(i, bits[i]);
    }

    return bitSet;
  }

  @Test
  void testIteratorIllegalArgumentException() {
    BddSetFactory factory = factory();

    assertThrows(IllegalArgumentException.class, () -> {
      BddSet set = factory.of(3);
      set.iterator(2);
    });

    assertThrows(IllegalArgumentException.class, () -> {
      BddSet set = factory.of(3);
      ImmutableBitSet support = ImmutableBitSet.of(1, 4);
      set.iterator(support);
    });
  }

  @Tag("performance")
  @Test
  void testIteratorPerformance() {
    BddSetFactory factory = factory();

    assertTimeout(Duration.ofMillis(300), () -> {
      BddSet set = factory.of(new BitSet(), 300);
      assertEquals(new BitSet(), set.iterator(300).next());
    });

    assertTimeout(Duration.ofMillis(300), () -> {
      BddSet set = factory.of(new BitSet(), 300);
      assertNotEquals(new BitSet(), set.complement().iterator(300).next());
    });
  }
}