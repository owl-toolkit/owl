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

import static owl.automaton.symbolic.SymbolicAutomaton.AllocationCombiner;
import static owl.automaton.symbolic.SymbolicAutomaton.VariableAllocation;
import static owl.automaton.symbolic.SymbolicAutomaton.VariableType;
import static owl.automaton.symbolic.SymbolicAutomaton.VariableType.ATOMIC_PROPOSITION;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import owl.collections.BitSet2;
import owl.collections.ImmutableBitSet;

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
  private final Map<Set<VariableType>, ImmutableBitSet> variables;

  SequentialVariableAllocationCombiner(
    List<? extends VariableAllocation> allocations,
    boolean startWithAtomicPropositions) {

    this.allocations = List.copyOf(allocations);
    Preconditions.checkArgument(!this.allocations.isEmpty());

    this.offsets = new int[this.allocations.size()];
    this.startWithAtomicPropositions = startWithAtomicPropositions;
    this.variables = new HashMap<>();
    this.nrOfAps = this.allocations
      .get(0)
      .variables(ATOMIC_PROPOSITION)
      .size();
    Preconditions.checkArgument(this.allocations.stream()
      .allMatch(allocation -> allocation.variables(ATOMIC_PROPOSITION).size() == nrOfAps));
    offsets[0] = startWithAtomicPropositions ? nrOfAps : 0;

    for (int i = 0; i < this.allocations.size() - 1; i++) {
      var allocation = this.allocations.get(i);
      assert allocation.variables(ATOMIC_PROPOSITION).size() == nrOfAps;
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
      BitSet aps = BitSet2.copyOf(allocation.variables(ATOMIC_PROPOSITION));
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
      int nrOfVariablesInAllocation = allocation.variables(type).size();
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
          .map(j -> allocations.get(j).variables(type).size())
          .reduce(0, Integer::sum);
        return offset + allocation.globalToLocal(allocationLocalVariable, type);
      }
    }
    throw new AssertionError("Unreachable");
  }

  @Override
  public ImmutableBitSet variables(VariableType... types) {
    Set<VariableType> typeSet = EnumSet.copyOf(Arrays.asList(types));
    return variables.computeIfAbsent(typeSet, variableTypes -> {
      BitSet bitSet = new BitSet();
      for (VariableType type : typeSet) {
        if (type == ATOMIC_PROPOSITION) {
          bitSet.set(startWithAtomicPropositions ? 0 : numberOfVariables() - nrOfAps,
            startWithAtomicPropositions ? nrOfAps : numberOfVariables());
        } else {
          for (var allocation : allocations) {
            bitSet.or(localToGlobal(allocation.variables(type).copyInto(new BitSet()), allocation));
          }
        }
      }
      return ImmutableBitSet.copyOf(bitSet);
    });
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

  private static int getNrOfSetBitsUntilVariable(ImmutableBitSet bitSet, int var) {
    int i = 0;
    for (var it = bitSet.intIterator(); it.hasNext(); ) {
      if (it.nextInt() >= var) {
        break;
      }
      i++;
    }
    return i;
  }

  private static int withAtomicPropositions(VariableAllocation allocation, int var) {
    BitSet aps = allocation.variables(ATOMIC_PROPOSITION).copyInto(new BitSet());
    int i = 0;
    int j = -1;
    while (i <= var) {
      i++;
      j = aps.nextClearBit(j + 1);
    }
    return j;
  }

  private static int withoutAtomicPropositions(VariableAllocation allocation, int var) {
    ImmutableBitSet aps = allocation.variables(ATOMIC_PROPOSITION);
    assert !aps.contains(var);
    return var - getNrOfSetBitsUntilVariable(aps, var);
  }
}
