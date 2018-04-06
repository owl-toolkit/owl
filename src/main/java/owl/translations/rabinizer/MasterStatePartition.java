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
      scc.forEach(state -> masterAutomaton.forEachLabelledEdge(state, (edge, valuations) -> {
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
