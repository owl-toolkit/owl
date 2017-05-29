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

package owl.translations.nba2ldba;

import com.google.common.collect.ImmutableSet;

import java.util.Objects;
import java.util.Set;
import javax.annotation.Nonnegative;

public class BreakpointState<S> {

  @Nonnegative
  final int ix;
  final Set<S> mx;
  final Set<S> nx;

  BreakpointState(@Nonnegative int i, Set<S> m, Set<S> n) {
    this.ix = i;
    this.mx = ImmutableSet.copyOf(m);
    this.nx = ImmutableSet.copyOf(n);
  }

  public String toString() {
    return "(" + ix + ", " + this.mx + ", " + this.nx + ")";
  }

  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    BreakpointState<?> state = (BreakpointState<?>) o;
    return ix == state.ix && Objects.equals(mx, state.mx)
        && Objects.equals(nx, state.nx);
  }

  @Override
  public int hashCode() {
    return 31 * mx.hashCode() + nx.hashCode() + ix;
  }
}
