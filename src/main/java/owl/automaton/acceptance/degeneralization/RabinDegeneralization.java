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

package owl.automaton.acceptance.degeneralization;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.common.primitives.ImmutableIntArray;
import de.tum.in.naturals.Indices;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterators;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import owl.automaton.AbstractImmutableAutomaton;
import owl.automaton.AnnotatedState;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.HashMapAutomaton;
import owl.automaton.MutableAutomaton;
import owl.automaton.MutableAutomatonUtil;
import owl.automaton.SuccessorFunction;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance.RabinPair;
import owl.automaton.acceptance.OmegaAcceptanceCast;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.algorithm.SccDecomposition;
import owl.automaton.edge.Edge;
import owl.collections.ValuationSet;
import owl.run.modules.OwlModule;
import owl.run.modules.OwlModule.AutomatonTransformer;

public final class RabinDegeneralization {

  public static final OwlModule<OwlModule.Transformer> MODULE = OwlModule.of(
    "dgra2dra",
    "Converts deterministic generalized Rabin automata into Rabin automata.",
    (commandLine, environment) ->
      AutomatonTransformer.of(RabinDegeneralization::degeneralize, GeneralizedRabinAcceptance.class)
  );

  private RabinDegeneralization() {}

  public static <S> Automaton<?, RabinAcceptance> degeneralize(
    Automaton<S, ? extends GeneralizedRabinAcceptance> automaton) {
    if (automaton.acceptance() instanceof RabinAcceptance) {
      return OmegaAcceptanceCast.cast(automaton, RabinAcceptance.class);
    }

    // Filter out the obviously irrelevant pairs
    List<RabinPair> trackedPairs = new ArrayList<>();
    List<RabinPair> noInfPairs = new ArrayList<>();

    // Generalized Rabin pair condition is Fin & /\ Inf(i), if the big AND is empty, it's true.
    // This means the condition translates to "don't visit the Fin set". Hence, as long as a
    // transition is not contained in the fin set, it's a good transition. We don't need to
    // track that index at all then and can save some space.

    automaton.acceptance().pairs().forEach(pair -> {
      if (pair.hasInfSet()) {
        trackedPairs.add(pair);
      } else {
        noInfPairs.add(pair);
      }
    });

    // General setup, allocate used pairs, the result automaton, etc.
    int trackedPairsCount = trackedPairs.size();
    int rabinCount = trackedPairsCount + noInfPairs.size();
    var rabinAcceptance = RabinAcceptance.of(rabinCount);

    // Arbitrary correspondence map for each original state
    Map<S, DegeneralizedRabinState<S>> stateMap = new HashMap<>();
    // Table containing all transient edges
    Table<DegeneralizedRabinState<S>, S, ValuationSet> transientEdgesTable =
      HashBasedTable.create();

    MutableAutomaton<DegeneralizedRabinState<S>, RabinAcceptance> resultAutomaton =
      HashMapAutomaton.of(rabinAcceptance, automaton.factory());

    var sccDecomposition = SccDecomposition.of(automaton);

    // Build the transition structure for each SCC separately
    for (Set<S> scc : sccDecomposition.sccs()) {
      if (sccDecomposition.isTransientScc(scc)) {
        // Transient SCCs never accept - ignore potential acceptance
        S state = Iterables.getOnlyElement(scc);
        assert !stateMap.containsKey(state);

        DegeneralizedRabinState<S> degeneralizedState = DegeneralizedRabinState.of(state);
        // This catches corner cases, where there are transient states with no successors
        resultAutomaton.addInitialState(degeneralizedState);
        stateMap.put(state, degeneralizedState);

        Map<S, ValuationSet> successors = transientEdgesTable.row(degeneralizedState);
        automaton.edgeMap(state).forEach((edge, valuations) ->
              successors.merge(edge.successor(), valuations, ValuationSet::union));
        continue;
      }

      // Determine the pairs which can accept in this SCC (i.e. those which have all their Inf in
      // this SCC)
      BitSet indices = AutomatonUtil.getAcceptanceSets(automaton, scc);
      IntList sccTrackedPairs = new IntArrayList(trackedPairsCount);
      Indices.forEachIndexed(trackedPairs, (pairIndex, pair) -> {
        assert pair.hasInfSet();
        if (IntIterators.all(pair.infSetIterator(), indices::get)) {
          sccTrackedPairs.add(pairIndex);
        }
      });

      assert sccTrackedPairs.size() <= trackedPairsCount;

      // Pick an arbitrary starting state for the exploration
      var initialSccState =
        DegeneralizedRabinState.of(scc.iterator().next(), new int[sccTrackedPairs.size()]);

      // This is a transient edge, add to the table and ignore it
      // The index of the next awaited inf set of each generalized pair in the successor
      // The acceptance on this edge. If a the Fin set of a generalized pair is encountered on
      // the original edge, this edge will have the corresponding Fin bit set. If otherwise an
      // Inf-breakpoint is reached, i.e. the awaited indices wrapped around for a particular
      // generalized pair, the corresponding Inf index will be set.
      // First handle the non-trivial case of pairs with Fin and Inf sets.
      // We have seen the fin set, put this transition into the fin set and restart
      // the wait
      // We did not see the fin set, check which inf sets have been seen
      // Check all inf sets of the rabin pair, starting from the awaited index.
      // We reached a breakpoint and can add the transition to the inf set
      // Deal with sets which have no Fin set separately

      Automaton<DegeneralizedRabinState<S>, RabinAcceptance> sourceAutomaton =
        new AbstractImmutableAutomaton.NonDeterministicEdgeMapAutomaton<>(resultAutomaton.factory(),
          Set.of(initialSccState), resultAutomaton.acceptance()) {

          @Override
          public Map<Edge<DegeneralizedRabinState<S>>, ValuationSet> edgeMap(
            DegeneralizedRabinState<S> state) {
            S generalizedState = state.state();
            Map<S, ValuationSet> transientSuccessors = transientEdgesTable.row(state);
            Map<Edge<DegeneralizedRabinState<S>>, ValuationSet> successors = new HashMap<>();

            automaton.edgeMap(generalizedState).forEach((edge, valuation) -> {
              S generalizedSuccessor = edge.successor();
              if (!scc.contains(generalizedSuccessor)) {
                // This is a transient edge, add to the table and ignore it
                transientSuccessors.merge(generalizedSuccessor, valuation, ValuationSet::union);
                return;
              }

              // The index of the next awaited inf set of each generalized pair in the successor
              int[] successorAwaitedIndices = new int[sccTrackedPairs.size()];

              // The acceptance on this edge. If a the Fin set of a generalized pair is
              // encountered on
              // the original edge, this edge will have the corresponding Fin bit set. If
              // otherwise an
              // Inf-breakpoint is reached, i.e. the awaited indices wrapped around for a
              // particular
              // generalized pair, the corresponding Inf index will be set.
              BitSet edgeAcceptance = new BitSet(rabinCount);

              // First handle the non-trivial case of pairs with Fin and Inf sets.
              for (int sccPairIndex = 0; sccPairIndex < sccTrackedPairs.size(); sccPairIndex++) {
                int currentPairIndex = sccTrackedPairs.getInt(sccPairIndex);
                RabinPair currentPair = trackedPairs.get(currentPairIndex);
                int awaitedInfSet = state.awaitedInfSet(sccPairIndex);

                if (edge.inSet(currentPair.finSet())) {
                  // We have seen the fin set, put this transition into the fin set and restart
                  // the wait
                  awaitedInfSet = 0;
                  edgeAcceptance.set(rabinAcceptance.pairs().get(currentPairIndex).finSet());
                } else {
                  // We did not see the fin set, check which inf sets have been seen
                  // Check all inf sets of the rabin pair, starting from the awaited index.
                  int infiniteIndexCount = currentPair.infSetCount();
                  int currentInfNumber = awaitedInfSet;
                  for (int i = 0; i < infiniteIndexCount; i++) {
                    currentInfNumber = (awaitedInfSet + i) % infiniteIndexCount;
                    int currentInfIndex = currentPair.infSet(currentInfNumber);
                    if (!edge.inSet(currentInfIndex)) {
                      break;
                    }

                    if (currentInfNumber == infiniteIndexCount - 1) {
                      // We reached a breakpoint and can add the transition to the inf set
                      edgeAcceptance.set(rabinAcceptance.pairs().get(currentPairIndex).infSet());
                    }
                  }
                  awaitedInfSet = currentInfNumber;
                }
                successorAwaitedIndices[sccPairIndex] = awaitedInfSet;
              }

              // Deal with sets which have no Fin set separately
              Indices.forEachIndexed(noInfPairs, (noInfIndex, pair) -> {
                int currentPairIndex = trackedPairsCount + noInfIndex;
                RabinPair currentPair = rabinAcceptance.pairs().get(currentPairIndex);

                edgeAcceptance.set(edge.inSet(pair.finSet())
                  ? currentPair.finSet()
                  : currentPair.infSet());
              });

              var successor =
                DegeneralizedRabinState.of(generalizedSuccessor, successorAwaitedIndices);
              successors.merge(Edge.of(successor, edgeAcceptance), valuation, ValuationSet::union);
            });

            return successors;
          }
        };

      MutableAutomatonUtil.copyInto(sourceAutomaton, resultAutomaton);

      var sccDecomposition2 = SccDecomposition.of(
        sourceAutomaton.states(),
        SuccessorFunction.filter(resultAutomaton, sourceAutomaton.states())
      );

      var sccs = sccDecomposition2.sccs();
      var resultBscc = sccs.stream()
        .filter(sccDecomposition2::isBottomScc)
        .findFirst().orElseThrow();

      // Mark some state of the result BSCC initial.
      if (!resultBscc.isEmpty()) {
        resultAutomaton.addInitialState(resultBscc.iterator().next());
      }

      var transientStates = Sets.difference(sourceAutomaton.states(), resultBscc);
      resultAutomaton.removeStateIf(transientStates::contains);
      resultAutomaton.trim();
      resultBscc.forEach(state -> stateMap.putIfAbsent(state.state(), state));
    }

    assert stateMap.keySet().equals(automaton.states());

    // Add transient edges
    transientEdgesTable.rowMap().forEach((state, successors) ->
      successors.forEach((generalizedSuccessor, valuations) -> {
        DegeneralizedRabinState<S> successor = stateMap.get(generalizedSuccessor);
        resultAutomaton.addState(state);
        resultAutomaton.addEdge(state, valuations, Edge.of(successor));
      }));

    // Set initial states
    resultAutomaton.initialStates(automaton.initialStates().stream()
      .map(stateMap::get)
      .collect(Collectors.toList()));
    resultAutomaton.trim();

    return resultAutomaton;
  }

  @AutoValue
  public abstract static class DegeneralizedRabinState<S> implements AnnotatedState<S> {
    @Override
    public abstract S state();

    abstract ImmutableIntArray awaitedSets();

    static <S> DegeneralizedRabinState<S> of(S state) {
      return new AutoValue_RabinDegeneralization_DegeneralizedRabinState<>(
        state, ImmutableIntArray.of());
    }

    static <S> DegeneralizedRabinState<S> of(S state, int[] awaitedSets) {
      return new AutoValue_RabinDegeneralization_DegeneralizedRabinState<>(
        state, ImmutableIntArray.copyOf(awaitedSets));
    }

    int awaitedInfSet(int generalizedPairIndex) {
      return awaitedSets().get(generalizedPairIndex);
    }

    @Override
    public abstract boolean equals(Object object);

    @Memoized
    @Override
    public abstract int hashCode();

    @Override
    public String toString() {
      return awaitedSets().isEmpty()
        ? String.format("{%s}", state())
        : String.format("{%s|%s}", state(), awaitedSets());
    }
  }
}
