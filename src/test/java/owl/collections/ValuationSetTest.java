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

import de.tum.in.naturals.bitset.BitSets;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import owl.factories.ValuationSetFactory;

public abstract class ValuationSetTest {

  private static final List<String> ATOMIC_PROPOSITIONS = List.of("a", "b", "c", "d", "e", "f");

  private ValuationSet abcd;
  private ValuationSet containsA;
  private ValuationSet empty;
  private ValuationSet universe;

  @BeforeEach
  void beforeEach() {
    ValuationSetFactory factory = factory(List.of("a", "b", "c", "d"));

    empty = factory.empty();
    universe = factory.universe();

    BitSet bs = new BitSet(4);
    bs.flip(0, 4);
    abcd = factory.of(bs);

    bs.clear();
    bs.set(0);
    containsA = factory.of(bs, bs);
  }

  protected abstract ValuationSetFactory factory(List<String> atomicPropositions);

  @Test
  void testComplement() {
    assertEquals(universe.complement(), empty);
    assertEquals(empty.complement(), universe);
    assertEquals(abcd.complement().complement(), abcd);
    assertNotEquals(abcd.complement(), containsA);
  }

  @Test
  void testForEach() {
    for (ValuationSet set : List.of(abcd, containsA, empty, universe)) {
      Set<BitSet> forEach = new HashSet<>();
      set.forEach(solution -> forEach.add(BitSet2.copyOf(solution)));

      Set<BitSet> collect = BitSets.powerSet(4).stream()
        .filter(set::contains)
        .map(BitSet2::copyOf)
        .collect(Collectors.toSet());

      assertEquals(collect, forEach);
    }
  }

  @Test
  void testForEachRestrict() {
    BitSet restriction = new BitSet();
    restriction.set(1);
    restriction.set(3);

    for (ValuationSet set : List.of(abcd, containsA, empty, universe)) {
      Set<BitSet> forEach = new HashSet<>();
      set.forEach(restriction, solution -> forEach.add(BitSet2.copyOf(solution)));

      Set<BitSet> collect = BitSets.powerSet(restriction).stream()
        .filter(set::contains)
        .map(BitSet2::copyOf)
        .collect(Collectors.toSet());

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
    universe.forEach(valuation -> {
      assertTrue(valuation.cardinality() <= 4);
      assertTrue(seen.add(valuation));
    });

    containsA.forEach(valuation -> assertTrue(valuation.get(0)));

    abcd.forEach(valuation -> {
      assertTrue(valuation.get(0));
      assertTrue(valuation.get(1));
      assertTrue(valuation.get(2));
      assertTrue(valuation.get(3));
    });

    empty.forEach(valuation -> fail("empty should be empty, but it contains " + valuation));
  }


  @Test
  void testCreateEmptyValuationSet() {
    var factory = factory(ATOMIC_PROPOSITIONS);
    assertThat(factory.empty(), ValuationSet::isEmpty);
  }

  @Test
  void testCreateUniverseValuationSet() {
    var factory = factory(ATOMIC_PROPOSITIONS);
    var empty = factory.empty();

    for (BitSet element : BitSets.powerSet(ATOMIC_PROPOSITIONS.size())) {
      assertTrue(factory.universe().contains(element));
      empty = factory.union(empty, factory.of(element));
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
    ValuationSet valuationSetBefore = factory.of(valuation1, valuation2);

    BitSet valuation3 = bitSetOf(true, true, false, false, false, true);
    BitSet valuation4 = bitSetOf(true, false, true, false, false, true);
    ValuationSet valuationSetAfter = factory.of(valuation3, valuation4);

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

    assertEquals(factory.empty(), factory.empty().relabel(mapping));
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

    var tree = ValuationTree.of(Set.of(true));
    var filter = factory.of(0);

    assertEquals(
      ValuationTree.of(0, ValuationTree.of(Set.of(true)), ValuationTree.of()),
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