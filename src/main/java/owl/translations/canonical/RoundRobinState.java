/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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

package owl.translations.canonical;

import java.util.Objects;
import javax.annotation.Nonnegative;
import org.immutables.value.Value;
import owl.util.annotation.Tuple;

@Value.Immutable
@Tuple
public abstract class RoundRobinState<E> {
  @Nonnegative
  public abstract int index();

  public abstract E state();

  public static <E> RoundRobinState<E> of(int index, E state) {
    Objects.checkIndex(index, Integer.MAX_VALUE);
    return RoundRobinStateTuple.create(index, state);
  }
}
