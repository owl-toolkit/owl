package owl.automaton.symbolic;

import static owl.automaton.symbolic.SymbolicAutomaton.AllocationCombiner;
import static owl.automaton.symbolic.SymbolicAutomaton.VariableAllocation;
import static owl.automaton.symbolic.SymbolicAutomaton.VariableType;
import static owl.automaton.symbolic.SymbolicAutomaton.VariableType.ATOMIC_PROPOSITION;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Combines variable allocations in sequence and moves atomic propositions
 * to the front or the back. E.g. APs, allocation 0, allocation 1, ...
 * or allocation 0, allocation 1, ..., APs
 */
public class SequentialVariableAllocationCombiner implements AllocationCombiner {

  private final List<? extends VariableAllocation> allocations;
  private final int[] offsets;
  private final int nrOfAps;
  private final boolean startWithAtomicPropositions;

  SequentialVariableAllocationCombiner(
    List<? extends VariableAllocation> allocations,
    boolean startWithAtomicPropositions) {

    this.allocations = List.copyOf(allocations);
    Preconditions.checkArgument(!this.allocations.isEmpty());

    this.offsets = new int[this.allocations.size()];
    this.startWithAtomicPropositions = startWithAtomicPropositions;
    this.nrOfAps = this.allocations
      .get(0)
      .variables(ATOMIC_PROPOSITION)
      .cardinality();
    Preconditions.checkArgument(this.allocations.stream()
      .allMatch(allocation -> allocation.variables(ATOMIC_PROPOSITION).cardinality() == nrOfAps));
    offsets[0] = startWithAtomicPropositions ? nrOfAps : 0;

    for (int i = 0; i < this.allocations.size() - 1; i++) {
      var allocation = this.allocations.get(i);
      assert allocation.variables(ATOMIC_PROPOSITION).cardinality() == nrOfAps;
      offsets[i + 1] = offsets[i] + allocation.numberOfVariables() - nrOfAps;
    }
  }

  SequentialVariableAllocationCombiner(List<? extends VariableAllocation> allocations) {
    this(allocations, true);
  }

  @Override
  public List<String> variableNames() {
    List<String> names = new ArrayList<>(numberOfVariables());
    if (startWithAtomicPropositions) {
      for (int i = 0; i < nrOfAps; i++) {
        names.add(String.format("ap_%d", i));
      }
    }
    for (int i = 0; i < allocations.size(); i++) {
      var allocation = allocations.get(i);
      BitSet aps = allocation.variables(ATOMIC_PROPOSITION);
      List<String> localNames = allocation.variableNames();
      for (int j = aps.nextClearBit(0);
           j < allocation.numberOfVariables(); j = aps.nextClearBit(j + 1)) {
        names.add(String.format("%d:%s", i, localNames.get(j)));
      }
    }
    if (!startWithAtomicPropositions) {
      for (int i = 0; i < nrOfAps; i++) {
        names.add(String.format("ap_%d", i));
      }
    }
    return names;
  }

  @Override
  public int localToGlobal(int variable, VariableType type) {
    if (type == ATOMIC_PROPOSITION) {
      return startWithAtomicPropositions ? variable : numberOfVariables() - nrOfAps + variable;
    }
    int ctr = 0;
    for (int i = 0; i < allocations.size(); i++) {
      var allocation = allocations.get(i);
      int nrOfVariablesInAllocation = allocation.variables(type).cardinality();
      if (ctr + nrOfVariablesInAllocation > variable) {
        int allocationLocal = allocation.localToGlobal(variable - ctr, type);
        return offsets[i] + withoutAtomicPropositions(allocation, allocationLocal);
      }
      ctr += nrOfVariablesInAllocation;
    }
    throw new AssertionError("Unreachable");
  }

  @Override
  public int globalToLocal(int variable, VariableType type) {
    if (type == ATOMIC_PROPOSITION) {
      return variable - (startWithAtomicPropositions ? 0 : numberOfVariables() - nrOfAps);
    }
    for (int i = 0; i < allocations.size(); i++) {
      if (offsets[i] <= variable && ((i == offsets.length - 1) || offsets[i + 1] > variable)) {
        var allocation = allocations.get(i);
        int allocationLocalVariable = withAtomicPropositions(allocation, variable - offsets[i]);
        int offset = IntStream.range(0, i)
          .map(j -> allocations.get(j).variables(type).cardinality())
          .reduce(0, Integer::sum);
        return offset + allocation.globalToLocal(allocationLocalVariable, type);
      }
    }
    throw new AssertionError("Unreachable");
  }

  @Override
  public BitSet variables(VariableType type) {
    BitSet bitset = new BitSet();
    if (type == ATOMIC_PROPOSITION) {
      bitset.set(startWithAtomicPropositions ? 0 : numberOfVariables() - nrOfAps,
        startWithAtomicPropositions ? nrOfAps : numberOfVariables());
    } else {
      for (var allocation : allocations) {
        bitset.or(localToGlobal(allocation.variables(type), allocation));
      }
    }
    return bitset;
  }

  @Override
  public int numberOfVariables() {
    return offsets[offsets.length - 1]
      + allocations.get(offsets.length - 1).numberOfVariables()
      - (startWithAtomicPropositions ? nrOfAps : 0);
  }

  @Override
  public int localToGlobal(int variable, VariableAllocation allocation) {
    if (allocation.typeOf(variable) == ATOMIC_PROPOSITION) {
      return (startWithAtomicPropositions ? 0 : numberOfVariables() - nrOfAps)
        + getNrOfSetBitsUntilVariable(allocation.variables(ATOMIC_PROPOSITION), variable);
    } else {
      return offsets[allocations.indexOf(allocation)] + withoutAtomicPropositions(allocation,
        variable);
    }
  }

  @Override
  public int globalToLocal(int variable, VariableAllocation allocation) {
    throw new UnsupportedOperationException("Not implemented");
  }

  private static int getNrOfSetBitsUntilVariable(BitSet bitSet, int var) {
    int i = 0;
    for (int j = bitSet.nextSetBit(0); j >= 0; j = bitSet.nextSetBit(j + 1)) {
      if (j >= var) {
        break;
      }
      i++;
    }
    return i;
  }

  private static int withAtomicPropositions(VariableAllocation allocation, int var) {

    BitSet aps = allocation.variables(ATOMIC_PROPOSITION);
    int i = 0;
    int j = -1;
    while (i <= var) {
      i++;
      j = aps.nextClearBit(j + 1);
    }
    return j;
  }

  private static int withoutAtomicPropositions(VariableAllocation allocation, int var) {

    BitSet aps = allocation.variables(ATOMIC_PROPOSITION);
    assert !aps.get(var);
    return var - getNrOfSetBitsUntilVariable(aps, var);
  }
}
