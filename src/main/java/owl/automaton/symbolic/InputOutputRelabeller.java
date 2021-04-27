package owl.automaton.symbolic;

import static owl.automaton.symbolic.VariableAllocation.VariableType.ATOMIC_PROPOSITION;
import static owl.automaton.symbolic.VariableAllocation.VariableType.COLOUR;
import static owl.automaton.symbolic.VariableAllocation.VariableType.STATE;
import static owl.automaton.symbolic.VariableAllocation.VariableType.SUCCESSOR_STATE;

import java.util.BitSet;
import owl.collections.Pair;

final class InputOutputRelabeller {
  private InputOutputRelabeller() {}

  static Pair<int[], Integer> getMapping(
    VariableAllocation allocation,
    BitSet controlledAPs
  ) {
    int[] relabelling = new int[allocation.numberOfVariables()];
    int counter = 0;
    for (int i : allocation.variables(STATE)) {
      relabelling[i] = counter++;
    }
    for (
      int i = controlledAPs.nextClearBit(0);
      i >= 0 && i < allocation.variables(ATOMIC_PROPOSITION).size();
      i = controlledAPs.nextClearBit(i + 1)
    ) {
      relabelling[allocation.localToGlobal(i, ATOMIC_PROPOSITION)] = counter++;
    }
    int determinizeAt = counter;
    for (int i : allocation.variables(COLOUR)) {
      relabelling[i] = counter++;
    }
    for (int i : allocation.variables(SUCCESSOR_STATE)) {
      relabelling[i] = counter++;
    }
    for (int i = controlledAPs.nextSetBit(0); i >= 0; i = controlledAPs.nextSetBit(i + 1)) {
      relabelling[allocation.localToGlobal(i, ATOMIC_PROPOSITION)] = counter++;
    }
    return Pair.of(relabelling, determinizeAt);
  }

  static int[] invert(int[] arr) {
    int[] result = new int[arr.length];
    for (int i = 0; i < arr.length; i++) {
      result[arr[i]] = i;
    }
    return result;
  }
}
