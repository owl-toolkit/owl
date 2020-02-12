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

import it.unimi.dsi.fastutil.ints.IntIterators;
import java.util.BitSet;
import java.util.Objects;
import java.util.PrimitiveIterator;
import javax.annotation.Nonnegative;
import javax.annotation.concurrent.Immutable;

@Immutable
final class EdgeSingleton<S> extends Edge<S> {
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
  public PrimitiveIterator.OfInt acceptanceSetIterator() {
    return hasAcceptanceSets()
      ? IntIterators.singleton(acceptance)
      : IntIterators.EMPTY_ITERATOR;
  }

  @Override
  public void forEachAcceptanceSet(java.util.function.IntConsumer action) {
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

    if (!(o instanceof EdgeSingleton)) {
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
  public String toString() {
    return "-> " + successor + " {" + (hasAcceptanceSets() ? acceptance : "") + '}';
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
