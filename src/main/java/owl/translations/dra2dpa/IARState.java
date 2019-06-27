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

import de.tum.in.naturals.IntPreOrder;
import org.immutables.value.Value;
import owl.automaton.util.AnnotatedState;
import owl.util.annotation.HashedTuple;

@Value.Immutable
@HashedTuple
public abstract class IARState<R> implements AnnotatedState<R> {
  @Override
  public abstract R state();

  public abstract IntPreOrder record();


  public static <R> IARState<R> active(R originalState, IntPreOrder record) {
    return IARStateTuple.create(originalState, record);
  }

  public static <R> IARState<R> trivial(R originalState) {
    return IARStateTuple.create(originalState, IntPreOrder.empty());
  }


  @Override
  public String toString() {
    if (record().size() == 0) {
      return String.format("{%s}", state());
    }
    return String.format("{%s|%s}", state(), record());
  }
}
