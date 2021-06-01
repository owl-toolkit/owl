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

import org.junit.jupiter.api.Test;

public class ManualVariableAllocationTest {

  @Test
  void manualVariableAllocationTest() {
    // AP_0, S_0, AP_1, S_1, C_0, X_0, S_2, X_1, X_2
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
    assertEquals(0, allocation0.localToGlobal(0, ATOMIC_PROPOSITION));
    assertEquals(2, allocation0.localToGlobal(1, ATOMIC_PROPOSITION));

    assertEquals(1, allocation0.localToGlobal(0, STATE));
    assertEquals(3, allocation0.localToGlobal(1, STATE));
    assertEquals(6, allocation0.localToGlobal(2, STATE));

    assertEquals(4, allocation0.localToGlobal(0, COLOUR));

    assertEquals(5, allocation0.localToGlobal(0, SUCCESSOR_STATE));
    assertEquals(7, allocation0.localToGlobal(1, SUCCESSOR_STATE));
    assertEquals(8, allocation0.localToGlobal(2, SUCCESSOR_STATE));


    assertEquals(0, allocation0.globalToLocal(0, ATOMIC_PROPOSITION));
    assertEquals(1, allocation0.globalToLocal(2, ATOMIC_PROPOSITION));

    assertEquals(0, allocation0.globalToLocal(1, STATE));
    assertEquals(1, allocation0.globalToLocal(3, STATE));
    assertEquals(2, allocation0.globalToLocal(6, STATE));

    assertEquals(0, allocation0.globalToLocal(4, COLOUR));

    assertEquals(0, allocation0.globalToLocal(5, SUCCESSOR_STATE));
    assertEquals(1, allocation0.globalToLocal(7, SUCCESSOR_STATE));
    assertEquals(2, allocation0.globalToLocal(8, SUCCESSOR_STATE));
  }
}
