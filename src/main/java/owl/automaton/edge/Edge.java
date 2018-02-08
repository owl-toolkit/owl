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

package owl.automaton.edge;

import de.tum.in.naturals.NaturalsTransformer;
import de.tum.in.naturals.bitset.ImmutableBitSet;
import java.util.BitSet;
import java.util.PrimitiveIterator;
import java.util.function.IntUnaryOperator;
import javax.annotation.Nonnegative;
import owl.collections.Collections3;

/**
 * This interface represents edges of automata including their acceptance membership.
 *
 * <p>Do not implement this interface when you plan to use the reference implementations given by
 * this package. Their equals and hashCode methods assume that there are no further implementations
 * of this interface to optimise performance.</p>
 *
 * @param <S>
 *     The type of the (successor) state.
 */
public interface Edge<S> {
  /**
   * Creates an edge which belongs to no delegate set.
   *
   * @param successor
   *     Successor of this edge.
   * @param <S>
   *     Type of the successor.
   *
   * @return An edge leading to {@code successor} with no delegate.
   */
  static <S> Edge<S> of(S successor) {
    assert successor != null;
    return new EdgeSingleton<>(successor);
  }

  /**
   * Creates an edge which belongs to a single delegate set.
   *
   * @param successor
   *     Successor of this edge.
   * @param <S>
   *     Type of the successor.
   * @param acceptance
   *     The delegate set this edge should belong to.
   *
   * @return An edge leading to {@code successor} with given delegate.
   */
  static <S> Edge<S> of(S successor, @Nonnegative int acceptance) {
    assert successor != null && acceptance >= 0;
    return new EdgeSingleton<>(successor, acceptance);
  }

  /**
   * Creates an edge which belongs to the specified delegate sets.
   *
   * @param successor
   *     Successor of this edge.
   * @param <S>
   *     Type of the successor.
   * @param acceptance
   *     The delegate sets this edge should belong to.
   *
   * @return An edge leading to {@code successor} with given delegate.
   */
  static <S> Edge<S> of(S successor, BitSet acceptance) {
    assert successor != null;
    if (acceptance.isEmpty()) {
      return of(successor);
    }

    if (acceptance.cardinality() == 1) {
      return of(successor, acceptance.nextSetBit(0));
    }

    if (acceptance.length() <= Long.SIZE) {
      return new EdgeLong<>(successor, acceptance);
    }

    return new EdgeGeneric<>(successor, ImmutableBitSet.copyOf(acceptance));
  }

  /**
   * An iterator containing all acceptance sets this edge is a member of in ascending order.
   *
   * @return An iterator with all acceptance sets of this edge.
   */
  PrimitiveIterator.OfInt acceptanceSetIterator();

  /**
   * Get the target state of the edge.
   *
   * @return The state the edge points to.
   */
  S getSuccessor();

  /**
   * Returns whether this edge has any acceptance set.
   */
  boolean hasAcceptanceSets();

  /**
   * Test membership of this edge for a specific acceptance set.
   *
   * @param i
   *     The number of the acceptance set.
   *
   * @return True if this edge is a member, false otherwise.
   */
  boolean inSet(@Nonnegative int i);

  /**
   * Returns the largest acceptance set this edge is a member of, or {@code -1} if none.
   */
  int largestAcceptanceSet();

  /**
   * Returns the largest acceptance set this edge is a member of, or {@code Integer.MAX_VALUE} if
   * none.
   */
  int smallestAcceptanceSet();

  default Edge<S> withAcceptance(BitSet acceptance) {
    return of(getSuccessor(), acceptance);
  }

  default Edge<S> withAcceptance(IntUnaryOperator transformer) {
    PrimitiveIterator.OfInt iter = new NaturalsTransformer(acceptanceSetIterator(), transformer);

    if (!iter.hasNext()) {
      return Edge.of(getSuccessor());
    }

    int first = iter.nextInt();

    if (!iter.hasNext()) {
      return Edge.of(getSuccessor(), first);
    }

    BitSet acceptanceSet = Collections3.toBitSet(iter);
    acceptanceSet.set(first);
    return withAcceptance(acceptanceSet);
  }

  /**
   * Returns an edge which has the same acceptance but the given state as successor.
   */
  <T> Edge<T> withSuccessor(T successor);
}