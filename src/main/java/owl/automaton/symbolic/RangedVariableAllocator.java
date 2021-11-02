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

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import owl.automaton.symbolic.SymbolicAutomaton.VariableType;
import owl.collections.ImmutableBitSet;

public final class RangedVariableAllocator implements SymbolicAutomaton.VariableAllocator {

  private final List<VariableType> order;

  public RangedVariableAllocator(VariableType... order) {
    this.order = List.of(order);
    Preconditions.checkArgument(Set.copyOf(this.order).size() == 4);
  }

  @Override
  public SymbolicAutomaton.VariableAllocation allocate(
    int stateVariables, int atomicPropositions, int colours) {

    return new RangedAllocation(stateVariables, atomicPropositions, colours, order);
  }

  private static class RangedAllocation implements SymbolicAutomaton.VariableAllocation {

    private final List<VariableType> order;
    private final EnumMap<VariableType, Integer> fromIndexInclusive;
    private final EnumMap<VariableType, Integer> toIndexExclusive;
    private final Map<Set<VariableType>, ImmutableBitSet> variables;
    private final int size;

    private RangedAllocation(int stateVariables, int atomicPropositions, int colours,
      List<VariableType> order) {

      this.order = List.copyOf(order);
      this.variables = new HashMap<>();
      this.size = 2 * stateVariables + atomicPropositions + colours;
      fromIndexInclusive = new EnumMap<>(VariableType.class);
      toIndexExclusive = new EnumMap<>(VariableType.class);

      int fromIndex = 0;

      for (VariableType type : order) {
        fromIndexInclusive.put(type, fromIndex);

        fromIndex = switch (type) {
          case STATE, SUCCESSOR_STATE -> fromIndex + stateVariables;
          case COLOUR -> fromIndex + colours;
          case ATOMIC_PROPOSITION -> fromIndex + atomicPropositions;
        };

        toIndexExclusive.put(type, fromIndex);
      }
    }

    @Override
    public ImmutableBitSet variables(VariableType... types) {
      return variables.computeIfAbsent(Set.of(types), variableTypes -> {
        BitSet bitSet = new BitSet();
        for (var type : variableTypes) {
          bitSet.set(fromIndexInclusive.get(type), toIndexExclusive.get(type));
        }
        return ImmutableBitSet.copyOf(bitSet);
      });
    }

    @Override
    public int numberOfVariables() {
      return size;
    }

    @Override
    public VariableType typeOf(int variable) {
      Objects.checkIndex(variable, numberOfVariables());
      for (VariableType type : VariableType.values()) {
        if (fromIndexInclusive.get(type) <= variable && toIndexExclusive.get(type) > variable) {
          return type;
        }
      }
      throw new AssertionError("Unreachable");
    }

    @Override
    public BitSet localToGlobal(BitSet bitSet, VariableType type) {
      BitSet globalBitSet = new BitSet();

      int offset = fromIndexInclusive.get(type);
      int size = toIndexExclusive.get(type) - offset;

      // TODO: use BitSet.nextSetBit() for better performance.
      for (int i = 0; i < size; i++) {
        globalBitSet.set(i + offset, bitSet.get(i));
      }

      return globalBitSet;
    }

    @Override
    public ImmutableBitSet globalToLocal(BitSet bitSet, VariableType type) {
      BitSet localBitSet = new BitSet();

      int offset = fromIndexInclusive.get(type);

      // TODO: use BitSet.nextSetBit() for better performance.
      for (int i = offset, s = toIndexExclusive.get(type); i < s; i++) {
        localBitSet.set(i - offset, bitSet.get(i));
      }

      return ImmutableBitSet.copyOf(localBitSet);
    }

    @Override
    public List<String> variableNames() {
      List<String> variablesNames = new ArrayList<>();

      for (VariableType type : order) {
        String prefix = switch (type) {
          case ATOMIC_PROPOSITION -> "ap";
          case COLOUR -> "c";
          case STATE -> "x";
          case SUCCESSOR_STATE -> "x'";
        };

        int variables = toIndexExclusive.get(type) - fromIndexInclusive.get(type);

        for (int x = 0; x < variables; x++) {
          variablesNames.add(prefix + '_' + x);
        }
      }

      return variablesNames;
    }

    @Override
    public int localToGlobal(int variable, VariableType type) {
      return variable + fromIndexInclusive.get(type);
    }

    @Override
    public int globalToLocal(int variable, VariableType type) {
      return variable - fromIndexInclusive.get(type);
    }
  }
}
