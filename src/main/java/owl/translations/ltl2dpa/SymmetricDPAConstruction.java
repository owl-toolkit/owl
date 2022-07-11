/*
 * Copyright (C) 2018, 2022  (Salomon Sickert)
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

package owl.translations.ltl2dpa;

import static java.util.Map.entry;

import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import owl.automaton.AbstractMemoizingAutomaton;
import owl.automaton.Automaton;
import owl.automaton.EmptyAutomaton;
import owl.automaton.SingletonAutomaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.ParityAcceptance.Parity;
import owl.automaton.algorithm.SccDecomposition;
import owl.automaton.edge.Edge;
import owl.ltl.EquivalenceClass;
import owl.ltl.LabelledFormula;
import owl.ltl.SyntacticFragments;
import owl.translations.ltl2ldba.AnnotatedLDBA;
import owl.translations.ltl2ldba.SymmetricLDBAConstruction;
import owl.translations.ltl2ldba.SymmetricProductState;
import owl.translations.mastertheorem.SymmetricEvaluatedFixpoints;

final class SymmetricDPAConstruction {

  private static final SymmetricLDBAConstruction<BuchiAcceptance> LDBA_CONSTRUCTION
      = SymmetricLDBAConstruction.of(BuchiAcceptance.class);

  Automaton<SymmetricRankingState, ParityAcceptance> of(
      LabelledFormula labelledFormula, boolean complete) {

    var ldba = LDBA_CONSTRUCTION.apply(labelledFormula);
    var atomicPropositions = ldba.acceptingComponent().atomicPropositions();

    if (ldba.initialComponent().initialStates().isEmpty()) {
      var acceptance = new ParityAcceptance(3, Parity.MIN_ODD);

      return complete
          ? SingletonAutomaton.of(
          atomicPropositions,
          SymmetricRankingState.of(Map.of()),
          acceptance,
          acceptance.rejectingSet().orElseThrow())
          : EmptyAutomaton.of(
              atomicPropositions,
              acceptance);
    }

    var builder = new SymmetricDPAConstruction.Builder(ldba, complete);

    return new AbstractMemoizingAutomaton.EdgeImplementation<>(
        atomicPropositions,
        builder.ldba.factory(),
        Set.of(builder.initialState),
        builder.acceptance) {

      @Nullable
      private Builder internalBuilder = builder;

      @Override
      protected Edge<SymmetricRankingState>
      edgeImpl(SymmetricRankingState state, BitSet valuation) {

        return internalBuilder.edge(state, valuation);
      }

      @Override
      protected void explorationCompleted() {
        internalBuilder = null;
      }
    };
  }

  private static final class Builder {

    private final ParityAcceptance acceptance;
    private final SymmetricRankingState initialState;
    private final List<Set<Map<Integer, EquivalenceClass>>> initialComponentSccs;
    private final AnnotatedLDBA<Map<Integer, EquivalenceClass>, SymmetricProductState,
        BuchiAcceptance, SortedSet<SymmetricEvaluatedFixpoints>, BiFunction<Integer, EquivalenceClass,
        Set<SymmetricProductState>>> ldba;

    @Nullable
    private final Edge<SymmetricRankingState> rejectingEdge;

    private Builder(AnnotatedLDBA<Map<Integer, EquivalenceClass>,
        SymmetricProductState, BuchiAcceptance, SortedSet<SymmetricEvaluatedFixpoints>,
        BiFunction<Integer, EquivalenceClass, Set<SymmetricProductState>>> ldba,
        boolean complete) {
      this.ldba = ldba;
      initialComponentSccs = SccDecomposition.of(ldba.initialComponent()).sccs();
      acceptance = new ParityAcceptance(
          2 * (ldba.acceptingComponent().states().size() + 1), Parity.MIN_ODD);
      Map<Integer, EquivalenceClass> ldbaInitialState = ldba.initialComponent().initialState();
      initialState = edge(ldbaInitialState, List.of(), 0, null).successor();
      rejectingEdge = complete
          ? Edge.of(SymmetricRankingState.of(Map.of()), acceptance.rejectingSet().orElseThrow())
          : null;
    }

    private Edge<SymmetricRankingState> edge(Map<Integer, EquivalenceClass> successor,
        List<Entry<Integer, SymmetricProductState>> previousRanking,
        int previousSafetyBucket, @Nullable BitSet valuation) {

      for (EquivalenceClass clazz : successor.values()) {
        if (SyntacticFragments.isSafety(clazz)) {
          return Edge.of(SymmetricRankingState.of(successor), 1);
        }
      }

      // We compute the relevant accepting components, which we can jump to.
      Set<Entry<Integer, SymmetricEvaluatedFixpoints>> allowedComponents = new HashSet<>();
      List<List<Entry<Integer, SymmetricProductState>>> targets
          = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
      NavigableMap<Integer, List<SymmetricProductState>> safety = new TreeMap<>();

      successor.forEach((index, value) -> {
        for (SymmetricProductState jumpTarget : ldba.stateAnnotation().apply(index, value)) {
          if (!ldba.acceptingComponent().states().contains(jumpTarget)) {
            continue;
          }

          var fixpoints = jumpTarget.evaluatedFixpoints;
          boolean changed = allowedComponents.add(entry(index, fixpoints));

          if (fixpoints.isSafety()) {
            safety.compute(index, (x, oldList) -> {
              if (oldList == null) {
                return new ArrayList<>(List.of(jumpTarget));
              } else {
                oldList.add(jumpTarget);
                return oldList;
              }
            });
          } else if (fixpoints.isLiveness() && jumpTarget.safety.isTrue()) {
            assert changed;
            targets.get(0).add(entry(index, jumpTarget));
          } else if (fixpoints.isLiveness()) {
            assert changed;
            targets.get(1).add(entry(index, jumpTarget));
          } else {
            assert changed;
            targets.get(2).add(entry(index, jumpTarget));
          }
        }
      });

      var comparator = Comparator
          .<Entry<Integer, SymmetricProductState>>comparingInt(Entry::getKey)
          .thenComparing(y -> y.getValue().evaluatedFixpoints);

      // Fix iteration order.
      targets.forEach(target -> target.sort(comparator));
      safety.values().forEach(x -> x.sort(Comparator.comparing(y -> y.evaluatedFixpoints)));

      // Default rejecting color.
      int edgeColor = 2 * previousRanking.size();
      List<Entry<Integer, SymmetricProductState>> ranking = new ArrayList<>(previousRanking.size());

      boolean activeSafetyComponent = false;

      { // Compute componentMap successor
        var iterator = previousRanking.listIterator();

        while (iterator.hasNext()) {
          assert valuation != null : "Valuation is only allowed to be null for empty rankings.";
          var entry = iterator.next();
          var rankingEdge = ldba.acceptingComponent().edge(entry.getValue(), valuation);
          var rankingSuccessor = rankingEdge == null ? null : rankingEdge.successor();

          // There are no jumps to this component anymore or the run stopped.
          if (rankingEdge == null
              || !allowedComponents.remove(fixpointsEntry(entry))) {
            edgeColor = Math.min(2 * iterator.previousIndex(), edgeColor);
            continue;
          }

          ranking.add(entry(entry.getKey(), rankingSuccessor));

          if (rankingEdge.colours().contains(0)) {
            edgeColor = Math.min(2 * iterator.previousIndex() + 1, edgeColor);
          }

          if (rankingSuccessor.evaluatedFixpoints.isSafety()) {
            // Since we remove rejecting sinks and transient markings this property does not hold
            // for edges in-between SCCs.
            // assert rankingEdge.colours().contains(0)
            //  : "SafetyComponents are assumed to be always accepting.";
            activeSafetyComponent = true;
          }
        }
      }

      // The state of the initial component is "Integer -> EquivalenceClass". We track from the
      // current safety condition stems, and if there exists multiple we need to order them.
      int safetyBucket;

      if (activeSafetyComponent) {
        safetyBucket = previousSafetyBucket;
      } else {
        for (var rankingState : Iterables.concat(targets.get(0), targets.get(1), targets.get(2))) {
          if (allowedComponents.remove(fixpointsEntry(rankingState))) {
            ranking.add(rankingState);
          }
        }

        var safetyEntry = safety.higherEntry(previousSafetyBucket);

        if (safetyEntry == null) {
          safetyEntry = safety.firstEntry();
        }

        if (safetyEntry == null) {
          safetyBucket = 0;
        } else {
          safetyBucket = safetyEntry.getKey();
          assert safetyBucket > 0;

          for (SymmetricProductState x : safetyEntry.getValue()) {
            ranking.add(entry(safetyBucket, x));
          }
        }
      }

      assert edgeColor < acceptance.acceptanceSets();
      return Edge.of(SymmetricRankingState.of(successor, ranking, safetyBucket), edgeColor);
    }

    @Nullable
    Edge<SymmetricRankingState> edge(SymmetricRankingState state, BitSet valuation) {
      // Check if the run is in the rejecting sink.
      if (rejectingEdge != null && rejectingEdge.successor().equals(state)) {
        return rejectingEdge;
      }

      // We obtain the successor of the state in the initial component.
      var successor = ldba.initialComponent().successor(state.state(), valuation);

      // The initial component moved to a rejecting sink and all runs stop.
      if (successor == null) {
        return rejectingEdge;
      }

      // If a SCC switch occurs, the ranking and the safety progress is reset.
      if (initialComponentSccs.stream()
          .anyMatch(x -> x.contains(state.state()) && !x.contains(successor))) {
        return edge(successor, List.of(), 0, valuation).withoutAcceptance();
      }

      return edge(successor, state.ranking(), state.safetyBucket(), valuation);
    }
  }

  private static Entry<Integer, SymmetricEvaluatedFixpoints> fixpointsEntry(
      Entry<Integer, SymmetricProductState> stateEntry) {
    return entry(stateEntry.getKey(), stateEntry.getValue().evaluatedFixpoints);
  }
}
