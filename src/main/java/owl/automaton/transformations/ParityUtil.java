/*
 * Copyright (C) 2016  (See AUTHORS)
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

package owl.automaton.transformations;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.Collections2;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.PrimitiveIterator;
import java.util.Set;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import owl.automaton.AutomatonUtil;
import owl.automaton.MutableAutomaton;
import owl.automaton.Views.ForwardingMutableAutomaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.ParityAcceptance.Priority;
import owl.automaton.algorithms.SccDecomposition;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.automaton.edge.LabelledEdge;
import owl.automaton.minimizations.GenericMinimizations;
import owl.collections.ValuationSet;

public final class ParityUtil {
  private ParityUtil() {
  }

  public static <S> void complement(MutableAutomaton<S, ParityAcceptance> automaton,
    Supplier<S> sinkSupplier) {
    BitSet rejectingAcceptance = new BitSet();
    ParityAcceptance parityCondition = automaton.getAcceptance();

    if (parityCondition.getAcceptanceSets() < 2) {
      parityCondition.setAcceptanceSets(2);
    }

    if (parityCondition.getPriority() == Priority.EVEN) {
      rejectingAcceptance.set(1);
    } else {
      rejectingAcceptance.set(0);
    }

    AutomatonUtil.complete(automaton, sinkSupplier, () -> rejectingAcceptance);
    automaton.getAcceptance().complement();
  }

  public static <S> MutableAutomaton<S, ParityAcceptance> minimizePriorities(
    MutableAutomaton<S, ParityAcceptance> automaton) {
    GenericMinimizations.removeTransientAcceptance(automaton);
    return minimizePriorities(automaton, SccDecomposition.computeSccs(automaton, false));
  }

  private static <S> MutableAutomaton<S, ParityAcceptance> minimizePriorities(
    MutableAutomaton<S, ParityAcceptance> automaton, List<Set<S>> sccs) {
    if (automaton instanceof ForwardingMutableAutomaton) {
      return automaton;
    }

    /* This optimization simply determines all priorities used in each SCC and then tries to
     * eliminate "gaps". For example, when [0, 2, 4, 5] are used, we actually only need to consider
     * [0, 1]. Furthermore, edges between SCCs are set to an arbitrary priority. */

    ParityAcceptance acceptance = automaton.getAcceptance();
    int acceptanceSets = acceptance.getAcceptanceSets();
    // Gather the priorities used _after_ the reduction - cheap and can be used for verification
    BitSet globallyUsedPriorities = new BitSet(acceptanceSets);

    // Construct the mapping for the priorities in this map
    Int2IntMap reductionMapping = new Int2IntOpenHashMap();
    reductionMapping.defaultReturnValue(-1);
    // Priorities used in each SCC
    BitSet usedPriorities = new BitSet(acceptanceSets);
    int usedAcceptanceSets = 0;

    for (Set<S> scc : sccs) {
      reductionMapping.clear();
      usedPriorities.clear();

      // Determine the used priorities
      for (S state : scc) {
        for (Edge<S> edge : automaton.getEdges(state)) {
          if (scc.contains(edge.getSuccessor())) {
            PrimitiveIterator.OfInt acceptanceSetIterator = edge.acceptanceSetIterator();
            if (acceptanceSetIterator.hasNext()) {
              usedPriorities.set(acceptanceSetIterator.nextInt());
            }
          }
        }
      }

      // All priorities are used, can't collapse any
      if (usedPriorities.cardinality() == acceptanceSets) {
        usedAcceptanceSets = Math.max(usedAcceptanceSets, acceptanceSets);
        continue;
      }

      // Construct the mapping
      int currentPriority = usedPriorities.nextSetBit(0);
      int currentTarget = currentPriority % 2;

      while (currentPriority != -1) {
        if (currentTarget % 2 != currentPriority % 2) {
          currentTarget += 1;
        }

        reductionMapping.put(currentPriority, currentTarget);
        globallyUsedPriorities.set(currentTarget);
        usedAcceptanceSets = Math.max(usedAcceptanceSets, currentTarget + 1);
        currentPriority = usedPriorities.nextSetBit(currentPriority + 1);
      }

      // This remaps _all_ outgoing edges of the states in the SCC - including transient edges.
      // Since these are only taken finitely often by any run, their value does not matter.
      automaton.remapEdges(scc, (state, edge) -> Edges.remapAcceptance(edge, reductionMapping));
    }

    automaton.getAcceptance().setAcceptanceSets(usedAcceptanceSets);
    return automaton;
  }

  public static <S> MutableAutomaton<S, ParityAcceptance> viewAsParity(
    MutableAutomaton<S, BuchiAcceptance> automaton) {
    return new WrappedBuchiAutomaton<>(automaton);
  }

  // TODO Complementing these automata feels a bit iffy right now: The priority type of the
  // acceptance is changed without notifying this automaton of it.
  private static final class WrappedBuchiAutomaton<S>
    extends
    ForwardingMutableAutomaton<S, ParityAcceptance, BuchiAcceptance> {
    private final ParityAcceptance acceptance;

    WrappedBuchiAutomaton(MutableAutomaton<S, BuchiAcceptance> backingAutomaton) {
      super(backingAutomaton);
      acceptance = new ParityAcceptance(2, Priority.EVEN);
    }

    @Override
    public void addEdge(S source, Edge<? extends S> edge) {
      super.addEdge(source, convertParityToBuchi(edge));
    }

    @Override
    public void addEdge(S source, BitSet valuation, Edge<? extends S> edge) {
      super.addEdge(source, valuation, convertParityToBuchi(edge));
    }

    @Override
    public void addEdge(S source, ValuationSet valuations, Edge<? extends S> edge) {
      super.addEdge(source, valuations, convertParityToBuchi(edge));
    }

    @SuppressWarnings("unchecked")
    private Edge<S> convertBuchiToParity(Edge<? extends S> edge) {
      checkState(isAcceptanceCompatible());

      return edge.inSet(0) ? (Edge<S>) edge : Edges.create(edge.getSuccessor(), 1);
    }

    private Edge<S> convertParityToBuchi(Edge<? extends S> edge) {
      checkState(isAcceptanceCompatible());
      return edge.inSet(0)
        ? Edges.create(edge.getSuccessor(), 0)
        : Edges.create(edge.getSuccessor());
    }

    @Override
    public ParityAcceptance getAcceptance() {
      return acceptance;
    }

    @Override
    public Collection<LabelledEdge<S>> getLabelledEdges(S state) {
      //noinspection ConstantConditions
      return Collections2.transform(super.getLabelledEdges(state), labelledEdge ->
        LabelledEdge.of(convertBuchiToParity(labelledEdge.edge), labelledEdge.valuations));
    }

    @Nullable
    @Override
    public S getSuccessor(S state, BitSet valuation) {
      return automaton.getSuccessor(state, valuation);
    }

    @Override
    public Set<S> getSuccessors(S state) {
      return automaton.getSuccessors(state);
    }

    private boolean isAcceptanceCompatible() {
      return acceptance.getAcceptanceSets() == 2;
    }
  }
}
