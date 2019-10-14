/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
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

import de.tum.in.naturals.bitset.ImmutableBitSet;
import java.util.BitSet;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;
import javax.annotation.Nonnegative;
import javax.annotation.concurrent.Immutable;

/**
 * This class (with specialised subclasses) represents edges of automata including their acceptance
 * membership.
 *
 * @param <S>
 *     The type of the (successor) state.
 */
public abstract class Edge<S> {

  protected final S successor;

  private Edge(S successor) {
    this.successor = Objects.requireNonNull(successor);
  }

  /**
   * Creates an edge which belongs to no acceptance set.
   *
   * @param successor
   *     Successor of this edge.
   * @param <S>
   *     Type of the successor.
   *
   * @return An edge leading to {@code successor} with no acceptance.
   */
  public static <S> Edge<S> of(S successor) {
    return new EdgeSingleton<>(successor);
  }

  /**
   * Creates an edge which belongs to a single acceptance set.
   *
   * @param successor
   *     Successor of this edge.
   * @param <S>
   *     Type of the successor.
   * @param acceptance
   *     The delegate set this edge should belong to.
   *
   * @return An edge leading to {@code successor} with given acceptance.
   */
  public static <S> Edge<S> of(S successor, @Nonnegative int acceptance) {
    return new EdgeSingleton<>(successor, acceptance);
  }

  /**
   * Creates an edge which belongs to the specified acceptance sets.
   *
   * @param successor
   *     Successor of this edge.
   * @param <S>
   *     Type of the successor.
   * @param acceptance
   *     The delegate sets this edge should belong to.
   *
   * @return An edge leading to {@code successor} with given acceptance.
   */
  public static <S> Edge<S> of(S successor, BitSet acceptance) {
    if (acceptance.isEmpty()) {
      return of(successor);
    }

    if (acceptance.cardinality() == 1) {
      return of(successor, acceptance.nextSetBit(0));
    }

    if (acceptance.length() <= Long.SIZE) {
      return new EdgeLong<>(successor, acceptance);
    }

    // drop ImmutableBitSet and revise code paths such that BitSet is only copied when necessary.
    return new EdgeGeneric<>(successor, ImmutableBitSet.copyOf(acceptance));
  }

  public abstract BitSet acceptanceSets();

  public abstract void forEachAcceptanceSet(IntConsumer action);

  /**
   * Get the target state of the edge.
   *
   * @return The state the edge points to.
   */
  public final S successor() {
    return successor;
  }

  /**
   * Returns whether this edge has any acceptance set.
   */
  public abstract boolean hasAcceptanceSets();

  /**
   * Test membership of this edge for a specific acceptance set.
   *
   * @param i
   *     The number of the acceptance set.
   *
   * @return True if this edge is a member, false otherwise.
   */
  public abstract boolean inSet(@Nonnegative int i);

  /**
   * Returns the largest acceptance set this edge is a member of, or {@code -1} if none.
   */
  public abstract int largestAcceptanceSet();

  /**
   * Returns the smallest acceptance set this edge is a member of, or {@code Integer.MAX_VALUE} if
   * none.
   */
  public abstract int smallestAcceptanceSet();


  public Edge<S> withAcceptance(int i) {
    return of(successor(), i);
  }

  public Edge<S> withAcceptance(BitSet acceptance) {
    return of(successor(), acceptance);
  }

  public Edge<S> withAcceptance(IntUnaryOperator transformer) {
    var iter = acceptanceSets().stream().map(transformer).filter(i -> i >= 0).iterator();

    if (!iter.hasNext()) {
      return this.withoutAcceptance();
    }

    int first = iter.nextInt();

    if (!iter.hasNext()) {
      return Edge.of(successor(), first);
    }

    BitSet acceptanceSet = new BitSet();
    acceptanceSet.set(first);
    iter.forEachRemaining((IntConsumer) acceptanceSet::set);
    return withAcceptance(acceptanceSet);
  }

  public Edge<S> withoutAcceptance() {
    return Edge.of(successor());
  }

  /**
   * Returns an edge which has the same acceptance but the given state as successor.
   */
  public abstract <T> Edge<T> withSuccessor(T successor);

  @Override
  public final String toString() {
    return "-> " + successor + " {"
      + acceptanceSets().stream().mapToObj(Integer::toString).collect(Collectors.joining(", "))
      + '}';
  }

  @Immutable
  private static final class EdgeGeneric<S> extends Edge<S> {
    private final BitSet acceptance;

    EdgeGeneric(S successor, BitSet acceptance) {
      super(successor);
      assert acceptance.cardinality() > 1;
      this.acceptance = Objects.requireNonNull(acceptance);
    }

    @Override
    public void forEachAcceptanceSet(IntConsumer action) {
      Objects.requireNonNull(action);

      for (int i = acceptance.nextSetBit(0); i >= 0; i = acceptance.nextSetBit(i + 1)) {
        action.accept(i);

        if (i == Integer.MAX_VALUE) {
          throw new IllegalStateException();
        }
      }
    }

    @Override
    public BitSet acceptanceSets() {
      return (BitSet) acceptance.clone();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }

      if (!(o instanceof Edge.EdgeGeneric)) {
        // instanceof is false when o == null
        return false;
      }

      EdgeGeneric<?> other = (EdgeGeneric<?>) o;
      return successor.equals(other.successor) && acceptance.equals(other.acceptance);
    }

    @Override
    public boolean hasAcceptanceSets() {
      return true;
    }

    @Override
    public int hashCode() {
      // Not using Objects.hash to avoid var-ags array instantiation
      return 31 * acceptance.hashCode() + successor.hashCode();
    }

    @Override
    public boolean inSet(@Nonnegative int i) {
      return acceptance.get(i);
    }

    @Override
    public int largestAcceptanceSet() {
      return acceptance.length() - 1;
    }

    @Override
    public int smallestAcceptanceSet() {
      return acceptance.nextSetBit(0);
    }

    @Override
    public <T> EdgeGeneric<T> withSuccessor(T successor) {
      return new EdgeGeneric<>(successor, acceptance);
    }
  }

  @Immutable
  private static final class EdgeLong<S> extends Edge<S> {
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
    public void forEachAcceptanceSet(IntConsumer action) {
      Objects.requireNonNull(action);

      long localStore = store;
      while (localStore != 0) {
        int i = Long.numberOfTrailingZeros(localStore);
        assert i != 64;
        localStore ^= 1L << i;
        action.accept(i);
      }
    }

    @Override
    public BitSet acceptanceSets() {
      BitSet bitSet = new BitSet();
      forEachAcceptanceSet(bitSet::set);
      return bitSet;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }

      if (!(o instanceof Edge.EdgeLong)) {
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
      return 31 * Long.hashCode(store) + successor.hashCode();
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
  }

  @Immutable
  private static final class EdgeSingleton<S> extends Edge<S> {
    private static final int EMPTY_ACCEPTANCE = -1;

    private final int acceptance;

    EdgeSingleton(S successor) {
      super(successor);
      this.acceptance = EMPTY_ACCEPTANCE;
    }

    EdgeSingleton(S successor, @Nonnegative int acceptance) {
      super(successor);
      Objects.checkIndex(acceptance, Integer.MAX_VALUE);
      this.acceptance = acceptance;
    }

    @Override
    public void forEachAcceptanceSet(IntConsumer action) {
      Objects.requireNonNull(action);

      if (hasAcceptanceSets()) {
        action.accept(acceptance);
      }
    }

    @Override
    public BitSet acceptanceSets() {
      BitSet bitSet = new BitSet();

      if (hasAcceptanceSets()) {
        bitSet.set(acceptance);
      }

      return bitSet;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }

      if (!(o instanceof Edge.EdgeSingleton)) {
        // instanceof is false when o == null
        return false;
      }

      EdgeSingleton<?> other = (EdgeSingleton<?>) o;
      return acceptance == other.acceptance && successor.equals(other.successor);
    }

    @Override
    public boolean hasAcceptanceSets() {
      return acceptance != EMPTY_ACCEPTANCE;
    }

    @Override
    public int hashCode() {
      // Not using Objects.hash to avoid var-ags array instantiation
      return 31 * (31 + successor.hashCode()) + acceptance;
    }

    @Override
    public boolean inSet(@Nonnegative int i) {
      Objects.checkIndex(i, Integer.MAX_VALUE);
      return i == acceptance;
    }

    @Override
    public int largestAcceptanceSet() {
      return hasAcceptanceSets() ? acceptance : -1;
    }

    @Override
    public int smallestAcceptanceSet() {
      return hasAcceptanceSets() ? acceptance : Integer.MAX_VALUE;
    }

    @Override
    public Edge<S> withAcceptance(int acceptance) {
      Objects.checkIndex(acceptance, Integer.MAX_VALUE);
      return this.acceptance == acceptance
        ? this
        : new EdgeSingleton<>(successor, acceptance);
    }

    @Override
    public Edge<S> withoutAcceptance() {
      return hasAcceptanceSets()
        ? new EdgeSingleton<>(successor)
        : this;
    }

    @Override
    public <T> EdgeSingleton<T> withSuccessor(T successor) {
      return hasAcceptanceSets()
        ? new EdgeSingleton<>(successor, acceptance)
        : new EdgeSingleton<>(successor);
    }
  }
}