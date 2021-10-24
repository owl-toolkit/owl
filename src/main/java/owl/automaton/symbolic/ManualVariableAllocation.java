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

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import owl.automaton.symbolic.SymbolicAutomaton.VariableType;
import owl.collections.ImmutableBitSet;

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
  public ImmutableBitSet variables(VariableType... types) {
    BitSet bitset = new BitSet();
    Set<VariableType> typeSet = EnumSet.copyOf(Arrays.asList(types));
    for (int i = 0; i < variables.size(); i++) {
      if (typeSet.contains(variables.get(i))) {
        bitset.set(i);
      }
    }
    return ImmutableBitSet.copyOf(bitset);
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
        case STATE -> result.add(String.format("s_%d", state++));
        case COLOUR -> result.add(String.format("c_%d", colour++));
        case ATOMIC_PROPOSITION -> result.add(String.format("ap_%d", ap++));
        case SUCCESSOR_STATE -> result.add(String.format("x_%d", suc++));
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
