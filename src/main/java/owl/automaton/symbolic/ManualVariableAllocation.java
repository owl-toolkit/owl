package owl.automaton.symbolic;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import owl.automaton.symbolic.SymbolicAutomaton.VariableType;

public class ManualVariableAllocation implements SymbolicAutomaton.VariableAllocation {

  private final List<VariableType> variables;

  @VisibleForTesting
  ManualVariableAllocation(VariableType... variables) {
    this(List.of(variables));
  }

  private ManualVariableAllocation(List<VariableType> variables) {
    this.variables = List.copyOf(variables);
  }

  @Override
  public BitSet variables(VariableType type) {
    BitSet bitset = new BitSet();
    for (int i = 0; i < variables.size(); i++) {
      if (variables.get(i) == type) {
        bitset.set(i);
      }
    }
    return bitset;
  }

  @Override
  public int numberOfVariables() {
    return variables.size();
  }

  @Override
  public VariableType typeOf(int variable) {
    return variables.get(variable);
  }

  @Override
  public List<String> variableNames() {
    List<String> result = new ArrayList<>(numberOfVariables());
    int state = 0;
    int colour = 0;
    int ap = 0;
    int suc = 0;
    for (int i = 0; i < numberOfVariables(); i++) {
      switch (typeOf(i)) {
        case STATE:
          result.add(String.format("s_%d", state++));
          break;
        case COLOUR:
          result.add(String.format("c_%d", colour++));
          break;
        case ATOMIC_PROPOSITION:
          result.add(String.format("ap_%d", ap++));
          break;
        case SUCCESSOR_STATE:
          result.add(String.format("x_%d", suc++));
          break;
        default:
          throw new AssertionError("Encountered unknown type " + typeOf(i));
      }
    }
    return result;
  }

  @Override
  public int localToGlobal(int variable, VariableType type) {
    int local = -1;
    for (int global = 0; global < numberOfVariables(); global++) {
      if (typeOf(global) == type) {
        local++;
      }
      if (local == variable) {
        return global;
      }
    }
    throw new IllegalArgumentException(variable + " is not a variable of type " + type);
  }

  @Override
  public int globalToLocal(int variable, VariableType type) {
    int j = 0;
    for (int i = 0; i < variable; i++) {
      if (typeOf(i) == type) {
        j++;
      }
    }
    return j;
  }
}
