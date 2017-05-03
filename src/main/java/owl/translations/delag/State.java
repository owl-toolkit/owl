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

package owl.translations.delag;

import com.google.common.collect.ImmutableList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;

public class State<T> {

  final ImmutableList<BitSet> history;
  final ProductState<T> productState;

  State() {
    history = ImmutableList.of();
    productState = (ProductState<T>) ProductState.builder().build();
  }

  State(ProductState<T> state, List<BitSet> history) {
    this.history = ImmutableList.copyOf(history);
    this.productState = state;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final State<?> state = (State<?>) o;
    return Objects.equals(productState, state.productState)
      && Objects.equals(history, state.history);
  }

  @Override
  public int hashCode() {
    return Objects.hash(productState, history);
  }

  @Override
  public String toString() {
    return "Fallback: " + productState.fallback
      + " Finished: " + productState.finished
      + " Safety: " + productState.safety
      + " History: " + history;
  }
}
