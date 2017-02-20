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
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;
import java.util.Set;
import java.util.function.IntConsumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class BitSets {
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
   * Checks if {@code first} is a subset of {@code second}.
   */
  public static boolean subset(BitSet first, BitSet second) {
    for (int i = first.nextSetBit(0); i >= 0; i = first.nextSetBit(i + 1)) {
      if (!second.get(i)) {
        return false;
      }
    }
    return true;
  }

  public static BitSet collect(PrimitiveIterator.OfInt iterator) {
    BitSet bitSet = new BitSet();
    while (iterator.hasNext()) {
      bitSet.set(iterator.nextInt());
    }
    return bitSet;
  }

  @Nullable
  public static IntList toList(PrimitiveIterator.OfInt bs) {
    IntList list = new IntArrayList();
    bs.forEachRemaining((IntConsumer) list::add);

    if (list.isEmpty()) {
      return null;
    }

    return list;
  }

  private static final class PowerBitSet extends AbstractSet<BitSet> {
    private final BitSet baseSet;

    PowerBitSet(BitSet input) {
      //noinspection UseOfClone
      baseSet = (BitSet) input.clone();
    }

    @Override
    public boolean contains(@Nullable Object obj) {
      return obj instanceof BitSet && BitSets.subset((BitSet) obj, baseSet);
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
      return new PowerBitSetIterator(baseSet);
    }

    @Override
    public int size() {
      return 1 << baseSet.cardinality();
    }

    @Override
    public String toString() {
      return "powerSet(" + baseSet + ")";
    }
  }

  private static final class PowerBitSetIterator implements Iterator<BitSet> {
    private final BitSet baseSet;
    @Nullable
    private BitSet next = new BitSet();

    PowerBitSetIterator(BitSet baseSet) {
      this.baseSet = baseSet;
    }

    @Override
    public boolean hasNext() {
      return (next != null);
    }

    @Override
    public BitSet next() {
      // TODO this can quite easily be replaced by an in-place iterator

      if (next == null) {
        throw new NoSuchElementException("No next element");
      }
      @SuppressWarnings("UseOfClone")
      BitSet current = (BitSet) next.clone();

      for (int i = baseSet.nextSetBit(0); i >= 0; i = baseSet.nextSetBit(i + 1)) {
        if (next.get(i)) {
          next.clear(i);
        } else {
          next.set(i);
          break;
        }
      }

      if (next.isEmpty()) {
        next = null;
      }

      return current;
    }
  }
}