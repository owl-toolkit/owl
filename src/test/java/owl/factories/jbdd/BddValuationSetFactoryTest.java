/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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

package owl.factories.jbdd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static owl.util.Assertions.assertThat;

import de.tum.in.jbdd.BddFactory;
import java.util.BitSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import owl.collections.ValuationSet;

class BddValuationSetFactoryTest {
  private static final List<String> ALPHABET = List.of("a", "b", "c", "d", "e");
  private ValuationFactory factory;

  @BeforeEach
  void beforeEach() {
    factory = new ValuationFactory(BddFactory.buildBdd(1024), ALPHABET);
  }

  @Test
  void testCreateEmptyValuationSet() {
    assertThat(factory.empty(), ValuationSet::isEmpty);
  }

  @Test
  void testCreateUniverseValuationSet() {
    ValuationSet universe = factory.universe();
    ValuationSet empty = factory.empty();
    BitSet alphabet = new BitSet(ALPHABET.size());
    alphabet.set(0, ALPHABET.size());
    for (BitSet element : de.tum.in.naturals.bitset.BitSets.powerSet(alphabet)) {
      assertThat(universe, x -> x.contains(element));
      empty = factory.union(empty, factory.of(element));
    }

    assertEquals(universe, empty);
  }

  @Test
  void testGetAlphabet() {
    assertEquals(ALPHABET.size(), factory.alphabetSize());
  }
}

