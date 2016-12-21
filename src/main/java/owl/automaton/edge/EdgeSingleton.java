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

import java.util.Objects;
import java.util.stream.IntStream;
import javax.annotation.Nonnegative;
import javax.annotation.concurrent.Immutable;

@Immutable
final class EdgeSingleton<S> implements Edge<S> {
  private static final int EMPTY_ACCEPTANCE = -1;

  private final S successor;
  private final int acceptance;

  EdgeSingleton(final S successor) {
    this.acceptance = EMPTY_ACCEPTANCE;
    this.successor = successor;
  }

  EdgeSingleton(final S successor, @Nonnegative final int acceptance) {
    assert acceptance >= 0;
    this.successor = successor;
    this.acceptance = acceptance;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof EdgeSingleton)) {
      // instanceof is false when o == null
      return false;
    }

    final EdgeSingleton other = (EdgeSingleton) o;
    return Objects.equals(this.acceptance, other.acceptance) &&
        Objects.equals(this.successor, other.successor);
  }

  @Override
  public S getSuccessor() {
    return successor;
  }

  @Override
  public int hashCode() {
    return 31 * (31 + successor.hashCode()) + acceptance;
  }

  @Override
  public boolean inSet(@Nonnegative final int i) {
    assert i >= 0;
    return i == acceptance;
  }

  @Override
  public IntStream acceptanceSetStream() {
    if (acceptance == EMPTY_ACCEPTANCE) {
      return IntStream.empty();
    }
    return IntStream.of(acceptance);
  }

  @Override
  public String toString() {
    return "-> " + successor + " {" + acceptance + '}';
  }
}
