package owl.automaton.symbolic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static owl.automaton.symbolic.VariableAllocation.VariableType.ATOMIC_PROPOSITION;
import static owl.automaton.symbolic.VariableAllocation.VariableType.COLOUR;
import static owl.automaton.symbolic.VariableAllocation.VariableType.STATE;
import static owl.automaton.symbolic.VariableAllocation.VariableType.SUCCESSOR_STATE;

import org.junit.jupiter.api.Test;

public class VariableAllocationTest {

  @Test
  void variableAllocationTest() {
    // AP_0, S_0, AP_1, S_1, C_0, X_0, S_2, X_1, X_2
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
    assertEquals(0, allocation0.localToGlobal(0, ATOMIC_PROPOSITION));
    assertEquals(2, allocation0.localToGlobal(1, ATOMIC_PROPOSITION));

    assertEquals(1, allocation0.localToGlobal(0, STATE));
    assertEquals(3, allocation0.localToGlobal(1, STATE));
    assertEquals(6, allocation0.localToGlobal(2, STATE));

    assertEquals(4, allocation0.localToGlobal(0, COLOUR));

    assertEquals(5, allocation0.localToGlobal(0, SUCCESSOR_STATE));
    assertEquals(7, allocation0.localToGlobal(1, SUCCESSOR_STATE));
    assertEquals(8, allocation0.localToGlobal(2, SUCCESSOR_STATE));


    assertEquals(0, allocation0.globalToLocal(0));
    assertEquals(1, allocation0.globalToLocal(2));

    assertEquals(0, allocation0.globalToLocal(1));
    assertEquals(1, allocation0.globalToLocal(3));
    assertEquals(2, allocation0.globalToLocal(6));

    assertEquals(0, allocation0.globalToLocal(4));

    assertEquals(0, allocation0.globalToLocal(5));
    assertEquals(1, allocation0.globalToLocal(7));
    assertEquals(2, allocation0.globalToLocal(8));
  }
}
