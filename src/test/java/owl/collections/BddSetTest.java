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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static owl.util.Assertions.assertThat;

import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import owl.bdd.BddSet;
import owl.bdd.BddSetFactory;
import owl.bdd.MtBdd;

public abstract class BddSetTest {

  private static final List<String> ATOMIC_PROPOSITIONS = List.of("a", "b", "c", "d", "e", "f");

  private BddSet abcd;
  private BddSet containsA;
  private BddSet empty;
  private BddSet universe;

  @BeforeEach
  void beforeEach() {
    BddSetFactory factory = factory(List.of("a", "b", "c", "d"));

    empty = factory.of();
    universe = factory.universe();

    BitSet bs = new BitSet(4);
    bs.flip(0, 4);
    abcd = factory.of(bs);

    bs.clear();
    bs.set(0);
    containsA = factory.of(bs, bs);
  }

  protected abstract BddSetFactory factory(List<String> atomicPropositions);

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
      set.toSet().forEach(
        (Consumer<? super BitSet>) solution -> forEach.add(BitSet2.copyOf(solution)));

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
  void testForEachRestrict() {
    BitSet restriction = new BitSet();
    restriction.set(1);
    restriction.set(3);

    for (BddSet set : List.of(abcd, containsA, empty, universe)) {
      Set<BitSet> forEach = new HashSet<>();
      set.forEach(restriction, solution -> forEach.add(BitSet2.copyOf(solution)));

      Set<BitSet> collect = new HashSet<>();
      for (BitSet bitSet : BitSet2.powerSet(restriction)) {
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
    universe.toSet().forEach((Consumer<? super BitSet>) valuation3 -> {
      assertTrue(valuation3.cardinality() <= 4);
      assertTrue(seen.add(valuation3));
    });

    containsA
      .toSet().forEach((Consumer<? super BitSet>) valuation2 -> assertTrue(valuation2.get(0)));

    abcd.toSet().forEach((Consumer<? super BitSet>) valuation1 -> {
      assertTrue(valuation1.get(0));
      assertTrue(valuation1.get(1));
      assertTrue(valuation1.get(2));
      assertTrue(valuation1.get(3));
    });

    empty.toSet().forEach((Consumer<? super BitSet>) valuation -> fail(
      "empty should be empty, but it contains " + valuation));
  }


  @Test
  void testCreateEmptyValuationSet() {
    var factory = factory(ATOMIC_PROPOSITIONS);
    assertThat(factory.of(), BddSet::isEmpty);
  }

  @Test
  void testCreateUniverseValuationSet() {
    var factory = factory(ATOMIC_PROPOSITIONS);
    var empty = factory.of();

    for (BitSet element : BitSet2.powerSet(ATOMIC_PROPOSITIONS.size())) {
      assertTrue(factory.universe().contains(element));
      empty = empty.union(factory.of(element));
    }

    assertEquals(factory.universe(), empty);
  }

  @Test
  void testGetAlphabet() {
    var factory = factory(ATOMIC_PROPOSITIONS);
    assertEquals(ATOMIC_PROPOSITIONS.size(), factory.atomicPropositions().size());
  }

  @Test
  void testRelabel() {
    var factory = factory(ATOMIC_PROPOSITIONS);

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

    assertEquals(factory.of(), factory.of().relabel(mapping));
    assertEquals(valuationSetAfter, valuationSetBefore.relabel(mapping));
    assertEquals(factory.universe(), factory.universe().relabel(mapping));
  }

  @Test
  void testRelabelThrows() {
    var factory = factory(ATOMIC_PROPOSITIONS);

    assertThrows(IllegalArgumentException.class, () -> {
      factory.universe().relabel(i -> -2);
    });

    assertThrows(IllegalArgumentException.class, () -> {
      factory.universe().relabel(i -> 10);
    });
  }

  @Test
  void testFilter() {
    var factory = factory(ATOMIC_PROPOSITIONS);

    var tree = MtBdd.of(Set.of(true));
    var filter = factory.of(0);

    assertEquals(
      MtBdd.of(0, MtBdd.of(Set.of(true)), MtBdd.of()),
      filter.filter(tree));
  }

  // Inefficient, but simple.
  private static BitSet bitSetOf(boolean... bits) {
    BitSet bitSet = new BitSet();

    for (int i = 0; i < bits.length; i++) {
      bitSet.set(i, bits[i]);
    }

    return bitSet;
  }
}