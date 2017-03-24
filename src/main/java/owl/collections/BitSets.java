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
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntIterators;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import java.util.AbstractSet;
import java.util.BitSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.PrimitiveIterator;
import java.util.Set;
import java.util.function.IntConsumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class BitSets {
  private BitSets() {

  }

  public static BitSet collect(IntIterator iterator) {
    BitSet bitSet = new BitSet();
    iterator.forEachRemaining(bitSet::set);
    return bitSet;
  }

  public static BitSet collect(PrimitiveIterator.OfInt iterator) {
    BitSet bitSet = new BitSet();
    iterator.forEachRemaining((IntConsumer) bitSet::set);
    return bitSet;
  }

  public static BitIntSet createBitSet() {
    BitSet backingSet = new BitSet();
    return new BitSetIntSet(backingSet);
  }

  public static BitIntSet createBitSet(int size) {
    BitSet backingSet = new BitSet(size);
    return new BitSetIntSet(backingSet);
  }

  public static void forEach(BitSet bitSet, IntConsumer consumer) {
    for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i + 1)) {
      consumer.accept(i);
    }
  }

  /**
   * Checks if {@code first} is a subset of {@code second}.
   */
  public static boolean isSubset(BitSet first, BitSet second) {
    for (int i = first.nextSetBit(0); i >= 0; i = first.nextSetBit(i + 1)) {
      if (!second.get(i)) {
        return false;
      }
    }
    return true;
  }

  public static Set<BitSet> powerSet(BitSet bs) {
    return new PowerBitSet(bs);
  }

  public static Set<BitSet> powerSet(int i) {
    BitSet bs = new BitSet(i);
    bs.set(0, i);
    return powerSet(bs);
  }

  public static IntList toList(PrimitiveIterator.OfInt bs) {
    if (!bs.hasNext()) {
      return IntLists.EMPTY_LIST;
    }
    IntList list = new IntArrayList();
    bs.forEachRemaining((IntConsumer) list::add);
    return list;
  }

  public static BitSet toSet(boolean... indices) {
    BitSet bitSet = new BitSet(indices.length);
    for (int i = 0; i < indices.length; i++) {
      if (indices[i]) {
        bitSet.set(i);
      }
    }
    return bitSet;
  }

  public static BitSet toSet(int... indices) {
    return collect(IntIterators.wrap(indices));
  }

  private static final class PowerBitSet extends AbstractSet<BitSet> {
    private final BitSet baseSet;

    PowerBitSet(BitSet input) {
      //noinspection UseOfClone
      baseSet = (BitSet) input.clone();
    }

    @Override
    public boolean contains(@Nullable Object obj) {
      return obj instanceof BitSet && BitSets.isSubset((BitSet) obj, baseSet);
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof PowerBitSet) {
        PowerBitSet that = (PowerBitSet) obj;
        return Objects.equals(baseSet, that.baseSet);
      }

      return super.equals(obj);
    }

    @Override
    public int hashCode() {
      return baseSet.hashCode() << (baseSet.cardinality() - 1);
    }

    @SuppressWarnings("MethodReturnAlwaysConstant")
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
      return String.format("powerSet(%s)", baseSet);
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
