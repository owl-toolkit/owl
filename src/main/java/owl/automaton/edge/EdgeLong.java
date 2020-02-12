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

package owl.automaton.edge;

import java.util.BitSet;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.PrimitiveIterator;
import java.util.function.IntConsumer;
import javax.annotation.Nonnegative;
import javax.annotation.concurrent.Immutable;

@Immutable
final class EdgeLong<S> extends Edge<S> {
  private final long store;

  private EdgeLong(S successor, long store) {
    super(successor);
    this.store = store;
    assert this.store != 0;
  }

  EdgeLong(S successor, BitSet bitSet) {
    super(successor);
    assert bitSet.length() <= Long.SIZE && bitSet.cardinality() > 1;
    long store = 0L;
    for (int i = 0; i < Long.SIZE; i++) {
      if (bitSet.get(i)) {
        store |= 1L << i;
      }
    }
    this.store = store;
    assert this.store != 0;
  }

  @Override
  public PrimitiveIterator.OfInt acceptanceSetIterator() {
    return new LongBitIterator(store);
  }

  @Override
  public void forEachAcceptanceSet(IntConsumer action) {
    Objects.requireNonNull(action);
    acceptanceSetIterator().forEachRemaining(action);
  }

  @Override
  public BitSet acceptanceSets() {
    BitSet bitSet = new BitSet();
    acceptanceSetIterator().forEachRemaining((IntConsumer) bitSet::set);
    return bitSet;
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
    return store == other.store && successor.equals(other.successor);
  }

  @Override
  public boolean hasAcceptanceSets() {
    return true;
  }

  @Override
  public int hashCode() {
    // Not using Objects.hash to avoid var-ags array instantiation
    return 31 * (int) (store ^ (store >>> 32)) + successor.hashCode();
  }

  @Override
  public boolean inSet(@Nonnegative int i) {
    Objects.checkIndex(i, Integer.MAX_VALUE);
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
  public <T> EdgeLong<T> withSuccessor(T successor) {
    return new EdgeLong<>(successor, store);
  }

  private static final class LongBitIterator implements PrimitiveIterator.OfInt {
    private long store;

    private LongBitIterator(long store) {
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
