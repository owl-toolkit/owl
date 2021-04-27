package owl.automaton.symbolic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import owl.collections.BitSet2;
import owl.collections.ImmutableBitSet;

public class VariableAllocation {
  public enum VariableType {
    STATE('s'), COLOUR('c'), ATOMIC_PROPOSITION('a'), SUCCESSOR_STATE('x');
    private final char symbol;

    VariableType(char symbol) {
      this.symbol = symbol;
    }

    public char getSymbol() {
      return symbol;
    }
  }

  private int[] globalToLocal;
  private int[][] localToGlobal;
  private final ImmutableBitSet[] variablesCache;
  private VariableType[] variableTypes;

  private VariableAllocation(int size) {
    int numberOfvariableTypes = VariableType.values().length;
    variablesCache = new ImmutableBitSet[1 << numberOfvariableTypes];
    localToGlobal = new int[numberOfvariableTypes][];
    globalToLocal = new int[size];
    variableTypes = new VariableType[size];
  }

  VariableAllocation(int[][] localToGlobal) {
    this(getSize(localToGlobal));
    for (int i = 0; i < localToGlobal.length; i++) {
      this.localToGlobal[i] = Arrays.copyOf(localToGlobal[i], localToGlobal[i].length);
      for (int j = 0; j < localToGlobal[i].length; j++) {
        variableTypes[localToGlobal[i][j]] = VariableType.values()[i];
        globalToLocal[localToGlobal[i][j]] = j;
      }
    }
  }

  VariableAllocation(VariableType... variables) {
    this(variables.length);
    variableTypes = Arrays.copyOf(variables, variables.length);
    globalToLocal = new int[variableTypes.length];
    localToGlobal = new int[VariableType.values().length][];
    Map<VariableType, List<Integer>> localToGlobalMap = new EnumMap<>(VariableType.class);
    for (VariableType type: VariableType.values()) {
      localToGlobalMap.put(type, new ArrayList<>());
    }
    int[] counts = new int[VariableType.values().length];
    for (int i = 0; i < variableTypes.length; i++) {
      VariableType type = variableTypes[i];
      int local = counts[type.ordinal()]++;
      globalToLocal[i] = local;
      localToGlobalMap.get(type).add(local, i);
    }
    for (var entry : localToGlobalMap.entrySet()) {
      localToGlobal[entry.getKey().ordinal()] = entry.getValue()
        .stream()
        .mapToInt(Integer::intValue)
        .toArray();
    }
  }

  public ImmutableBitSet variables(VariableType... type) {
    int index = indexOfVariables(type);
    ImmutableBitSet result = variablesCache[index];
    if (result == null) {
      result = createImmutableBitSet(type);
      variablesCache[index] = result;
    }
    return result;
  }

  private static int indexOfVariables(VariableType... types) {
    int result = 0;
    for (VariableType type : types) {
      result |= 1 << type.ordinal();
    }
    return result;
  }

  private ImmutableBitSet createImmutableBitSet(VariableType... types) {
    BitSet result = new BitSet();
    EnumSet<VariableType> set = EnumSet.copyOf(Arrays.asList(types));
    for (int i = 0; i < variableTypes.length; i++) {
      if (set.contains(variableTypes[i])) {
        result.set(i);
      }
    }
    return ImmutableBitSet.copyOf(result);
  }

  private static int getSize(int[][] localToGlobal) {
    int ctr = 0;
    for (int[] localToGlobalArr : localToGlobal) {
      ctr += localToGlobalArr.length;
    }
    return ctr;
  }

  public int numberOfVariables() {
    return globalToLocal.length;
  }

  public VariableType typeOf(int variable) {
    Objects.checkIndex(variable, variableTypes.length);
    return variableTypes[variable];
  }

  public List<String> variableNames() {
    List<String> result = new ArrayList<>(globalToLocal.length);
    int[] counts = new int[VariableType.values().length];
    for (int i = 0; i < variableTypes.length; i++) {
      VariableType type = variableTypes[i];
      result.add(i, String.valueOf(type.getSymbol()) + '_' + counts[type.ordinal()]++);
    }
    return result;
  }

  public int localToGlobal(int variable, VariableType type) {
    return localToGlobal[type.ordinal()][variable];
  }

  public int globalToLocal(int variable) {
    return globalToLocal[variable];
  }

  public int[][] getLocalToGlobalArray() {
    int[][] result = new int[localToGlobal.length][];
    for (int i = 0; i < localToGlobal.length; i++) {
      result[i] = Arrays.copyOf(localToGlobal[i], localToGlobal[i].length);
    }
    return result;
  }

  public BitSet localToGlobal(BitSet bitSet, VariableType type) {
    BitSet result = new BitSet();
    for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i + 1)) {
      result.set(localToGlobal(i, type));
    }
    return result;
  }

  public ImmutableBitSet globalToLocal(BitSet bitSet) {
    BitSet result = new BitSet();
    for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i + 1)) {
      result.set(globalToLocal(i));
    }
    return ImmutableBitSet.copyOf(result);
  }

  public ImmutableBitSet globalToLocal(BitSet bitSet, VariableType type) {
    BitSet copy = BitSet2.copyOf(bitSet);
    copy.and(variables(type).copyInto(new BitSet()));
    return globalToLocal(copy);
  }
}
