package owl.automaton.edge;

import java.util.BitSet;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.PrimitiveIterator;
import java.util.PrimitiveIterator.OfInt;
import javax.annotation.Nonnegative;
import javax.annotation.concurrent.Immutable;
import owl.util.BitUtil;

@Immutable
final class EdgeLong<S> implements Edge<S> {
  private final long store;
  private final S successor;

  private EdgeLong(S successor, long store) {
    this.store = store;
    this.successor = successor;
  }

  EdgeLong(S successor, BitSet bitSet) {
    assert bitSet.length() <= Long.SIZE && bitSet.cardinality() > 1;
    long store = 0L;
    for (int i = 0; i < Long.SIZE; i++) {
      if (bitSet.get(i)) {
        store |= 1L << i;
      }
    }
    this.store = store;
    this.successor = successor;
  }

  @Override
  public PrimitiveIterator.OfInt acceptanceSetIterator() {
    return new LongBitIterator(store);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof EdgeLong)) {
      return false;
    }

    EdgeLong<?> other = (EdgeLong<?>) o;
    return this.store == other.store
      && Objects.equals(this.successor, other.successor);
  }

  @Override
  public S getSuccessor() {
    return successor;
  }

  @Override
  public boolean hasAcceptanceSets() {
    assert store != 0;
    return true;
  }

  @Override
  public int hashCode() {
    // Not using Objects.hash to avoid var-ags array instantiation
    return 31 * (int) (store ^ (store >>> 32)) + successor.hashCode();
  }

  @Override
  public boolean inSet(@Nonnegative int i) {
    assert i >= 0;
    return i < Long.SIZE && BitUtil.isSet(store, i);
  }

  @Override
  public int largestAcceptanceSet() {
    return (Long.SIZE - 1) - Long.numberOfLeadingZeros(store);
  }

  @Override
  public int smallestAcceptanceSet() {
    return Long.numberOfTrailingZeros(store);
  }

  @Override
  public String toString() {
    OfInt acceptanceSetIterator = acceptanceSetIterator();
    StringBuilder builder = new StringBuilder(10);
    builder.append(acceptanceSetIterator.nextInt());
    acceptanceSetIterator.forEachRemaining((int x) -> builder.append(", ").append(x));
    return "-> " + successor + " {" + builder + '}';
  }

  @Override
  public <T> EdgeLong<T> withSuccessor(T successor) {
    assert successor != null;
    return new EdgeLong<>(successor, store);
  }

  private static final class LongBitIterator implements PrimitiveIterator.OfInt {
    private final long store;
    private int next;

    LongBitIterator(long store) {
      this.store = store;
      this.next = BitUtil.nextSetBit(store, 0);
    }

    @Override
    public boolean hasNext() {
      return next != -1;
    }

    @Override
    public int nextInt() {
      if (next == -1) {
        throw new NoSuchElementException();
      }

      int current = next;
      next = BitUtil.nextSetBit(store, next + 1);
      return current;
    }
  }
}
