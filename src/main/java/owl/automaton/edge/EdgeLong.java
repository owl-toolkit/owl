package owl.automaton.edge;

import java.util.BitSet;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.PrimitiveIterator;
import java.util.PrimitiveIterator.OfInt;
import javax.annotation.Nonnegative;
import javax.annotation.concurrent.Immutable;

@Immutable
final class EdgeLong<S> implements Edge<S> {
  private final long store;
  private final S successor;

  private EdgeLong(S successor, long store) {
    this.store = store;
    this.successor = Objects.requireNonNull(successor);
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
    assert this.store != 0;
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
  public S successor() {
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
    return i < Long.SIZE && ((store >>> i) & 1L) != 0L;
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
    return new EdgeLong<>(successor, store);
  }

  private static final class LongBitIterator implements PrimitiveIterator.OfInt {
    private long store;

    LongBitIterator(long store) {
      this.store = store;
    }

    @Override
    public boolean hasNext() {
      return store != 0;
    }

    @Override
    public int nextInt() {
      int i = Long.numberOfTrailingZeros(store);

      if (i == 64) {
        throw new NoSuchElementException();
      }

      store ^= 1L << i;
      return i;
    }
  }
}
