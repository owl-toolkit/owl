/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
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
import java.util.List;
import java.util.Set;

public final class RangedVariableAllocator implements SymbolicAutomaton.VariableAllocator {

  private final List<SymbolicAutomaton.VariableAllocation.VariableType> order;

  public RangedVariableAllocator(SymbolicAutomaton.VariableAllocation.VariableType... order) {
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

    private RangedAllocation(int stateVariables, int atomicPropositions, int colours,
      List<SymbolicAutomaton.VariableAllocation.VariableType> order) {

      this.order = List.copyOf(order);
      fromIndexInclusive = new EnumMap<>(VariableType.class);
      toIndexExclusive = new EnumMap<>(VariableType.class);

      int fromIndex = 0;

      for (VariableType type : order) {
        fromIndexInclusive.put(type, fromIndex);

        switch (type) {
          case STATE:
          case SUCCESSOR_STATE:
            fromIndex = fromIndex + stateVariables;
            break;

          case COLOUR:
            fromIndex = fromIndex + colours;
            break;

          case ATOMIC_PROPOSITION:
            fromIndex = fromIndex + atomicPropositions;
            break;

          default:
            throw new AssertionError("Unreachable.");
        }

        toIndexExclusive.put(type, fromIndex);
      }
    }

    @Override
    public BitSet variables(VariableType type) {
      BitSet bitSet = new BitSet();
      bitSet.set(fromIndexInclusive.get(type), toIndexExclusive.get(type));
      return bitSet;
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
    public BitSet globalToLocal(BitSet bitSet, VariableType type) {
      BitSet localBitSet = new BitSet();

      int offset = fromIndexInclusive.get(type);

      // TODO: use BitSet.nextSetBit() for better performance.
      for (int i = offset, s = toIndexExclusive.get(type); i < s; i++) {
        localBitSet.set(i - offset, bitSet.get(i));
      }

      return localBitSet;
    }

    @Override
    public List<String> variableNames() {
      List<String> variablesNames = new ArrayList<>();

      for (VariableType type : order) {
        String prefix;
        switch (type) {
          case ATOMIC_PROPOSITION:
            prefix = "ap";
            break;

          case COLOUR:
            prefix = "c";
            break;

          case STATE:
            prefix = "x";
            break;

          case SUCCESSOR_STATE:
            prefix = "x'";
            break;

          default:
            throw new AssertionError("Unreachable.");
        }

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
