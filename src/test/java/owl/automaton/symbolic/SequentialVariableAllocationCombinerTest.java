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
import static owl.automaton.symbolic.SymbolicAutomaton.VariableType.ATOMIC_PROPOSITION;
import static owl.automaton.symbolic.SymbolicAutomaton.VariableType.COLOUR;
import static owl.automaton.symbolic.SymbolicAutomaton.VariableType.STATE;
import static owl.automaton.symbolic.SymbolicAutomaton.VariableType.SUCCESSOR_STATE;

import java.util.List;
import org.junit.jupiter.api.Test;

public class SequentialVariableAllocationCombinerTest {

  @Test
  void sequentialVariableAllocationCombinerTest() {
    var allocation0 = new ManualVariableAllocation(
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
    var allocation1 = new ManualVariableAllocation(
      ATOMIC_PROPOSITION,
      ATOMIC_PROPOSITION,
      STATE,
      COLOUR,
      SUCCESSOR_STATE
    );
    var allocation2 = new ManualVariableAllocation(
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
    var combination1 = new SequentialVariableAllocationCombiner(
      List.of(allocation0, allocation1, allocation2));
    // S_0_0, S_0_1, C_0_0, X_0_0, S_0_2, X_0_1, X_0_1, S_1_0, C_1_0, X_1_0, C_2_0, C_2_1, S_2_0,
    // X_2_0, X_2_1, S_2_1, AP_0, AP_1
    var combination2 = new SequentialVariableAllocationCombiner(
      List.of(allocation0, allocation1, allocation2), false);
    assertAllocationLocalGlobalEquals(allocation0, new int[] {0, 2, 1, 3, 4, 5, 6, 7, 8},
      combination1);
    assertAllocationLocalGlobalEquals(allocation0, new int[] {16, 0, 17, 1, 2, 3, 4, 5, 6},
      combination2);

    assertLocalGlobalEquals(STATE, new int[] {2, 3, 6, 9, 14, 17}, combination1);
    assertLocalGlobalEquals(ATOMIC_PROPOSITION, new int[] {0, 1}, combination1);
    assertLocalGlobalEquals(COLOUR, new int[] {4, 10, 12, 13}, combination1);
    assertLocalGlobalEquals(SUCCESSOR_STATE, new int[] {5, 7, 8, 11, 15, 16}, combination1);

    assertLocalGlobalEquals(STATE, new int[] {0, 1, 4, 7, 12, 15}, combination2);
    assertLocalGlobalEquals(ATOMIC_PROPOSITION, new int[] {16, 17}, combination2);
    assertLocalGlobalEquals(COLOUR, new int[] {2, 8, 10, 11}, combination2);
    assertLocalGlobalEquals(SUCCESSOR_STATE, new int[] {3, 5, 6, 9, 13, 14}, combination2);
  }

  private static void assertLocalGlobalEquals(SymbolicAutomaton.VariableType type, int[] expected,
    SymbolicAutomaton.VariableAllocation actual) {
    for (int i = 0; i < expected.length; i++) {
      assertEquals(expected[i],
        actual.localToGlobal(i, type));
      assertEquals(i,
        actual.globalToLocal(expected[i], type)
      );
    }
  }

  private static void assertAllocationLocalGlobalEquals(
    SymbolicAutomaton.VariableAllocation allocation, int[] expected,
    SymbolicAutomaton.AllocationCombiner actual) {
    for (int i = 0; i < expected.length; i++) {
      assertEquals(expected[i],
        actual.localToGlobal(i, allocation));
    }
  }
}
