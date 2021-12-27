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

package owl.automaton.edge;

import com.google.auto.value.AutoValue;
import java.util.BitSet;
import java.util.Collection;
import java.util.PrimitiveIterator;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import javax.annotation.Nonnegative;
import owl.collections.ImmutableBitSet;

/**
 * This class represents edges of automata including their acceptance membership.
 *
 * @param <S>
 *     The type of the (successor) state.
 */
@AutoValue
public abstract class Edge<S> {

  /**
   * Get the target state of the edge.
   *
   * @return The state the edge points to.
   */
  public abstract S successor();

  /**
   * Colours: the acceptance sets this edge is part of.
   */
  public abstract ImmutableBitSet colours();

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
    return of(successor, ImmutableBitSet.of());
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
    return of(successor, ImmutableBitSet.of(acceptance));
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

    return of(successor, ImmutableBitSet.copyOf(acceptance));
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
  public static <S> Edge<S> of(S successor, Collection<Integer> acceptance) {
    return of(successor, ImmutableBitSet.copyOf(acceptance));
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
  public static <S> Edge<S> of(S successor, ImmutableBitSet acceptance) {
    return new AutoValue_Edge<>(successor, acceptance);
  }

  public Edge<S> withAcceptance(int i) {
    return colours().size() == 1 && colours().contains(i) ? this : of(successor(), i);
  }

  public Edge<S> withAcceptance(BitSet acceptance) {
    return of(successor(), acceptance);
  }

  public Edge<S> withAcceptance(ImmutableBitSet acceptance) {
    return colours().equals(acceptance) ? this : of(successor(), acceptance);
  }

  public Edge<S> mapAcceptance(IntUnaryOperator transformer) {
    PrimitiveIterator.OfInt iter = colours().intIterator();

    int first = -1;

    while (first < 0 && iter.hasNext()) {
      first = transformer.applyAsInt(iter.next());
    }

    if (!iter.hasNext()) {
      return first < 0 ? this.withoutAcceptance() : this.withAcceptance(first);
    }

    BitSet acceptanceSet = new BitSet();
    acceptanceSet.set(first);
    iter.forEachRemaining((int x) -> {
      int e = transformer.applyAsInt(x);

      if (0 <= e) {
        acceptanceSet.set(e);
      }
    });
    return withAcceptance(acceptanceSet);
  }

  public Edge<S> withoutAcceptance() {
    return colours().isEmpty() ? this : of(successor());
  }

  /**
   * Returns an edge which has the same acceptance but the given state as successor.
   */
  public <T> Edge<T> withSuccessor(T successor) {
    return of(successor, colours());
  }

  public <T> Edge<T> mapSuccessor(Function<? super S, ? extends T> mapper) {
    return of(mapper.apply(successor()), colours());
  }

  @Override
  public String toString() {
    return "-> " + successor() + ' ' + colours();
  }
}