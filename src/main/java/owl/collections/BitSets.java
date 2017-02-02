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

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.AbstractSet;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class BitSets {

  private BitSets() {

  }

  public static Set<BitSet> powerSet(int i) {
    BitSet bs = new BitSet(i);
    bs.flip(0, i);
    return powerSet(bs);
  }

  public static Set<BitSet> powerSet(BitSet bs) {
    return new PowerBitSet(bs);
  }

  /**
   * Checks if A is a subset of B.
   */
  public static boolean subset(BitSet A, BitSet B) {
    BitSet C = (BitSet) A.clone();
    C.andNot(B);
    return C.isEmpty();
  }

  @Nullable
  public static List<Integer> toList(IntStream bs) {
    if (bs == null) {
      return null;
    }

    IntList list = new IntArrayList();
    bs.forEach(list::add);

    if (list.isEmpty()) {
      return null;
    }

    return list;
  }

  private static final class PowerBitSet extends AbstractSet<BitSet> {
    final BitSet baseSet;

    PowerBitSet(BitSet input) {
      baseSet = (BitSet) input.clone();
    }

    @Override
    public boolean contains(@Nullable Object obj) {
      if (obj instanceof BitSet) {
        BitSet set = (BitSet) ((BitSet) obj).clone();
        set.andNot(baseSet);
        return set.isEmpty();
      }

      return false;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof PowerBitSet) {
        PowerBitSet that = (PowerBitSet) obj;
        return baseSet.equals(that.baseSet);
      }

      return super.equals(obj);
    }

    @Override
    public int hashCode() {
      return baseSet.hashCode() << (baseSet.cardinality() - 1);
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Nonnull
    @Override
    public Iterator<BitSet> iterator() {
      return new PowerBitSetIterator();
    }

    @Override
    public int size() {
      return 1 << baseSet.cardinality();
    }

    @Override
    public String toString() {
      return "powerSet(" + baseSet + ")";
    }

    // TODO: Performance: Zero Copy
    private class PowerBitSetIterator implements Iterator<BitSet> {

      BitSet next = new BitSet();

      @Override
      public boolean hasNext() {
        return (next != null);
      }

      @Override
      public BitSet next() {
        BitSet n = (BitSet) next.clone();

        for (int i = baseSet.nextSetBit(0); i >= 0; i = baseSet.nextSetBit(i + 1)) {
          if (!next.get(i)) {
            next.set(i);
            break;
          } else {
            next.clear(i);
          }
        }

        if (next.isEmpty()) {
          next = null;
        }

        return n;
      }
    }
  }
}
