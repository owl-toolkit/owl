package owl.translations.rabinizer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Table;
import java.util.List;
import java.util.Set;
import owl.algorithms.SccAnalyser;
import owl.automaton.Automaton;
import owl.automaton.edge.Edge;
import owl.collections.Lists2;
import owl.collections.ValuationSet;
import owl.ltl.EquivalenceClass;

final class MasterStatePartition {
  final ImmutableList<Set<EquivalenceClass>> partition;
  final ImmutableTable<EquivalenceClass, EquivalenceClass, ValuationSet>
    partitionOutgoingTransitions;
  final ImmutableSet<EquivalenceClass> transientStates;

  private MasterStatePartition(List<Set<EquivalenceClass>> partition,
    Set<EquivalenceClass> transientStates,
    Table<EquivalenceClass, EquivalenceClass, ValuationSet> partitionOutgoingTransitions) {
    this.partition = ImmutableList.copyOf(partition);
    this.transientStates = ImmutableSet.copyOf(transientStates);
    this.partitionOutgoingTransitions = ImmutableTable.copyOf(partitionOutgoingTransitions);
  }

  public static MasterStatePartition create(Automaton<EquivalenceClass, ?> masterAutomaton) {
    // Determine the SCC decomposition and build the sub-automata separately
    List<Set<EquivalenceClass>> masterSccs = SccAnalyser.computeSccs(masterAutomaton);

    ImmutableTable.Builder<EquivalenceClass, EquivalenceClass, ValuationSet>
      partitionOutgoingTransitionsBuilder = ImmutableTable.builder();
    Builder<Set<EquivalenceClass>> partitionBuilder = ImmutableList.builder();
    ImmutableSet.Builder<EquivalenceClass> transientStatesBuilder = ImmutableSet.builder();
    for (Set<EquivalenceClass> scc : masterSccs) {
      // Compute all outgoing transitions
      scc.forEach(state -> masterAutomaton.getLabelledEdges(state).forEach(labelledEdge -> {
        Edge<EquivalenceClass> edge = labelledEdge.getEdge();
        if (!scc.contains(edge.getSuccessor())) {
          ValuationSet valuations = labelledEdge.getValuations();
          partitionOutgoingTransitionsBuilder.put(state, edge.getSuccessor(), valuations);
        }
      }));
      if (SccAnalyser.isTransient(masterAutomaton::getSuccessors, scc)) {
        transientStatesBuilder.add(Iterables.getOnlyElement(scc));
      } else {
        partitionBuilder.add(ImmutableSet.copyOf(scc));
      }
    }
    return new MasterStatePartition(partitionBuilder.build(), transientStatesBuilder.build(),
      partitionOutgoingTransitionsBuilder.build());
  }

  int partitionSize() {
    return partition.size();
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder(32 + (40 * transientStates.size())
      + 100 * partition.size());
    builder.append("Master state partitioning:");
    if (!transientStates.isEmpty()) {
      builder.append("\n  Transient: ").append(transientStates);
    }
    Lists2.forAllIndexed(partition, (index, element) ->
      builder.append("\n  ").append(index).append(": ").append(element));
    return builder.toString();
  }
}
