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
import com.google.common.collect.ImmutableMap;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;

public class ProductState {

  final ImmutableList<BitSet> history;
  final ImmutableMap<Formula, EquivalenceClass> safetyStates;

  public ProductState(ImmutableMap<Formula, EquivalenceClass> safetyStates, List<BitSet> history) {
    this.safetyStates = safetyStates;
    this.history = ImmutableList.copyOf(history);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ProductState that = (ProductState) o;
    return safetyStates.equals(that.safetyStates) && history.equals(that.history);
  }

  @Override
  public int hashCode() {
    return Objects.hash(safetyStates, history);
  }

  @Override
  public String toString() {
    return "ProductState{history=" + history + ", safetyStates=" + safetyStates + '}';
  }
}
