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

import de.tum.in.naturals.NaturalsTransformer;
import de.tum.in.naturals.bitset.BitSets;
import de.tum.in.naturals.bitset.ImmutableBitSet;
import java.util.BitSet;
import java.util.Objects;
import java.util.PrimitiveIterator;
import java.util.function.IntConsumer;
import java.util.function.IntUnaryOperator;
import javax.annotation.Nonnegative;

/**
 * This class (with specialised subclasses) represents edges of automata including their acceptance
 * membership.
 *
 * @param <S>
 *     The type of the (successor) state.
 */
public abstract class Edge<S> {

  final S successor;

  Edge(S successor) {
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

    return new EdgeGeneric<>(successor, ImmutableBitSet.copyOf(acceptance));
  }


  /**
   * An iterator containing all acceptance sets this edge is a member of in ascending order.
   *
   * @return An iterator with all acceptance sets of this edge.
   */
  public abstract PrimitiveIterator.OfInt acceptanceSetIterator();

  public abstract void forEachAcceptanceSet(IntConsumer action);

  public abstract BitSet acceptanceSets();

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
   * Returns the largest acceptance set this edge is a member of, or {@code Integer.MAX_VALUE} if
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
    PrimitiveIterator.OfInt iter = new NaturalsTransformer(acceptanceSetIterator(), transformer);

    if (!iter.hasNext()) {
      return this.withoutAcceptance();
    }

    int first = iter.nextInt();

    if (!iter.hasNext()) {
      return Edge.of(successor(), first);
    }

    BitSet acceptanceSet = BitSets.of(iter);
    acceptanceSet.set(first);
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
  public String toString() {
    PrimitiveIterator.OfInt acceptanceSetIterator = acceptanceSetIterator();
    StringBuilder builder = new StringBuilder(10);
    builder.append(acceptanceSetIterator.nextInt());
    acceptanceSetIterator.forEachRemaining((int x) -> builder.append(", ").append(x));
    return "-> " + successor + " {" + builder + '}';
  }
}