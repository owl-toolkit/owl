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

package owl.automaton.acceptance.degeneralization;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.common.primitives.ImmutableIntArray;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import owl.automaton.AbstractMemoizingAutomaton;
import owl.automaton.AnnotatedState;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.HashMapAutomaton;
import owl.automaton.MutableAutomaton;
import owl.automaton.SuccessorFunction;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance.RabinPair;
import owl.automaton.acceptance.OmegaAcceptanceCast;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.algorithm.SccDecomposition;
import owl.automaton.edge.Edge;
import owl.bdd.BddSet;
import owl.collections.ImmutableBitSet;

public final class RabinDegeneralization {

  private RabinDegeneralization() {}

  public static <S> Automaton<?, ? extends RabinAcceptance> degeneralize(
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
    Table<DegeneralizedRabinState<S>, S, BddSet> transientEdgesTable =
      HashBasedTable.create();

    MutableAutomaton<DegeneralizedRabinState<S>, RabinAcceptance> resultAutomaton =
      HashMapAutomaton.create(automaton.atomicPropositions(), automaton.factory(), rabinAcceptance);

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

        Map<S, BddSet> successors = transientEdgesTable.row(degeneralizedState);
        automaton.edgeMap(state).forEach((edge, valuations) ->
              successors.merge(edge.successor(), valuations, BddSet::union));
        continue;
      }

      // Determine the pairs which can accept in this SCC (i.e. those which have all their Inf in
      // this SCC)
      ImmutableBitSet indices = AutomatonUtil.getAcceptanceSets(automaton, scc);
      List<Integer> sccTrackedPairs = new ArrayList<>(trackedPairsCount);
      for (int i = 0, s = trackedPairs.size(); i < s; i++) {
        assert trackedPairs.get(i).hasInfSet();
        if (trackedPairs.get(i).infSetStream().allMatch(indices::contains)) {
          sccTrackedPairs.add(i);
        }
      }

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
        new AbstractMemoizingAutomaton.EdgeMapImplementation<>(
          resultAutomaton.atomicPropositions(),
          resultAutomaton.factory(),
          Set.of(initialSccState),
          resultAutomaton.acceptance()) {

          @Override
          public Map<Edge<DegeneralizedRabinState<S>>, BddSet> edgeMapImpl(
            DegeneralizedRabinState<S> state) {

            S generalizedState = state.state();
            Map<S, BddSet> transientSuccessors = transientEdgesTable.row(state);
            Map<Edge<DegeneralizedRabinState<S>>, BddSet> successors = new HashMap<>();

            automaton.edgeMap(generalizedState).forEach((edge, valuation) -> {
              S generalizedSuccessor = edge.successor();
              if (!scc.contains(generalizedSuccessor)) {
                // This is a transient edge, add to the table and ignore it
                transientSuccessors.merge(generalizedSuccessor, valuation, BddSet::union);
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
                int currentPairIndex = sccTrackedPairs.get(sccPairIndex);
                RabinPair currentPair = trackedPairs.get(currentPairIndex);
                int awaitedInfSet = state.awaitedInfSet(sccPairIndex);

                if (edge.colours().contains(currentPair.finSet())) {
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
                    if (!edge.colours().contains(currentInfIndex)) {
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
              for (int i = 0, s = noInfPairs.size(); i < s; i++) {
                int currentPairIndex = trackedPairsCount + i;
                RabinPair currentPair = rabinAcceptance.pairs().get(currentPairIndex);
                edgeAcceptance.set(edge.colours().contains(noInfPairs.get(i).finSet())
                  ? currentPair.finSet()
                  : currentPair.infSet());
              }

              var successor =
                DegeneralizedRabinState.of(generalizedSuccessor, successorAwaitedIndices);
              successors.merge(Edge.of(successor, edgeAcceptance), valuation, BddSet::union);
            });

            return successors;
          }
        };

      resultAutomaton.addInitialState(initialSccState);
      // Use a work-list algorithm in case source is an on-the-fly generated automaton.
      var workList = new ArrayDeque<>(sourceAutomaton.initialStates());
      var visited = new HashSet<>(workList);

      while (!workList.isEmpty()) {
        var state1 = workList.remove();
        resultAutomaton.addState(state1);
        sourceAutomaton.edgeMap(state1).forEach((x, y) -> {
          resultAutomaton.addEdge(state1, y, x);
          if (visited.add(x.successor())) {
            workList.add(x.successor());
          }
        });
      }
      resultAutomaton.trim();

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
      .toList());
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
