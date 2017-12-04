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

import de.tum.in.naturals.bitset.BitSets;
import java.util.BitSet;
import java.util.Objects;
import java.util.PrimitiveIterator.OfInt;
import javax.annotation.Nonnegative;
import javax.annotation.concurrent.Immutable;

@Immutable
final class EdgeGeneric<S> implements Edge<S> {
  private final BitSet acceptance;
  private final S successor;

  EdgeGeneric(S successor, BitSet acceptance) {
    assert acceptance.cardinality() > 1;

    this.successor = successor;
    this.acceptance = acceptance;
  }

  @Override
  public OfInt acceptanceSetIterator() {
    return BitSets.iterator(acceptance);
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
    return Objects.equals(acceptance, other.acceptance)
      && Objects.equals(successor, other.successor);
  }

  @Override
  public S getSuccessor() {
    return successor;
  }

  @Override
  public boolean hasAcceptanceSets() {
    assert !acceptance.isEmpty();
    return true;
  }

  @Override
  public int hashCode() {
    // Not using Objects.hash to avoid var-ags array instantiation
    return 31 * acceptance.hashCode() + successor.hashCode();
  }

  @Override
  public boolean inSet(@Nonnegative int i) {
    assert i >= 0;
    return acceptance.get(i);
  }

  @Override
  public int largestAcceptanceSet() {
    assert !acceptance.isEmpty();
    return acceptance.length() - 1;
  }

  @Override
  public int smallestAcceptanceSet() {
    assert !acceptance.isEmpty();
    return acceptance.nextSetBit(0);
  }

  @Override
  public <T> EdgeGeneric<T> withSuccessor(T successor) {
    return new EdgeGeneric<>(successor, acceptance);
  }

  @Override
  public String toString() {
    OfInt acceptanceSetIterator = acceptanceSetIterator();
    StringBuilder builder = new StringBuilder(10);
    builder.append(acceptanceSetIterator.nextInt());
    acceptanceSetIterator.forEachRemaining((int x) -> builder.append(", ").append(x));
    return "-> " + getSuccessor() + " {" + builder.toString() + '}';
  }
}
