package owl.automaton.symbolic;

@FunctionalInterface
public interface VariableAllocationCombiner {
  VariableAllocationCombination combine(VariableAllocation... allocation);
}
