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
  final ImmutableSet<S> mx;
  final ImmutableSet<S> nx;

  BreakpointState(@Nonnegative int i, Set<S> m, Set<S> n) {
    this.ix = i;
    this.mx = ImmutableSet.copyOf(m);
    this.nx = ImmutableSet.copyOf(n);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final BreakpointState<?> that = (BreakpointState<?>) o;
    return ix == that.ix
      && Objects.equals(mx, that.mx)
      && Objects.equals(nx, that.nx);
  }

  @Override
  public int hashCode() {
    int result = ix;

    for (Object element : new Object[] {mx, nx}) {
      result = 31 * result + element.hashCode();
    }

    return result;
  }

  @Override
  public String toString() {
    return "(" + ix + ", " + this.mx + ", " + this.nx + ")";
  }
}
