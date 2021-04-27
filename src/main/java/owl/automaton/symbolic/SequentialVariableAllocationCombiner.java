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

import static owl.automaton.symbolic.VariableAllocation.VariableType.ATOMIC_PROPOSITION;

import com.google.common.base.Preconditions;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Combines variable allocations in sequence and moves atomic propositions
 * to the front. E.g. APs, allocation 0, allocation 1, ...
 * or allocation 0, allocation 1, ..., APs
 */
public class SequentialVariableAllocationCombiner implements VariableAllocationCombiner {

  @Override
  public VariableAllocationCombination combine(
    VariableAllocation... allocations) {
    Preconditions.checkArgument(allocations.length == 0 || Arrays.stream(allocations).allMatch(
      allocation -> allocation.variables(ATOMIC_PROPOSITION).size() == allocations[0].variables(
        ATOMIC_PROPOSITION).size()
    ));
    if (allocations.length == 0) {
      return new VariableAllocationCombination(Map.of());
    }
    int counter = allocations[0].variables(ATOMIC_PROPOSITION).size();
    Map<VariableAllocation, int[]> result = new HashMap<>();
    for (VariableAllocation allocation : allocations) {
      int[] allocationLocalToGlobal = new int[allocation.numberOfVariables()];
      for (int i = 0; i < allocation.numberOfVariables(); i++) {
        allocationLocalToGlobal[i] = allocation.typeOf(i) == ATOMIC_PROPOSITION
          ? allocation.globalToLocal(i) : counter++;
      }
      result.put(allocation, allocationLocalToGlobal);
    }
    return new VariableAllocationCombination(result);
  }
}
