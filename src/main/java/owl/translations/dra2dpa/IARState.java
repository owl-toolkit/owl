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

package owl.translations.dra2dpa;

import com.google.auto.value.AutoValue;
import de.tum.in.naturals.IntPreOrder;
import owl.automaton.AnnotatedState;

@AutoValue
public abstract class IARState<R> implements AnnotatedState<R> {
  @Override
  public abstract R state();

  public abstract IntPreOrder record();

  public static <R> IARState<R> of(R originalState) {
    return of(originalState, IntPreOrder.empty());
  }

  public static <R> IARState<R> of(R originalState, IntPreOrder record) {
    return new AutoValue_IARState<>(originalState, record);
  }

  @Override
  public String toString() {
    if (record().size() == 0) {
      return String.format("{%s}", state());
    }
    return String.format("{%s|%s}", state(), record());
  }
}
