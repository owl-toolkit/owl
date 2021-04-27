package owl.automaton.symbolic;

@FunctionalInterface
public interface VariableAllocator {
  VariableAllocation allocate(int stateVariables, int atomicPropositions, int colours);
}
