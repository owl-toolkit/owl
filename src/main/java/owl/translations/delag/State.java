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

package owl.translations.delag;

import java.util.Objects;

public final class State<T> {
  public final History past;
  public final ProductState<T> productState;

  State() {
    past = new History();
    productState = ProductState.<T>builder().build();
  }

  State(ProductState<T> state, History past) {
    this.past = past;
    this.productState = state;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof State)) {
      return false;
    }

    State<?> state = (State<?>) o;
    return Objects.equals(productState, state.productState)
      && Objects.equals(past, state.past);
  }

  @Override
  public int hashCode() {
    return Objects.hash(productState, past);
  }

  @Override
  public String toString() {
    return "Fallback: " + productState.fallback()
      + " Finished: " + productState.finished()
      + " Safety: " + productState.safety()
      + " History: " + past;
  }
}
