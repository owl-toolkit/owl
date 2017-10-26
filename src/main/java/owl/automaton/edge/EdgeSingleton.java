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

import it.unimi.dsi.fastutil.ints.IntIterators;
import java.util.Objects;
import java.util.PrimitiveIterator;
import java.util.stream.IntStream;
import javax.annotation.Nonnegative;
import javax.annotation.concurrent.Immutable;

@Immutable
final class EdgeSingleton<S> implements Edge<S> {
  private static final int EMPTY_ACCEPTANCE = -1;
  private final int acceptance;
  private final S successor;

  EdgeSingleton(S successor) {
    this.acceptance = EMPTY_ACCEPTANCE;
    this.successor = successor;
  }

  EdgeSingleton(S successor, @Nonnegative int acceptance) {
    assert acceptance >= 0;
    this.successor = successor;
    this.acceptance = acceptance;
  }

  @Override
  public int acceptanceSetCount() {
    return hasAcceptanceSets() ? 1 : 0;
  }

  @Override
  public PrimitiveIterator.OfInt acceptanceSetIterator() {
    return hasAcceptanceSets()
      ? IntIterators.singleton(acceptance)
      : IntIterators.EMPTY_ITERATOR;
  }

  @Override
  public IntStream acceptanceSetStream() {
    return hasAcceptanceSets() ? IntStream.of(acceptance) : IntStream.empty();
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
    return this.acceptance == other.acceptance
      && Objects.equals(this.successor, other.successor);
  }

  @Override
  public S getSuccessor() {
    return successor;
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
    assert i >= 0;
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
    return Edge.toString(this);
  }

  @Override
  public EdgeSingleton<S> withSuccessor(S successor) {
    return hasAcceptanceSets()
      ? new EdgeSingleton<>(successor, acceptance)
      : new EdgeSingleton<>(successor);
  }
}
