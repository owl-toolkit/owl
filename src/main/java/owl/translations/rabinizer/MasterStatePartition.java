/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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

package owl.translations.rabinizer;

import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Table;
import de.tum.in.naturals.Indices;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import owl.automaton.Automaton;
import owl.automaton.algorithms.SccDecomposition;
import owl.collections.ValuationSet;
import owl.ltl.EquivalenceClass;

final class MasterStatePartition {
  final List<Set<EquivalenceClass>> sccs;
  final ImmutableTable<EquivalenceClass, EquivalenceClass, ValuationSet>
    outgoingTransitions;
  final Set<EquivalenceClass> transientStates;

  private MasterStatePartition(List<Set<EquivalenceClass>> sccs,
    Set<EquivalenceClass> transientStates,
    Table<EquivalenceClass, EquivalenceClass, ValuationSet> outgoingTransitions) {
    this.sccs = List.copyOf(sccs);
    this.transientStates = Set.copyOf(transientStates);
    this.outgoingTransitions = ImmutableTable.copyOf(outgoingTransitions);
  }

  public static MasterStatePartition create(Automaton<EquivalenceClass, ?> masterAutomaton) {
    // Determine the SCC decomposition and build the sub-automata separately
    List<Set<EquivalenceClass>> masterSccs = SccDecomposition.computeSccs(masterAutomaton);

    ImmutableTable.Builder<EquivalenceClass, EquivalenceClass, ValuationSet>
      outgoingTransitionsBuilder = ImmutableTable.builder();
    List<Set<EquivalenceClass>> sccListBuilder = new ArrayList<>();
    Set<EquivalenceClass> transientStatesBuilder = new HashSet<>();
    for (Set<EquivalenceClass> scc : masterSccs) {
      // Compute all outgoing transitions
      scc.forEach(state -> masterAutomaton.edgeMap(state).forEach((edge, valuations) -> {
        if (!scc.contains(edge.successor())) {
          outgoingTransitionsBuilder.put(state, edge.successor(), valuations);
        }
      }));
      if (SccDecomposition.isTransient(masterAutomaton::successors, scc)) {
        transientStatesBuilder.add(Iterables.getOnlyElement(scc));
      } else {
        sccListBuilder.add(Set.copyOf(scc));
      }
    }
    return new MasterStatePartition(sccListBuilder, transientStatesBuilder,
      outgoingTransitionsBuilder.build());
  }

  int partitionSize() {
    return sccs.size();
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder(32 + (40 * transientStates.size())
      + 100 * sccs.size());
    builder.append("Master state partitioning:");
    if (!transientStates.isEmpty()) {
      builder.append("\n  Transient: ").append(transientStates);
    }
    Indices.forEachIndexed(sccs, (index, element) ->
      builder.append("\n  ").append(index).append(": ").append(element));
    return builder.toString();
  }
}
