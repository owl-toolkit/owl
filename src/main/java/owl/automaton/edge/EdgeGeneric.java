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
import java.util.Objects;
import java.util.PrimitiveIterator.OfInt;
import javax.annotation.Nonnegative;
import javax.annotation.concurrent.Immutable;

@Immutable
final class EdgeGeneric<S> extends Edge<S> {
  private final BitSet acceptance;

  EdgeGeneric(S successor, BitSet acceptance) {
    super(successor);
    assert acceptance.cardinality() > 1;
    this.acceptance = Objects.requireNonNull(acceptance);
  }

  @Override
  public OfInt acceptanceSetIterator() {
    return acceptance.stream().iterator();
  }

  @Override
  public void forEachAcceptanceSet(java.util.function.IntConsumer action) {
    Objects.requireNonNull(action);
    acceptance.stream().forEach(action);
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
    if (!(o instanceof EdgeGeneric)) {
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
