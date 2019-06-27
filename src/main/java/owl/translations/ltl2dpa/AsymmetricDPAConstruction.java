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
import java.util.HashSet;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.function.Function;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.AutomatonFactory;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.ParityAcceptance.Parity;
import owl.automaton.algorithms.SccDecomposition;
import owl.automaton.edge.Edge;
import owl.factories.EquivalenceClassFactory;
import owl.ltl.EquivalenceClass;
import owl.ltl.SyntacticFragments;
import owl.translations.SafetyCoreDetector;
import owl.translations.ltl2ldba.AnnotatedLDBA;
import owl.translations.ltl2ldba.AsymmetricProductState;
import owl.translations.mastertheorem.AsymmetricEvaluatedFixpoints;

final class AsymmetricDPAConstruction {
  private AsymmetricDPAConstruction() {}

  static Automaton<AsymmetricRankingState, ParityAcceptance>
  of(AnnotatedLDBA<EquivalenceClass, AsymmetricProductState, BuchiAcceptance, SortedSet
    <AsymmetricEvaluatedFixpoints>, Function<EquivalenceClass, Set<AsymmetricProductState>>> ldba) {
    var builder = new Builder(ldba);
    return AutomatonFactory.create(builder.ldba.factory(),
      builder.initialState, builder.acceptance, builder::edge);
  }

  static final class Builder {
    final ParityAcceptance acceptance;
    final AsymmetricRankingState initialState;
    final EquivalenceClassFactory factory;
    final List<Set<EquivalenceClass>> initialComponentSccs;
    final AnnotatedLDBA<EquivalenceClass, AsymmetricProductState, BuchiAcceptance, SortedSet
      <AsymmetricEvaluatedFixpoints>, Function<EquivalenceClass, Set<AsymmetricProductState>>> ldba;

    private Builder(AnnotatedLDBA<EquivalenceClass, AsymmetricProductState, BuchiAcceptance,
      SortedSet<AsymmetricEvaluatedFixpoints>, Function<EquivalenceClass,
      Set<AsymmetricProductState>>> ldba) {
      this.ldba = ldba;
      this.initialComponentSccs = SccDecomposition.computeSccs(ldba.initialComponent());
      acceptance = new ParityAcceptance(2 * Math.max(1, ldba.acceptingComponent().size() + 1),
        Parity.MIN_ODD);
      EquivalenceClass ldbaInitialState = ldba.initialComponent().onlyInitialState();
      factory = ldbaInitialState.factory();
      initialState = edge(ldbaInitialState, List.of(), -1, null).successor();
    }

    Edge<AsymmetricRankingState> edge(EquivalenceClass successor,
      List<AsymmetricProductState> previousRanking, int previousSafetyProgress,
      @Nullable BitSet valuation) {

      // Short-circuit, if the language includes a non-empty safety language.
      if (SafetyCoreDetector.safetyCoreExists(successor)) {
        return Edge.of(AsymmetricRankingState.of(successor), 1);
      }

      // Compute reachable fixpoints (and corresponding states) for ranking pruning and saturation.
      Set<AsymmetricEvaluatedFixpoints> availableFixpoints = new HashSet<>();
      List<NavigableMap<AsymmetricEvaluatedFixpoints, AsymmetricProductState>> partitionedFixpoints
        = List.of(
          new TreeMap<>(), // Pure liveness-languages
          new TreeMap<>(), // General languages
          new TreeMap<>(), // Almost safety-languages
          new TreeMap<>()  // Safety-languages
        );

      for (AsymmetricProductState jumpTarget : ldba.stateAnnotation().apply(successor)) {
        var fixpoints = jumpTarget.evaluatedFixpoints;

        boolean changed = availableFixpoints.add(fixpoints);
        assert changed : "Detected two jumps to the same component."
          + ldba.stateAnnotation().apply(successor);

        if (fixpoints.isSafety()) {
          if (SyntacticFragments.isSafety(jumpTarget.language().modalOperators())) {
            partitionedFixpoints.get(3).put(fixpoints, jumpTarget);
          } else {
            partitionedFixpoints.get(2).put(fixpoints, jumpTarget);
          }
        } else if (fixpoints.isLiveness()) {
          partitionedFixpoints.get(0).put(fixpoints, jumpTarget);
        } else {
          partitionedFixpoints.get(1).put(fixpoints, jumpTarget);
        }
      }

      // Compute successors of existing ranking.
      List<AsymmetricProductState> ranking = new ArrayList<>(previousRanking.size());
      int rankingColor = 2 * previousRanking.size(); // Default rejecting color.
      var rankingLanguage = factory.getFalse();

      {
        var iterator = previousRanking.iterator();

        while (iterator.hasNext()) {
          assert valuation != null : "Valuation is only allowed to be null for empty rankings.";

          var rankingState = iterator.next();
          var rankingEdge = ldba.acceptingComponent().states().contains(rankingState)
            ? ldba.acceptingComponent().edge(rankingState, valuation)
            : null;

          if (rankingEdge == null) {
            rankingColor = Math.min(2 * ranking.size(), rankingColor);
            continue;
          }

          var rankingSuccessor = rankingEdge.successor();
          var fixpoints = Objects.requireNonNull(rankingSuccessor.evaluatedFixpoints);
          var successorLanguage = rankingSuccessor.language();

          // There are no jumps to this component anymore.
          if (!availableFixpoints.contains(fixpoints)
            || successorLanguage.implies(rankingLanguage)) {
            rankingColor = Math.min(2 * ranking.size(), rankingColor);
            continue;
          }

          if (rankingEdge.inSet(0)) {
            rankingColor = Math.min(2 * ranking.size() + 1, rankingColor);
            rankingLanguage = rankingLanguage.or(fixpoints.language());
          } else {
            rankingLanguage = rankingLanguage.or(successorLanguage);
          }

          ranking.add(rankingSuccessor);

          // Check last element of ranking (could be safety)
          if (!iterator.hasNext()
            && partitionedFixpoints.get(3).containsKey(rankingSuccessor.evaluatedFixpoints)
            && ldba.annotation().headSet(fixpoints).size() == previousSafetyProgress) {
            rankingLanguage = factory.getTrue();
          }
        }
      }

      // Saturate ranking
      int safetyProgress;

      if (rankingLanguage.isTrue()) {
        // Already saturated
        safetyProgress = ranking.isEmpty() ? -1 : previousSafetyProgress;
      } else {
        // Add partitions 0 to 2.
        for (AsymmetricProductState jumpTarget : Iterables.concat(
          partitionedFixpoints.get(0).values(),
          partitionedFixpoints.get(1).values(),
          partitionedFixpoints.get(2).values())) {
          var jumpTargetLanguage = jumpTarget.language();

          if (!jumpTargetLanguage.implies(rankingLanguage)) {
            ranking.add(jumpTarget);
            rankingLanguage = rankingLanguage.or(jumpTargetLanguage);
          }
        }

        // Add partition 3.
        var availableSafetyFixpoints = partitionedFixpoints.get(3);

        Iterable<AsymmetricProductState> orderedSafetyStates;

        if (previousSafetyProgress >= 0) {
          var previousSafetyFixpoints = Iterables.get(ldba.annotation(), previousSafetyProgress);
          orderedSafetyStates = Iterables.concat(
            availableSafetyFixpoints.tailMap(previousSafetyFixpoints, false).values(),
            availableSafetyFixpoints.headMap(previousSafetyFixpoints, true).values());
        } else {
          orderedSafetyStates = availableSafetyFixpoints.values();
        }

        safetyProgress = -1;

        for (AsymmetricProductState safetyState : orderedSafetyStates) {
          if (!safetyState.language().implies(rankingLanguage)) {
            ranking.add(safetyState);
            safetyProgress = ldba.annotation().headSet(safetyState.evaluatedFixpoints).size();
            break;
          }
        }
      }

      assert rankingColor < acceptance.acceptanceSets();
      return Edge.of(AsymmetricRankingState.of(successor, ranking, safetyProgress), rankingColor);
    }

    @Nullable
    Edge<AsymmetricRankingState> edge(AsymmetricRankingState macroState, BitSet valuation) {
      // We obtain the successor of the state in the initial component.
      var successor = ldba.initialComponent().successor(macroState.state(), valuation);

      // The of the leading state is empty.
      if (successor == null) {
        return null;
      }

      // If a SCC switch occurs, the ranking and the safety progress is reset.
      if (initialComponentSccs.stream()
        .anyMatch(x -> x.contains(macroState.state()) && !x.contains(successor))) {
        return edge(successor, List.of(), -1, valuation).withoutAcceptance();
      }

      return edge(successor, macroState.ranking(), macroState.safetyIndex(), valuation);
    }
  }
}
