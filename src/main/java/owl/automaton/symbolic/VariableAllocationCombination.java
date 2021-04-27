package owl.automaton.symbolic;

import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class VariableAllocationCombination extends VariableAllocation {
  private final int[] globalToLocal;
  private final Map<VariableAllocation, int[]> localToGlobal;

  VariableAllocationCombination(Map<VariableAllocation, int[]> localToGlobal) {
    super(flatten(localToGlobal));
    this.localToGlobal = new HashMap<>();
    this.globalToLocal = new int[super.numberOfVariables()];
    for (var entry : localToGlobal.entrySet()) {
      this.localToGlobal.put(
        entry.getKey(),
        Arrays.copyOf(entry.getValue(), entry.getValue().length)
      );
      for (int i = 0; i < entry.getValue().length; i++) {
        globalToLocal[entry.getValue()[i]] = i;
      }
    }
  }

  private static int[][] flatten(Map<VariableAllocation, int[]> allocations) {
    Map<VariableType, Set<Integer>> map = new EnumMap<>(VariableType.class);
    for (VariableType type : VariableType.values()) {
      map.put(type, new HashSet<>());
    }
    for (var entry : allocations.entrySet()) {
      for (int i = 0; i < entry.getValue().length; i++) {
        int j = entry.getValue()[i];
        VariableType type = entry.getKey().typeOf(i);
        map.get(type).add(j);
      }
    }
    int[][] result = new int[VariableType.values().length][];
    for (VariableType type : VariableType.values()) {
      result[type.ordinal()] = map.get(type).stream().sorted().mapToInt(i -> i).toArray();
    }
    return result;
  }

  int allocationLocalToGlobal(int variable, VariableAllocation allocation) {
    Objects.checkIndex(variable, allocation.numberOfVariables());
    return localToGlobal.get(allocation)[variable];
  }

  int globalToAllocationLocal(int variable) {
    return globalToLocal[variable];
  }


  public BitSet localToGlobal(BitSet bitSet, VariableAllocation allocation) {
    BitSet result = new BitSet();
    for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i + 1)) {
      result.set(allocationLocalToGlobal(i, allocation));
    }
    return result;
  }

  public BitSet globalToLocal(BitSet bitSet, VariableAllocation allocation) {
    BitSet result = new BitSet();
    for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i + 1)) {
      result.set(globalToLocal(i));
    }
    return result;
  }
}
