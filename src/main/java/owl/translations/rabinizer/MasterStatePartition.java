package owl.translations.rabinizer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Table;
import java.util.List;
import java.util.Set;
import owl.automaton.Automaton;
import owl.automaton.algorithms.SccDecomposition;
import owl.automaton.edge.Edge;
import owl.collections.Collections3;
import owl.collections.ValuationSet;
import owl.ltl.EquivalenceClass;

final class MasterStatePartition {
  final ImmutableList<Set<EquivalenceClass>> sccs;
  final ImmutableTable<EquivalenceClass, EquivalenceClass, ValuationSet>
    outgoingTransitions;
  final ImmutableSet<EquivalenceClass> transientStates;

  private MasterStatePartition(List<Set<EquivalenceClass>> sccs,
    Set<EquivalenceClass> transientStates,
    Table<EquivalenceClass, EquivalenceClass, ValuationSet> outgoingTransitions) {
    this.sccs = ImmutableList.copyOf(sccs);
    this.transientStates = ImmutableSet.copyOf(transientStates);
    this.outgoingTransitions = ImmutableTable.copyOf(outgoingTransitions);
  }

  public static MasterStatePartition create(Automaton<EquivalenceClass, ?> masterAutomaton) {
    // Determine the SCC decomposition and build the sub-automata separately
    List<Set<EquivalenceClass>> masterSccs = SccDecomposition.computeSccs(masterAutomaton);

    ImmutableTable.Builder<EquivalenceClass, EquivalenceClass, ValuationSet>
      outgoingTransitionsBuilder = ImmutableTable.builder();
    ImmutableList.Builder<Set<EquivalenceClass>> sccListBuilder = ImmutableList.builder();
    ImmutableSet.Builder<EquivalenceClass> transientStatesBuilder = ImmutableSet.builder();
    for (Set<EquivalenceClass> scc : masterSccs) {
      // Compute all outgoing transitions
      scc.forEach(state -> masterAutomaton.getLabelledEdges(state).forEach(labelledEdge -> {
        Edge<EquivalenceClass> edge = labelledEdge.getEdge();
        if (!scc.contains(edge.getSuccessor())) {
          ValuationSet valuations = labelledEdge.getValuations();
          outgoingTransitionsBuilder.put(state, edge.getSuccessor(), valuations);
        }
      }));
      if (SccDecomposition.isTransient(masterAutomaton::getSuccessors, scc)) {
        transientStatesBuilder.add(Iterables.getOnlyElement(scc));
      } else {
        sccListBuilder.add(ImmutableSet.copyOf(scc));
      }
    }
    return new MasterStatePartition(sccListBuilder.build(), transientStatesBuilder.build(),
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
    Collections3.forEachIndexed(sccs, (index, element) ->
      builder.append("\n  ").append(index).append(": ").append(element));
    return builder.toString();
  }
}
