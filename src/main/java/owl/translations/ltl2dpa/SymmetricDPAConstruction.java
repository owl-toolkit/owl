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

package owl.translations.ltl2dpa;

import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.AutomatonFactory;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.ParityAcceptance.Parity;
import owl.automaton.algorithms.SccDecomposition;
import owl.automaton.edge.Edge;
import owl.ltl.EquivalenceClass;
import owl.ltl.LabelledFormula;
import owl.ltl.SyntacticFragments;
import owl.run.Environment;
import owl.translations.ltl2ldba.AnnotatedLDBA;
import owl.translations.ltl2ldba.SymmetricLDBAConstruction;
import owl.translations.ltl2ldba.SymmetricProductState;
import owl.translations.mastertheorem.SymmetricEvaluatedFixpoints;

final class SymmetricDPAConstruction {
  private final SymmetricLDBAConstruction<BuchiAcceptance> ldbaTranslator;

  SymmetricDPAConstruction(Environment environment) {
    ldbaTranslator = SymmetricLDBAConstruction.of(environment, BuchiAcceptance.class);
  }

  Automaton<SymmetricRankingState, ParityAcceptance> of(LabelledFormula labelledFormula) {
    var ldba = ldbaTranslator.apply(labelledFormula);

    if (ldba.initialComponent().initialStates().isEmpty()) {
      return AutomatonFactory.empty(ldba.factory(), new ParityAcceptance(3, Parity.MIN_ODD));
    }

    var builder = new SymmetricDPAConstruction.Builder(ldba);
    return AutomatonFactory.create(builder.ldba.factory(),
      builder.initialState, builder.acceptance, builder::edge);
  }

  private static final class Builder {
    private final ParityAcceptance acceptance;
    private final SymmetricRankingState initialState;
    private final List<Set<Map<Integer, EquivalenceClass>>> initialComponentSccs;
    private final AnnotatedLDBA<Map<Integer, EquivalenceClass>, SymmetricProductState,
      BuchiAcceptance, SortedSet<SymmetricEvaluatedFixpoints>, BiFunction<Integer, EquivalenceClass,
      Set<SymmetricProductState>>> ldba;

    private Builder(AnnotatedLDBA<Map<Integer, EquivalenceClass>,
          SymmetricProductState, BuchiAcceptance, SortedSet<SymmetricEvaluatedFixpoints>,
          BiFunction<Integer, EquivalenceClass, Set<SymmetricProductState>>> ldba) {
      this.ldba = ldba;
      this.initialComponentSccs = SccDecomposition.computeSccs(ldba.initialComponent());
      // Identify  safety components.
      acceptance = new ParityAcceptance(2 * Math.max(1, ldba.acceptingComponent().size() + 1),
        Parity.MIN_ODD);
      Map<Integer, EquivalenceClass> ldbaInitialState = ldba.initialComponent().onlyInitialState();
      initialState = edge(ldbaInitialState, List.of(), 0, -1, null).successor();
    }

    private Edge<SymmetricRankingState> edge(
      Map<Integer, EquivalenceClass> successor, List<SymmetricProductState> previousRanking,
      int previousSafetyBucket, int previousSafetyBucketIndex, @Nullable BitSet valuation) {

      for (EquivalenceClass entry : successor.values()) {
        if (SyntacticFragments.isSafety(entry.modalOperators())) {
          return Edge.of(SymmetricRankingState.of(successor), 1);
        }
      }

      // We compute the relevant accepting components, which we can jump to.
      Set<SymmetricEvaluatedFixpoints> allowedComponents = new HashSet<>();
      List<List<SymmetricProductState>> targets
        = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
      var safety = new TreeMap<Integer, List<SymmetricProductState>>();

      successor.forEach((index, value) -> {
        for (SymmetricProductState jumpTarget : ldba.stateAnnotation().apply(index, value)) {
          if (!ldba.acceptingComponent().states().contains(jumpTarget)) {
            continue;
          }

          var fixpoints = jumpTarget.evaluatedFixpoints;
          boolean changed = allowedComponents.add(fixpoints);

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
            targets.get(0).add(jumpTarget);
          } else if (fixpoints.isLiveness()) {
            assert changed;
            targets.get(1).add(jumpTarget);
          } else {
            assert changed;
            targets.get(2).add(jumpTarget);
          }
        }
      });

      // Fix iteration order.
      targets.forEach(x -> x.sort(Comparator.comparing(y -> y.evaluatedFixpoints)));
      safety.values().forEach(x -> x.sort(Comparator.comparing(y -> y.evaluatedFixpoints)));

      // Default rejecting color.
      int edgeColor = 2 * previousRanking.size();
      List<SymmetricProductState> ranking = new ArrayList<>(previousRanking.size());

      boolean activeSafetyComponent = false;

      { // Compute componentMap successor
        var iterator = previousRanking.listIterator();

        while (iterator.hasNext()) {
          assert valuation != null : "Valuation is only allowed to be null for empty rankings.";
          var rankingEdge = ldba.acceptingComponent().edge(iterator.next(), valuation);
          var rankingSuccessor = rankingEdge == null ? null : rankingEdge.successor();

          // There are no jumps to this component anymore or the run stopped.
          if (rankingEdge == null
            || !allowedComponents.remove(rankingSuccessor.evaluatedFixpoints)) {
            edgeColor = Math.min(2 * iterator.previousIndex(), edgeColor);
            continue;
          }

          ranking.add(rankingSuccessor);

          if (rankingEdge.inSet(0)) {
            edgeColor = Math.min(2 * iterator.previousIndex() + 1, edgeColor);
          }

          if (rankingSuccessor.evaluatedFixpoints.isSafety()) {
            assert rankingEdge.inSet(0) : "SafetyComponents are assumed to be always accepting.";
            activeSafetyComponent = true;
          }
        }
      }

      // The state of the initial component is "Integer -> EquivalenceClass". We track from the
      // current safety condition stems, and if there exists multiple we need to order them.
      int safetyBucket;
      int safetyBucketIndex;

      if (activeSafetyComponent) {
        safetyBucket = previousSafetyBucket;
        safetyBucketIndex = previousSafetyBucketIndex;
      } else {
        for (SymmetricProductState rankingState
          : Iterables.concat(targets.get(0), targets.get(1), targets.get(2))) {
          if (allowedComponents.remove(rankingState.evaluatedFixpoints)) {
            ranking.add(rankingState);
          }
        }

        var safetyEntry = safety.ceilingEntry(previousSafetyBucket);
        safetyBucketIndex = previousSafetyBucketIndex;

        if (safetyEntry != null) {
          if (safetyEntry.getKey() == previousSafetyBucket
              && safetyEntry.getValue().size() <= safetyBucketIndex + 1) {
            safetyEntry = safety.ceilingEntry(previousSafetyBucket + 1);
            safetyBucketIndex = -1;
          } else if (safetyEntry.getKey() > previousSafetyBucket) {
            safetyBucketIndex = -1;
          }
        }

        if (safetyEntry == null) {
          safetyEntry = safety.ceilingEntry(0);
          safetyBucketIndex = -1;
        }

        if (safetyEntry == null) {
          safetyBucket = 0;
          safetyBucketIndex = -1;
        } else {
          safetyBucket = safetyEntry.getKey();
          assert safetyBucket > 0;
          safetyBucketIndex = safetyBucketIndex + 1;
          ranking.add(safetyEntry.getValue().get(safetyBucketIndex));
        }
      }

      assert edgeColor < acceptance.acceptanceSets();
      return Edge.of(
        SymmetricRankingState.of(successor, ranking, safetyBucket, safetyBucketIndex), edgeColor);
    }

    @Nullable
    Edge<SymmetricRankingState> edge(SymmetricRankingState state, BitSet valuation) {
      // We obtain the successor of the state in the initial component.
      var successor = ldba.initialComponent().successor(state.state(), valuation);

      // The initial component moved to a rejecting sink. Thus all runs die.
      if (successor == null) {
        return null;
      }

      // If a SCC switch occurs, the ranking and the safety progress is reset.
      if (initialComponentSccs.stream()
        .anyMatch(x -> x.contains(state.state()) && !x.contains(successor))) {
        return edge(successor, List.of(), 0, -1, valuation).withoutAcceptance();
      }

      return edge(successor, state.ranking(), state.safetyBucket(), state.safetyBucketIndex(),
        valuation);
    }
  }
}
