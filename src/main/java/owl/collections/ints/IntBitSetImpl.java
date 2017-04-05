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

package owl.collections.ints;

import it.unimi.dsi.fastutil.ints.AbstractIntBidirectionalIterator;
import it.unimi.dsi.fastutil.ints.AbstractIntSortedSet;
import it.unimi.dsi.fastutil.ints.IntBidirectionalIterator;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntComparator;
import it.unimi.dsi.fastutil.ints.IntIterator;
import java.util.BitSet;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.PrimitiveIterator;
import java.util.function.IntConsumer;
import javax.annotation.Nonnegative;
import javax.annotation.Nullable;

class IntBitSetImpl extends AbstractIntSortedSet implements IntBitSet {
  // TODO Some methods could be rewritten to use the approach of BitSet#toString() -
  // The idea there is to not blindly iterate via nextSetBit but determine "blocks" by alternating
  // nextSetBit and nextClearBit. If the sets are rather tightly packed, this can save a lot of
  // calls!

  private final BitSet bitSet;
  private final int max;
  private final int min;

  private IntBitSetImpl(BitSet bitSet, int min, int max) {
    assert min <= max;
    this.min = Math.max(0, min);
    this.max = max;
    this.bitSet = bitSet;
  }

  IntBitSetImpl(BitSet bitSet) {
    this(bitSet, Integer.MIN_VALUE, Integer.MAX_VALUE);
  }

  private static boolean inRange(int element, int from, int to) {
    return from <= element && element < to;
  }

  private static boolean isSubRange(int subFrom, int subTo, int from, int to) {
    return from <= subFrom && subTo <= to;
  }

  private static boolean isValidRange(int from, int to) {
    return from <= to;
  }

  @Override
  public boolean add(int k) {
    checkValue(k);
    if (bitSet.get(k)) {
      return false;
    }
    bitSet.set(k);
    return true;
  }

  @Override
  public boolean addAll(IntCollection c) {
    if (c instanceof IntBitSetImpl) {
      IntBitSetImpl other = (IntBitSetImpl) c;
      BitSet otherBitSet = other.bitSet;
      if (isSetInView(otherBitSet) && (other.isOwnSetInView())) {
        int cardinality = this.bitSet.cardinality();
        this.bitSet.or(otherBitSet);
        return this.bitSet.cardinality() != cardinality;
      }
    }
    return super.addAll(c);
  }

  private void checkRange(int from, int to) {
    if (!isValidRange(from, to)) {
      throw new IllegalArgumentException(String.format("Invalid range %d, %d", from, to));
    }
    if (!(isSubRange(from, to, min, max))) {
      throw new IllegalArgumentException(String.format("Specified range [%d, %d) out of bounds "
        + "[%d, %d)", from, to, min, max));
    }
  }

  private void checkValue(int value) {
    if (!(isValidValue(value))) {
      throw new IllegalArgumentException(String.format("Specified value %d out of bounds [%d, %d)",
        value, min, max));
    }
  }

  @Override
  public void clear(int i) {
    if (!isValidValue(i)) {
      return;
    }
    bitSet.clear(i);
  }

  @Override
  public void clear(int from, int to) {
    assert from <= to;
    if (to < 0) {
      return;
    }
    int clampedFrom = Math.max(0, from);
    checkRange(clampedFrom, to);
    bitSet.clear(clampedFrom, to);
  }

  @Override
  public void clear() {
    if (isOwnSetInView()) {
      bitSet.clear();
    } else {
      bitSet.clear(min, max);
    }
  }

  @Override
  @SuppressWarnings({"UseOfClone", "MethodDoesntCallSuperMethod"})
  public IntBitSetImpl clone() throws CloneNotSupportedException {
    throw new CloneNotSupportedException("");
  }

  @Override
  @Nullable
  public IntComparator comparator() {
    // We can only use natural ordering - null signals this.
    return null;
  }

  @Override
  public boolean contains(int k) {
    if (!isValidValue(k)) {
      return false;
    }
    return bitSet.get(k);
  }

  @Override
  public boolean containsAll(IntCollection c) {
    if (c instanceof IntBitSetImpl) {
      IntBitSetImpl other = (IntBitSetImpl) c;
      BitSet otherBitSet = other.bitSet;
      int otherCardinality = otherBitSet.cardinality();
      if (isSetInView(otherBitSet) && (other.isOwnSetInView())) {
        if (otherCardinality > bitSet.cardinality()) {
          // The other view definitely contains more elements
          return false;
        }
        // The otherBitSet is contained in our view - no need to check with min / max
        for (int i = otherBitSet.nextSetBit(0); i >= 0; i = otherBitSet.nextSetBit(i + 1)) {
          assert inRange(i, min, max);
          if (!bitSet.get(i)) {
            return false;
          }
        }
        // Alternative: Check otherBitSet.clone().andNot(bitSet).isEmpty(); - Remains to be tested!
        return true;
      } else if (other.isFullSet() && (max - min) < otherCardinality) {
        // The otherBitSet contains more elements than this view can possibly hold
        return false;
      }
    }
    return super.containsAll(c);
  }

  @Override
  public boolean containsAny(IntCollection o) {
    if (o instanceof IntBitSetImpl) {
      IntBitSetImpl other = (IntBitSetImpl) o;
      BitSet otherBitSet = other.bitSet;
      if (isSetInView(otherBitSet) && other.isOwnSetInView()) {
        return bitSet.intersects(otherBitSet);
      }
    }
    IntIterator iterator = o.iterator();
    while (iterator.hasNext()) {
      if (bitSet.get(iterator.nextInt())) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean equals(Object o) {
    // Note: Set contract demands that we compare for equal elements.

    if (this == o) {
      return true;
    }

    if (o instanceof IntBitSetImpl) {
      IntBitSetImpl other = (IntBitSetImpl) o;

      if (isOwnSetInView() && other.isOwnSetInView()) {
        return Objects.equals(this.bitSet, other.bitSet);
      }
    }
    return super.equals(o);
  }

  @Override
  @Nonnegative
  public int firstInt() {
    int first = bitSet.nextSetBit(min);

    if (first == -1) {
      throw new NoSuchElementException("Set is empty");
    }

    return first;
  }

  @Override
  public void forEach(IntConsumer consumer) {
    for (int i = bitSet.nextSetBit(min); i < max && i >= 0; i = bitSet.nextSetBit(i + 1)) {
      consumer.accept(i);
    }
  }

  @Override
  public int hashCode() {
    return bitSet.hashCode() + 31 * min + 7 * max;
  }

  @Override
  public IntBitSetImpl headSet(int toElement) {
    return subSet(min, toElement);
  }

  @Override
  public boolean isEmpty() {
    if (isOwnSetInView()) {
      return bitSet.isEmpty();
    }
    int firstBit = bitSet.nextSetBit(min);
    return firstBit == -1 || firstBit >= max;
  }

  /**
   * Returns true if {@code min == 0} and {@code max == Integer.MAX_VALUE}. This implies that the
   * elements contained in this set object are always exactly those contained in the bit set. This
   * allows to perform some operations directly on the underlying bit set.
   */
  private boolean isFullSet() {
    return min == 0 && max == Integer.MAX_VALUE;
  }

  /**
   * Determines whether the own {@code bitSet} is a subset of the view specified by the {@code min}
   * and {@code max} fields.
   *
   * @see #isSetInView(BitSet)
   */
  private boolean isOwnSetInView() {
    return isSetInView(bitSet);
  }

  /**
   * Determines whether the given {@code bitSet} is a subset of the view specified by the
   * {@code min} and {@code max} fields, i.e. there is no element in {@code bitSet} which can't be
   * contained in this set. This allows to perform some operations directly on the bit set.
   */
  private boolean isSetInView(BitSet bitSet) {
    return isFullSet() || min <= bitSet.nextSetBit(0) && bitSet.length() < max;
  }

  private boolean isValidValue(int i) {
    return inRange(i, min, max);
  }

  @Override
  public IntBidirectionalIterator iterator(int fromElement) {
    return new BitSetIterator(fromElement);
  }

  @Override
  public IntBidirectionalIterator2 iterator() {
    return new BitSetIterator();
  }

  @Override
  @Nonnegative
  public int lastInt() {
    int last;
    if (max == Integer.MAX_VALUE) {
      last = bitSet.length() - 1;
    } else {
      last = bitSet.previousSetBit(max - 1);
    }
    if (last == -1) {
      throw new NoSuchElementException("Set is empty");
    }
    return last;
  }

  @Override
  public boolean rem(int k) {
    if (!isValidValue(k)) {
      return false;
    }
    if (!bitSet.get(k)) {
      return false;
    }
    bitSet.clear(k);
    return true;
  }

  @Override
  public boolean removeAll(IntCollection c) {
    if (c instanceof IntBitSetImpl) {
      IntBitSetImpl other = (IntBitSetImpl) c;
      if (isOwnSetInView() && other.isOwnSetInView()) {
        int cardinality = this.bitSet.cardinality();
        this.bitSet.andNot(other.bitSet);
        return this.bitSet.cardinality() != cardinality;
      }
    }
    return super.removeAll(c);
  }

  @Override
  public boolean retainAll(IntCollection c) {
    if (c instanceof IntBitSetImpl) {
      IntBitSetImpl other = (IntBitSetImpl) c;
      if (isOwnSetInView() && other.isOwnSetInView()) {
        int cardinality = bitSet.cardinality();
        bitSet.and(other.bitSet);
        return bitSet.cardinality() != cardinality;
      }
    }
    return super.retainAll(c);
  }

  @Override
  public void set(int from, int to) {
    assert from <= to;
    checkRange(from, to);
    bitSet.set(from, to);
  }

  @Override
  public void set(int i) {
    checkValue(i);
    bitSet.set(i);
  }

  @Override
  public int size() {
    if (isOwnSetInView()) {
      return bitSet.cardinality();
    }
    // We have to resort to dumb counting.
    int count = 0;
    for (int i = bitSet.nextSetBit(min); i < max && i >= 0; i = bitSet.nextSetBit(i + 1)) {
      count += 1;
    }
    return count;
  }

  @Override
  public IntBitSetImpl subSet(int fromElement, int toElement) {
    assert fromElement <= toElement;
    if (min == fromElement && max == toElement) {
      return this;
    }
    checkRange(Math.max(0, fromElement), toElement);
    return new IntBitSetImpl(bitSet, fromElement, toElement);
  }

  @Override
  public IntBitSetImpl tailSet(int fromElement) {
    return subSet(fromElement, max);
  }

  @Override
  public String toString() {
    if (isOwnSetInView()) {
      return bitSet.toString();
    }

    // This is sub-optimal, see BitSet#toString() for a smarter implementation.
    StringBuilder b = new StringBuilder(max - min);
    b.append('{');

    int i = bitSet.nextSetBit(min);
    if (i < max && i >= 0) {
      b.append(i);
      i = bitSet.nextSetBit(i + 1);
      while (i < max && i >= 0) {
        b.append(", ").append(i);
        i = bitSet.nextSetBit(i + 1);
      }
    }
    b.append('}');
    return b.toString();

  }

  private final class BitSetIterator extends AbstractIntBidirectionalIterator
    implements PrimitiveIterator.OfInt, IntBidirectionalIterator2 {
    private int last = -1;
    private int next;
    private int previous;

    BitSetIterator() {
      previous = -1;
      next = bitSet.nextSetBit(min);
      if (next >= max) {
        next = -1;
      }
    }

    BitSetIterator(int startingPoint) {
      this.previous = getPrevious(startingPoint);
      this.next = getNext(startingPoint);
      assert this.previous < this.next || this.next == -1 || this.previous == -1;
    }

    private int getNext(int current) {
      if (current >= max) {
        return -1;
      }

      int next;

      if (current < min) {
        next = bitSet.nextSetBit(min);
      } else {
        next = bitSet.nextSetBit(current + 1);
      }

      return next < max ? next : -1;
    }

    private int getPrevious(int current) {
      if (current < min) {
        return -1;
      }

      int previous;

      if (current >= max) {
        previous = bitSet.previousSetBit(max - 1);
      } else {
        previous = bitSet.previousSetBit(current - 1);
      }

      return previous < min ? -1 : previous;
    }

    @Override
    public boolean hasNext() {
      return next != -1;
    }

    @Override
    public boolean hasPrevious() {
      return previous != -1;
    }

    @Override
    public int nextInt() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }

      previous = next;
      next = getNext(next);
      last = previous;
      assert last != -1 && contains(last);
      return previous;
    }

    @Override
    public void forEachRemaining(IntConsumer action) {
      while (hasNext()) {
        action.accept(nextInt());
      }
    }

    @Override
    public int previousInt() {
      if (!hasPrevious()) {
        throw new NoSuchElementException();
      }

      next = previous;
      previous = getPrevious(previous);
      last = next;
      assert last != -1 && contains(last);
      return next;
    }

    @Override
    public void remove() {
      if (last == -1) {
        throw new IllegalStateException("");
      }

      if (!rem(last)) {
        throw new IllegalStateException("Concurrent modification");
      }

      last = -1;
    }
  }
}
