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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static owl.automaton.symbolic.VariableAllocation.VariableType.ATOMIC_PROPOSITION;
import static owl.automaton.symbolic.VariableAllocation.VariableType.COLOUR;
import static owl.automaton.symbolic.VariableAllocation.VariableType.STATE;
import static owl.automaton.symbolic.VariableAllocation.VariableType.SUCCESSOR_STATE;

import org.junit.jupiter.api.Test;

public class SequentialVariableAllocationCombinerTest {

  @Test
  void sequentialVariableAllocationCombinerTest() {
    var allocation0 = new VariableAllocation(
      ATOMIC_PROPOSITION,
      STATE,
      ATOMIC_PROPOSITION,
      STATE,
      COLOUR,
      SUCCESSOR_STATE,
      STATE,
      SUCCESSOR_STATE,
      SUCCESSOR_STATE
    );
    var allocation1 = new VariableAllocation(
      ATOMIC_PROPOSITION,
      ATOMIC_PROPOSITION,
      STATE,
      COLOUR,
      SUCCESSOR_STATE
    );
    var allocation2 = new VariableAllocation(
      COLOUR,
      COLOUR,
      STATE,
      SUCCESSOR_STATE,
      ATOMIC_PROPOSITION,
      ATOMIC_PROPOSITION,
      SUCCESSOR_STATE,
      STATE
    );
    // AP_0, AP_1, S_0_0, S_0_1, C_0_0, X_0_0, S_0_2, X_0_1, X_0_1, S_1_0, C_1_0, X_1_0, C_2_0,
    // C_2_1, S_2_0, X_2_0, X_2_1, S_2_1
    var combination = new SequentialVariableAllocationCombiner().combine(
      allocation0, allocation1, allocation2
    );
    assertAllocationLocalGlobalEquals(allocation0, new int[] {0, 2, 1, 3, 4, 5, 6, 7, 8},
      combination);
    assertLocalGlobalEquals(STATE, new int[] {2, 3, 6, 9, 14, 17}, combination);
    assertLocalGlobalEquals(ATOMIC_PROPOSITION, new int[] {0, 1}, combination);
    assertLocalGlobalEquals(COLOUR, new int[] {4, 10, 12, 13}, combination);
    assertLocalGlobalEquals(SUCCESSOR_STATE, new int[] {5, 7, 8, 11, 15, 16}, combination);
  }

  private static void assertLocalGlobalEquals(VariableAllocation.VariableType type, int[] expected,
    VariableAllocation actual) {
    for (int i = 0; i < expected.length; i++) {
      assertEquals(expected[i], actual.localToGlobal(i, type));
      assertEquals(i, actual.globalToLocal(expected[i]));
    }
  }

  private static void assertAllocationLocalGlobalEquals(
    VariableAllocation allocation, int[] expected,
    VariableAllocationCombination actual) {
    for (int i = 0; i < expected.length; i++) {
      assertEquals(expected[i],
        actual.allocationLocalToGlobal(i, allocation));
    }
  }
}
