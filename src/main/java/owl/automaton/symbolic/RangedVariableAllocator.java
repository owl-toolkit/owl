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

package owl.automaton.symbolic;

import com.google.common.base.Preconditions;
import java.util.List;
import java.util.Set;
import owl.automaton.symbolic.VariableAllocation.VariableType;

public final class RangedVariableAllocator implements VariableAllocator {

  private final List<VariableType> order;

  public RangedVariableAllocator(VariableType... order) {
    this.order = List.of(order);
    Preconditions.checkArgument(Set.copyOf(this.order).size() == 4);
  }

  @Override
  public VariableAllocation allocate(
    int stateVariables, int atomicPropositions, int colours) {
    int offset = 0;
    int[][] localToGlobal = new int[VariableType.values().length][];
    for (VariableType type : order) {
      int size = 0;
      switch (type) {
        case SUCCESSOR_STATE:
        case STATE:
          size = stateVariables;
          break;
        case COLOUR:
          size = colours;
          break;
        case ATOMIC_PROPOSITION:
          size = atomicPropositions;
          break;
        default:
          throw new AssertionError("unreachable");
      }
      int[] localToGlobalArr = new int[size];
      for (int i = 0; i < size; i++) {
        localToGlobalArr[i] = offset + i;
      }
      localToGlobal[type.ordinal()] = localToGlobalArr;
      offset += size;
    }
    return new VariableAllocation(localToGlobal);
  }
}
