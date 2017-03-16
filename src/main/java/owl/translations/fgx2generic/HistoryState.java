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

package owl.translations.fgx2generic;

import com.google.common.collect.ImmutableList;
import java.util.BitSet;

public class HistoryState {

  final ImmutableList<BitSet> history;

  HistoryState(ImmutableList<BitSet> history) {
    this.history = history;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    return history.equals(((HistoryState) o).history);
  }

  @Override
  public int hashCode() {
    return history.hashCode();
  }

  @Override
  public String toString() {
    return "HistoryState{" + history + '}';
  }
}
