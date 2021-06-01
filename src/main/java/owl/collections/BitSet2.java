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

import java.util.AbstractSet;
import java.util.BitSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class BitSet2 {

  private BitSet2() {}

  public static BitSet of() {
    return new BitSet();
  }

  public static BitSet of(int e0) {
    BitSet bitSet = new BitSet();
    bitSet.set(e0);
    return bitSet;
  }

  public static BitSet of(int e0, int e1) {
    if (e0 == e1) {
      throw new IllegalArgumentException("duplicate element: " + e0);
    }

    BitSet bitSet = new BitSet();
    bitSet.set(e0);
    bitSet.set(e1);
    return bitSet;
  }

  public static BitSet of(int... elements) {
    BitSet bitSet = new BitSet(elements.length);

    for (int element : elements) {
      if (bitSet.get(element)) {
        throw new IllegalArgumentException("duplicate element: " + element);
      }

      bitSet.set(element);
    }

    return bitSet;
  }

  public static BitSet copyOf(BitSet bitSet) {
    if (bitSet.getClass() == BitSet.class) {
      return (BitSet) bitSet.clone();
    }

    // Manual copy, because the passed bitSet was subclassed and might do wonky stuff in clone().
    BitSet copy = new BitSet();
    copy.or(bitSet);
    return copy;
  }

  public static BitSet copyOf(ImmutableBitSet set) {
    return set.copyInto(new BitSet());
  }

  public static BitSet copyOf(Collection<Integer> set) {
    BitSet bitSet = new BitSet(set.size());
    set.forEach(bitSet::set);
    return bitSet;
  }

  /**
   * Converts a set into a bitset.
   *
   * @param set set to be converted
   * @param mapping mapping from elements to indices
   * @return corresponding BitSet
   */
  public static <S> BitSet copyOf(Collection<? extends S> set, ToIntFunction<? super S> mapping) {
    BitSet bitSet = new BitSet(set.size());

    for (S s : set) {
      bitSet.set(mapping.applyAsInt(s));
    }

    return bitSet;
  }

  /**
   * Creates a view of the {@param bitSet} as a {@link Set}. All changes of the underlying Bitset
   * are reflected in the view and vice-versa.
   *
   * <p>This method acts as bridge between bitset-based and collection-based APIs, in combination
   * with {@link BitSet2#copyOf(Collection)}, similar to
   * {@link java.util.Arrays#asList(Object[])}
   *
   * @param bitSet the bitset for which the view is created.
   * @return a view.
   */
  // TODO: add asUnmodifiableSet() to give an unmodifiable view of the BitSet.
  public static SortedSet<Integer> asSet(BitSet bitSet) {
    return new SetView(bitSet);
  }

  /**
   * Converts a BitSet into a set.
   * @param bs bitset to be decoded
   * @param stateMap mapping from bits to elements
   * @return resulting set
   */
  public static <S> Set<? extends S> asSet(BitSet bs, IntFunction<? extends S> stateMap) {
    return bs.stream().mapToObj(stateMap).collect(Collectors.toSet());
  }

  /**
   * Converts a BitSet into an Int.
   * @param bs bitset to be encoded (should be small enough to fit into int)
   */
  public static int toInt(BitSet bs) {
    if (bs.isEmpty()) {
      return 0;
    }

    long[] bits = bs.toLongArray();

    if (bits.length != 1) {
      throw new IllegalArgumentException();
    }

    return StrictMath.toIntExact(bits[0]);
  }

  /**
   * Converts an int into a BitSet.
   * @param i int to be decoded into bitset
   */
  public static BitSet fromInt(int i) {
    return BitSet.valueOf(new long[] {i});
  }

  public static BitSet union(BitSet a, BitSet b) {
    BitSet ret = (BitSet) a.clone();
    ret.or(b);
    return ret;
  }

  public static BitSet intersection(BitSet a, BitSet b) {
    BitSet ret = (BitSet) a.clone();
    ret.and(b);
    return ret;
  }

  public static BitSet without(BitSet a, BitSet b) {
    BitSet ret = (BitSet) a.clone();
    ret.andNot(b);
    return ret;
  }

  private static class SetView extends AbstractSet<Integer> implements SortedSet<Integer> {

    private final BitSet bitSet;

    private SetView(BitSet bitSet) {
      this.bitSet = bitSet;
    }

    @Override
    public Comparator<? super Integer> comparator() {
      return Integer::compareTo;
    }

    @Override
    public SortedSet<Integer> subSet(Integer fromElement, Integer toElement) {
      throw new UnsupportedOperationException();
    }

    @Override
    public SortedSet<Integer> headSet(Integer toElement) {
      throw new UnsupportedOperationException();
    }

    @Override
    public SortedSet<Integer> tailSet(Integer fromElement) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Integer first() {
      int value = bitSet.nextSetBit(0);

      if (value < 0) {
        throw new NoSuchElementException();
      }

      return value;
    }

    @Override
    public Integer last() {
      int value = bitSet.previousSetBit(bitSet.length() - 1);

      if (value < 0) {
        throw new NoSuchElementException();
      }

      return value;
    }

    @Override
    public boolean contains(Object o) {
      if (o instanceof Integer) {
        int element = (Integer) o;
        return bitSet.get(element);
      }

      return false;
    }

    @Override
    public boolean add(Integer i) {
      boolean oldValue = bitSet.get(i);
      bitSet.set(i);
      return !oldValue;
    }

    @Override
    public boolean remove(Object o) {
      if (!(o instanceof Integer)) {
        return false;
      }

      int i = (Integer) o;
      boolean oldValue = bitSet.get(i);
      bitSet.clear(i);
      return oldValue;
    }

    @Override
    public void clear() {
      bitSet.clear();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
      Objects.requireNonNull(c);
      boolean modified = false;

      if (size() > c.size()) {
        for (Object e : c) {
          modified |= remove(e);
        }
      } else {
        for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i + 1)) {
          if (c.contains(i)) {
            bitSet.clear(i);
            modified = true;
          }

          if (i == Integer.MAX_VALUE) {
            break; // or (i+1) would overflow
          }
        }
      }

      return modified;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
      Objects.requireNonNull(c);
      boolean modified = false;

      for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i + 1)) {
        if (!c.contains(i)) {
          bitSet.clear(i);
          modified = true;
        }

        if (i == Integer.MAX_VALUE) {
          break; // or (i+1) would overflow
        }
      }

      return modified;
    }

    @Override
    public Iterator<Integer> iterator() {
      return bitSet.stream().iterator();
    }

    @Override
    public Stream<Integer> stream() {
      return bitSet.stream().boxed();
    }

    @Override
    public boolean isEmpty() {
      return bitSet.isEmpty();
    }

    @Override
    public int size() {
      return bitSet.cardinality();
    }
  }

  /**
   * Returns an iterator over all BitSets with length at most i.
   *
   * @param i the maximal length.
   * @return each next()-call constructs a fresh BitSet.
   */
  public static Iterable<BitSet> powerSet(int i) {
    if (i < PowerBitSetSimpleIterator.MAX_LENGTH) {
      return () -> new PowerBitSetSimpleIterator(i);
    }

    return () -> new PowerBitSetIterator(ImmutableBitSet.range(0, i));
  }

  public static Iterable<BitSet> powerSet(BitSet basis) {
    int length = basis.length();

    // Check if the basis is continuous and does not exceed MAX_LENGTH.
    if (length == basis.cardinality()
      && length < PowerBitSetSimpleIterator.MAX_LENGTH) {

      return () -> new PowerBitSetSimpleIterator(length);
    }

    var basisCopy = ImmutableBitSet.copyOf(basis);
    return () -> new PowerBitSetIterator(basisCopy);
  }

  private static final class PowerBitSetSimpleIterator implements Iterator<BitSet> {

    // MAX_LENGTH < Long.SIZE - 1
    private static final int MAX_LENGTH = 60;

    private final long maxValue;
    private long value;

    private PowerBitSetSimpleIterator(int size) {
      Objects.checkIndex(size, MAX_LENGTH);
      maxValue = 1L << ((long) size);
      value = 0;
    }

    @Override
    public boolean hasNext() {
      return value < maxValue;
    }

    @Override
    public BitSet next() {
      if (!hasNext()) {
        throw new NoSuchElementException("No next element");
      }

      BitSet bitSet = BitSet.valueOf(new long[]{value});
      value++;
      return bitSet;
    }
  }

  private static final class PowerBitSetIterator implements Iterator<BitSet> {
    private final ImmutableBitSet baseSet;
    private final BitSet iteration;
    private final int baseCardinality;
    private int numSetBits = -1;

    private PowerBitSetIterator(ImmutableBitSet baseSet) {
      this.baseSet = baseSet;
      this.baseCardinality = baseSet.size();
      this.iteration = new BitSet(32);
    }

    @Override
    public boolean hasNext() {
      return numSetBits < baseCardinality;
    }

    @Override
    public BitSet next() {
      if (numSetBits == -1) {
        numSetBits = 0;
        return copyOf(iteration);
      }

      if (numSetBits == baseCardinality) {
        throw new NoSuchElementException("No next element");
      }

      var iterator = baseSet.intIterator();
      while (iterator.hasNext()) {
        int index = iterator.nextInt();
        if (iteration.get(index)) {
          iteration.clear(index);
          numSetBits -= 1;
        } else {
          iteration.set(index);
          numSetBits += 1;
          break;
        }
      }

      return copyOf(iteration);
    }
  }
}
