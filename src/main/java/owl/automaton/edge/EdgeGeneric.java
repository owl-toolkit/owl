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

import java.util.BitSet;
import java.util.Objects;
import java.util.stream.IntStream;
import javax.annotation.Nonnegative;
import javax.annotation.concurrent.Immutable;

@Immutable
final class EdgeGeneric<S> implements Edge<S> {
  private final S successor;
  private final BitSet acceptance;

  EdgeGeneric(final S successor, final BitSet acceptance) {
    assert acceptance.cardinality() > 1;
    this.successor = successor;
    this.acceptance = acceptance;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof EdgeGeneric)) {
      // instanceof is false when o == null
      return false;
    }

    final EdgeGeneric<?> other = (EdgeGeneric<?>) o;
    return Objects.equals(acceptance, other.acceptance)
        && Objects.equals(successor, other.successor);
  }

  @Override
  public S getSuccessor() {
    return successor;
  }

  @Override
  public int hashCode() {
    return 31 * (31 + successor.hashCode()) + acceptance.hashCode();
  }

  @Override
  public boolean inSet(@Nonnegative final int i) {
    return acceptance.get(i);
  }

  @Override
  public IntStream acceptanceSetStream() {
    return acceptance.stream();
  }

  @Override
  public String toString() {
    return "-> " + successor + " {" + acceptance + '}';
  }
}