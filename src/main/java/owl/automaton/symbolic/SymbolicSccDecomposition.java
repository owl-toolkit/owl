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

import static com.google.common.base.Preconditions.checkArgument;
import static owl.automaton.symbolic.SymbolicAutomaton.VariableType.COLOUR;
import static owl.automaton.symbolic.SymbolicAutomaton.VariableType.STATE;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.List;
import owl.bdd.BddSet;

@AutoValue
public abstract class SymbolicSccDecomposition {

  public abstract SymbolicAutomaton<?> automaton();

  public static SymbolicSccDecomposition of(SymbolicAutomaton<?> automaton) {
    return new AutoValue_SymbolicSccDecomposition(automaton);
  }

  /**
   * Computes the scc decomposition of the automaton using the algorithm
   * described in https://doi.org/10.1007/s10703-006-4341-z, where the acceptance condition
   * is included in the states. The returned BDDs therefore only contain state and colour variables
   *
   * @param restrictedTo a BddSet representing the state-space for which the SCC decomposition
   *     is computed. Only states in restrictedTo are included in the result.
   *
   * @return A list of BddSets representing the SCCs of the automaton.
   */
  public List<BddSet> sccs(BddSet restrictedTo) {
    if (restrictedTo.isEmpty()) {
      return List.of();
    }
    BddSet transitionRelation = automaton().transitionRelation();
    SymbolicAutomaton.VariableAllocation variableAllocation = automaton().variableAllocation();
    BitSet states = variableAllocation.variables(STATE, COLOUR).copyInto(new BitSet());
    checkArgument(restrictedTo.factory() == transitionRelation.factory());
    Deque<BddSet> worklist = new ArrayDeque<>();
    worklist.push(restrictedTo);
    List<BddSet> sccs = new ArrayList<>();
    while (!worklist.isEmpty()) {
      BddSet consideredStates = worklist.pop();
      // Get some arbitrary state from consideredStates
      BddSet node = transitionRelation
        .factory()
        .of(consideredStates.element().orElseThrow(), states);
      BddSet forwardSet = node;
      BddSet backwardSet = node;
      BddSet successors = node;
      BddSet predecessors = node;
      // Compute successors and predecessors in lock-step
      while (!successors.isEmpty() && !predecessors.isEmpty()) {
        successors = automaton().successors(successors).intersection(consideredStates)
          .intersection(forwardSet.complement());
        predecessors = automaton().predecessors(predecessors).intersection(consideredStates)
          .intersection(backwardSet.complement());
        forwardSet = forwardSet.union(successors);
        backwardSet = backwardSet.union(predecessors);
      }
      BddSet converged;
      // Refine forwardset or backwardset
      if (successors.isEmpty()) {
        converged = forwardSet;
        while (!predecessors.intersection(forwardSet).isEmpty()) {
          predecessors = automaton().predecessors(predecessors).intersection(consideredStates)
            .intersection(backwardSet.complement());
          backwardSet = backwardSet.union(predecessors);
        }
      } else {
        converged = backwardSet;
        while (!successors.intersection(backwardSet).isEmpty()) {
          successors = automaton().successors(successors).intersection(consideredStates)
            .intersection(forwardSet.complement());
          forwardSet = forwardSet.union(successors);
        }
      }
      // Save the SCC
      BddSet scc = forwardSet.intersection(backwardSet);
      sccs.add(scc);
      // Continue with the "recursive" steps of the algorithm
      BddSet next1 = converged.intersection(scc.complement());
      BddSet next2 = consideredStates.intersection(converged.complement());
      if (!next1.isEmpty()) {
        worklist.push(next1);
      }
      if (!next2.isEmpty()) {
        worklist.push(next2);
      }
    }
    return sccs;
  }

  @Memoized
  public List<BddSet> sccs() {
    return sccs(automaton().reachableStates());
  }

  public boolean isTrivialScc(BddSet scc) {
    return automaton().successors(scc).intersection(scc).isEmpty();
  }
}
