package owl.automaton.edge;

import java.util.BitSet;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.PrimitiveIterator;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;
import javax.annotation.Nonnegative;
import javax.annotation.concurrent.Immutable;
import owl.util.BitUtil;

@Immutable
final class EdgeLong<S> implements Edge<S> {
  private final long store;
  private final S successor;

  EdgeLong(S successor, BitSet bitSet) {
    assert bitSet.length() <= Long.SIZE && bitSet.cardinality() > 1;
    long[] bitSetLongs = bitSet.toLongArray();
    assert bitSetLongs.length == 1;
    this.store = bitSetLongs[0];
    this.successor = successor;
  }

  @Override
  public PrimitiveIterator.OfInt acceptanceSetIterator() {
    return new LongBitIterator(store);
  }

  @Override
  public IntStream acceptanceSetStream() {
    IntStream.Builder builder = IntStream.builder();
    acceptanceSetIterator().forEachRemaining((IntConsumer) builder::add);
    return builder.build();
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
  public int hashCode() {
    // Not using Objects.hash to avoid var-ags array instantiation
    return 31 * (int) (store ^ (store >>> 32)) + successor.hashCode();
  }

  @Override
  public S getSuccessor() {
    return successor;
  }

  @Override
  public boolean inSet(@Nonnegative int i) {
    assert i >= 0;
    return i < Long.SIZE && BitUtil.isSet(store, i);
  }

  @Override
  public String toString() {
    return Edge.toString(this);
  }

  // Inspired by java.util.BitSet#stream()
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
