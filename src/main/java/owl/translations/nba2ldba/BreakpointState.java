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

package owl.translations.nba2ldba;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.Preconditions;
import java.util.Set;
import javax.annotation.Nonnegative;

@AutoValue
public abstract class BreakpointState<S> {

  @Nonnegative
  abstract int ix();

  abstract Set<S> mx();

  abstract Set<S> nx();

  static <S> BreakpointState<S> of(@Nonnegative int i, Set<S> m, Set<S> n) {
    Preconditions.checkArgument(i >= 0, "Index needs to be non-negative.");
    return new AutoValue_BreakpointState<>(i, Set.copyOf(m), Set.copyOf(n));
  }

  @Override
  public abstract boolean equals(Object object);

  @Memoized
  @Override
  public abstract int hashCode();

  @Override
  public String toString() {
    return String.format("(%d, %s, %s)", ix(), mx(), nx());
  }
}
