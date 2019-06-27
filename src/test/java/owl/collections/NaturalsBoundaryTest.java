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

package owl.collections;

import de.tum.in.naturals.bitset.BitSets;
import java.util.BitSet;
import org.junit.jupiter.api.Test;
import owl.util.Assertions;

class NaturalsBoundaryTest {

  @Test
  void isSubset() {
    BitSet set1 = new BitSet();
    BitSet set2 = new BitSet();

    set1.set(1);
    set2.set(1, 3);

    Assertions.assertThat(set1, x -> BitSets.isSubset(x, set2));

    set1.clear();
    set2.clear();

    set1.set(1);
    set2.set(0);

    Assertions.assertThat(set1, x -> !BitSets.isSubset(x, set2));
  }
}