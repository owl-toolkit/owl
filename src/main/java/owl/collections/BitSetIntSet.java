package owl.collections;

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

class BitSetIntSet extends AbstractIntSortedSet implements BitIntSet {
  private final BitSet bitSet;
  private final int max;
  private final int min;

  private BitSetIntSet(BitSet bitSet, int min, int max) {
    assert 0 <= min && min <= max;
    this.min = min;
    this.max = max;
    this.bitSet = bitSet;
  }

  BitSetIntSet(BitSet bitSet) {
    this(bitSet, 0, Integer.MAX_VALUE);
  }

  private static boolean inRange(int element, int from, int to) {
    return from <= element && element < to;
  }

  private static boolean isSubRange(int subFrom, int subTo, int from, int to) {
    return from <= subFrom && subTo < to;
  }

  private static boolean validRange(int from, int to) {
    return from <= to;
  }

  @Override
  public boolean add(int k) {
    checkInRange(k);
    boolean present = bitSet.get(k);
    if (present) {
      return false;
    }
    bitSet.set(k);
    return true;
  }

  @Override
  public boolean addAll(IntCollection c) {
    if (c instanceof BitSetIntSet) {
      BitSetIntSet other = (BitSetIntSet) c;
      BitSet otherBitSet = other.bitSet;
      if (isContainedInView(otherBitSet)) {
        int cardinality = this.bitSet.cardinality();
        this.bitSet.or(otherBitSet);
        return this.bitSet.cardinality() != cardinality;
      }
    }
    return super.addAll(c);
  }

  private void checkInRange(int element) {
    if (!(inRange(element))) {
      throw new IllegalArgumentException(String.format("Specified value %d out of bounds [%d, %d)",
        element, min, max));
    }
  }

  private void checkInRange(int from, int to) {
    if (!validRange(from, to)) {
      throw new IllegalArgumentException(String.format("Invalid range %d, %d", from, to));
    }
    if (!(isSubRange(from, to, min, max))) {
      throw new IllegalArgumentException(String.format("Specified range [%d, %d) out of bounds "
        + "[%d, %d)", from, to, min, max));
    }
  }

  @Override
  public void clear(int i) {
    checkInRange(i);
    bitSet.clear(i);
  }

  @Override
  public void clear(int from, int to) {
    checkInRange(from, to);
    assert 0 <= from && from <= to;
    bitSet.clear(from, to);
  }

  @Override
  public void clear() {
    if (isFullSet()) {
      bitSet.clear();
    } else {
      bitSet.clear(min, max);
    }
  }

  @Override
  @SuppressWarnings({"UseOfClone", "MethodDoesntCallSuperMethod"})
  public BitSetIntSet clone() throws CloneNotSupportedException {
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
    checkInRange(k);
    return bitSet.get(k);
  }

  @Override
  public boolean containsAll(IntCollection c) {
    if (c instanceof BitSetIntSet) {
      BitSetIntSet other = (BitSetIntSet) c;
      BitSet otherBitSet = other.bitSet;
      int otherCardinality = otherBitSet.cardinality();
      if (isContainedInView(otherBitSet)) {
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
        return true;
      } else if ((max - min) <= otherCardinality) {
        // The otherBitSet contains more elements than this view can possibly hold
        return false;
      }
    }
    return super.containsAll(c);
  }

  @Override
  public boolean containsAny(IntCollection o) {
    if (o instanceof BitSetIntSet) {
      BitSetIntSet other = (BitSetIntSet) o;
      BitSet otherBitSet = other.bitSet;
      if (isContainedInView(otherBitSet)) {
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

    if (o instanceof BitSetIntSet) {
      BitSetIntSet other = (BitSetIntSet) o;
      BitSet otherBitSet = other.bitSet;

      int otherBitSetMin = otherBitSet.nextSetBit(0);
      int otherBitSetMax = otherBitSet.length();
      int bitSetMin = bitSet.nextSetBit(0);
      int bitSetMax = bitSet.length();

      if (Math.max(min, other.min) <= Math.min(bitSetMin, otherBitSetMin) &&
        Math.max(bitSetMax, otherBitSetMax) < Math.min(max, other.max)) {
        // We can compare the underlying sets as both views contain both bit sets
        assert isContainedInView(bitSet) && isContainedInView(otherBitSet)
          && other.isContainedInView(bitSet) && other.isContainedInView(otherBitSet);
        return Objects.equals(this.bitSet, otherBitSet);
      }
      // Can do more optimizations here but we don't call equals in critical code anyway
    }
    return super.equals(o);
  }

  @Override
  @Nonnegative
  public int firstInt() {
    return bitSet.nextSetBit(min);
  }

  @Override
  public int hashCode() {
    return bitSet.hashCode() + 31 * min + 7 * max;
  }

  @Override
  public BitIntSet headSet(int toElement) {
    return subSet(min, toElement);
  }

  private boolean inRange(int i) {
    return inRange(i, min, max);
  }

  /**
   * Determines whether the given {@code bitSet} is a subset of the view specified by the
   * {@code min} and {@code max} fields. This is necessary to perform most optimized operations.
   */
  private boolean isContainedInView(BitSet bitSet) {
    return isFullSet() || min <= bitSet.nextSetBit(0) && bitSet.length() <= max;
  }

  @Override
  public boolean isEmpty() {
    return bitSet.isEmpty();
  }

  private boolean isFullSet() {
    return min == 0 && max == Integer.MAX_VALUE;
  }

  @Override
  public IntBidirectionalIterator iterator(int fromElement) {
    return new BitSetIterator(this, fromElement);
  }

  @Override
  public IntBidirectionalIterator iterator() {
    return new BitSetIterator(this);
  }

  @Override
  @Nonnegative
  public int lastInt() {
    return max == Integer.MAX_VALUE ? bitSet.length() - 1 : bitSet.previousSetBit(max);
  }

  @Override
  public boolean rem(int k) {
    checkInRange(k);
    // This is "remove the value k"
    boolean present = bitSet.get(k);
    if (!present) {
      return false;
    }
    bitSet.clear(k);
    return false;
  }

  @Override
  public boolean removeAll(IntCollection c) {
    if (c instanceof BitSetIntSet) {
      BitSetIntSet other = (BitSetIntSet) c;
      BitSet otherBitSet = other.bitSet;
      if (isContainedInView(otherBitSet)) {
        int cardinality = this.bitSet.cardinality();
        this.bitSet.andNot(otherBitSet);
        return this.bitSet.cardinality() != cardinality;
      }
    }
    return super.removeAll(c);
  }

  @Override
  public boolean retainAll(IntCollection c) {
    if (c instanceof BitSetIntSet) {
      BitSetIntSet other = (BitSetIntSet) c;
      BitSet otherBitSet = other.bitSet;
      if (isContainedInView(otherBitSet)) {
        int cardinality = bitSet.cardinality();
        bitSet.and(otherBitSet);
        return bitSet.cardinality() != cardinality;
      }
    }
    return super.retainAll(c);
  }

  @Override
  public void set(int from, int to) {
    checkInRange(from, to);
    bitSet.set(from, to);
  }

  @Override
  public void set(int i) {
    checkInRange(i);
    bitSet.set(i);
  }

  @Override
  public int size() {
    if (isContainedInView(bitSet)) {
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
  public BitIntSet subSet(int fromElement, int toElement) {
    checkInRange(fromElement, toElement);
    if (min == fromElement && max == toElement) {
      return this;
    }
    return new BitSetIntSet(bitSet, fromElement, toElement);
  }

  @Override
  public BitIntSet tailSet(int fromElement) {
    return subSet(fromElement, max);
  }

  @Override
  public String toString() {
    if (isContainedInView(bitSet)) {
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

  @SuppressWarnings("AssignmentToCollectionOrArrayFieldFromParameter")
  private static final class BitSetIterator extends AbstractIntBidirectionalIterator
    implements PrimitiveIterator.OfInt {
    private final BitSetIntSet set;
    private int next;
    private int previous;

    BitSetIterator(BitSetIntSet set) {
      this.set = set;
      previous = -1;
      next = set.bitSet.nextSetBit(set.min);
      if (next >= set.max) {
        next = -1;
      }
    }

    BitSetIterator(BitSetIntSet set, int startingPoint) {
      assert set.inRange(startingPoint);
      this.set = set;
      this.previous = getPrevious(startingPoint);
      this.next = getNext(startingPoint);
      assert this.previous < this.next || this.next == -1 || this.previous == -1;
    }

    @Override
    public void forEachRemaining(IntConsumer action) {
      while (next != -1) {
        action.accept(next);
        previous = next;
        next = getNext(next);
      }
    }

    private int getNext(int current) {
      if (current < set.max) {
        int next = set.bitSet.nextSetBit(current + 1);
        if (next >= set.max) {
          return -1;
        }
        return next;
      }
      return -1;
    }

    private int getPrevious(int current) {
      if (current >= set.min) {
        int previous = set.bitSet.previousSetBit(current);
        if (previous < set.min) {
          return -1;
        }
        return previous;
      }
      return -1;
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
      if (next == -1) {
        throw new NoSuchElementException();
      }
      previous = next;
      next = getNext(next);
      return previous;
    }

    @Override
    public int previousInt() {
      if (previous == -1) {
        throw new NoSuchElementException();
      }
      next = previous;
      previous = getPrevious(previous);
      return next;

    }
  }
}
