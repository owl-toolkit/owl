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

package owl.factories.jbdd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import de.tum.in.jbdd.BddFactory;
import java.util.BitSet;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import owl.collections.ValuationSet;

public class BddValuationSetFactoryTest {
  private List<String> alphabet;
  private ValuationFactory factory;

  @Before
  public void setUp() {
    alphabet = List.of("a", "b", "c", "d", "e");
    factory = new ValuationFactory(BddFactory.buildBdd(1024), alphabet);
  }

  @Test
  public void testCreateEmptyValuationSet() {
    assertTrue(factory.empty().isEmpty());
  }

  @Test
  public void testCreateUniverseValuationSet() {
    ValuationSet universe = factory.universe();
    ValuationSet empty = factory.empty();
    BitSet alphabet = new BitSet(this.alphabet.size());
    alphabet.set(0, this.alphabet.size());
    for (BitSet element : de.tum.in.naturals.bitset.BitSets.powerSet(alphabet)) {
      assertTrue(universe.contains(element));
      empty = factory.union(empty, factory.of(element));
    }
    assertEquals(empty, universe);
  }

  @Test
  public void testGetAlphabet() {
    assertEquals(alphabet.size(), factory.alphabetSize());
  }
}

