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

import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.function.Function;
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
import owl.automaton.edge.Edges;
import owl.bdd.EquivalenceClassFactory;
import owl.bdd.FactorySupplier;
import owl.bdd.MtBdd;
import owl.bdd.MtBddOperations;
import owl.ltl.BooleanConstant;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.SyntacticFragments;
import owl.translations.BlockingElements;
import owl.translations.ltl2ldba.AnnotatedLDBA;
import owl.translations.ltl2ldba.AsymmetricLDBAConstruction;
import owl.translations.ltl2ldba.AsymmetricProductState;
import owl.translations.mastertheorem.AsymmetricEvaluatedFixpoints;

final class AsymmetricDPAConstruction {

  private static final AsymmetricLDBAConstruction<BuchiAcceptance> LDBA_CONSTRUCTION
      = AsymmetricLDBAConstruction.of(BuchiAcceptance.class);


  Automaton<AsymmetricRankingState, ParityAcceptance> of(
      LabelledFormula labelledFormula, boolean complete) {

    var nnfLabelledFormula = labelledFormula.nnf();
    var ldba = LDBA_CONSTRUCTION.apply(nnfLabelledFormula);
    var atomicPropositions = ldba.acceptingComponent().atomicPropositions();

    if (ldba.initialComponent().initialStates().isEmpty()) {
      var acceptance = new ParityAcceptance(3, Parity.MIN_ODD);
      var eqFactory =
          FactorySupplier.defaultSupplier().getEquivalenceClassFactory(atomicPropositions);

      return complete
          ? SingletonAutomaton.of(
          atomicPropositions,
          AsymmetricRankingState.of(eqFactory.of(false)),
          acceptance,
          acceptance.rejectingSet().orElseThrow())
          : EmptyAutomaton.of(
              atomicPropositions,
              acceptance);
    }

    var builder = new Builder(nnfLabelledFormula.formula(), ldba, complete);

    return new AbstractMemoizingAutomaton.EdgeTreeImplementation<>(
        builder.ldba.acceptingComponent().atomicPropositions(),
        builder.ldba.factory(),
        Set.of(builder.initialState),
        builder.acceptance) {

      @Nullable
      private AsymmetricDPAConstruction.Builder internalBuilder = builder;

      @Override
      protected MtBdd<Edge<AsymmetricRankingState>> edgeTreeImpl(
          AsymmetricRankingState state) {
        return internalBuilder.edgeTree(state);
      }

      @Override
      protected void explorationCompleted() {
        internalBuilder = null;
      }
    };
  }

  private static final class Builder {

    private final ParityAcceptance acceptance;
    private final AsymmetricRankingState initialState;
    private final EquivalenceClassFactory factory;
    private final List<Set<EquivalenceClass>> initialComponentSccs;
    private final AnnotatedLDBA<EquivalenceClass, AsymmetricProductState, BuchiAcceptance, SortedSet
        <AsymmetricEvaluatedFixpoints>, Function<EquivalenceClass, Set<AsymmetricProductState>>> ldba;
    private final Set<Formula.TemporalOperator> blockingSafetyOperators;

    private final Map<EquivalenceClass, Boolean> blockedByTransient = new HashMap<>();
    private final Map<EquivalenceClass, Boolean> blockedBySafety = new HashMap<>();

    @Nullable
    private final Edge<AsymmetricRankingState> rejectingEdge;

    private Builder(Formula formula, AnnotatedLDBA<EquivalenceClass, AsymmetricProductState,
        BuchiAcceptance, SortedSet<AsymmetricEvaluatedFixpoints>, Function<EquivalenceClass,
        Set<AsymmetricProductState>>> ldba,
        boolean complete) {

      this.ldba = ldba;
      this.initialComponentSccs = SccDecomposition.of(ldba.initialComponent()).sccs();
      acceptance = new ParityAcceptance(
          2 * (ldba.acceptingComponent().states().size() + 1), Parity.MIN_ODD);
      EquivalenceClass ldbaInitialState = ldba.initialComponent().initialState();
      factory = ldbaInitialState.factory();
      blockingSafetyOperators = BlockingElements.blockingSafetyFormulas(factory.of(formula));
      initialState = edge(ldbaInitialState, List.of(), -1).successor();

      rejectingEdge = complete
          ? Edge.of(
          AsymmetricRankingState.of(factory.of(false)),
          acceptance.rejectingSet().orElseThrow())
          : null;
    }

    private boolean isBlockedByTransient(EquivalenceClass state) {
      return blockedByTransient.computeIfAbsent(state, BlockingElements::isBlockedByTransient);
    }

    private boolean isBlockedBySafety(EquivalenceClass state) {
      return blockedBySafety.computeIfAbsent(state,
          x -> !Collections.disjoint(x.temporalOperators(), blockingSafetyOperators)
              || BlockingElements.isBlockedBySafety(x));
    }

    private Edge<AsymmetricRankingState> edge(EquivalenceClass successor,
        List<Edge<AsymmetricProductState>> rankingEdges, int previousSafetyProgress) {

      // Short-circuit, if the language includes a non-empty safety language.
      if (isBlockedByTransient(successor) || isBlockedBySafety(successor)) {
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
          if (SyntacticFragments.isSafety(jumpTarget.language())) {
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
      List<AsymmetricProductState> ranking = new ArrayList<>(rankingEdges.size());
      int rankingColor = 2 * rankingEdges.size(); // Default rejecting color.
      var rankingLanguage = factory.of(BooleanConstant.FALSE);

      {
        var iterator = rankingEdges.iterator();

        while (iterator.hasNext()) {
          var rankingEdge = iterator.next();

          if (rankingEdge == null
              || !ldba.acceptingComponent().states().contains(rankingEdge.successor())) {
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

          if (rankingEdge.colours().contains(0)) {
            rankingColor = Math.min(2 * ranking.size() + 1, rankingColor);
          }

          ranking.add(rankingSuccessor);
          rankingLanguage = rankingLanguage.or(successorLanguage);

          // Check last element of ranking (could be safety)
          if (!iterator.hasNext()
              && partitionedFixpoints.get(3).containsKey(rankingSuccessor.evaluatedFixpoints)
              && ldba.annotation().headSet(fixpoints).size() == previousSafetyProgress) {
            rankingLanguage = factory.of(BooleanConstant.TRUE);
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

          if (!jumpTargetLanguage.implies(rankingLanguage)
              && ldba.acceptingComponent().states().contains(jumpTarget)) {
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
          if (!safetyState.language().implies(rankingLanguage)
              && ldba.acceptingComponent().states().contains(safetyState)) {
            ranking.add(safetyState);
            safetyProgress = ldba.annotation().headSet(safetyState.evaluatedFixpoints).size();
            break;
          }
        }
      }

      assert rankingColor < acceptance.acceptanceSets();
      return Edge.of(AsymmetricRankingState.of(successor, ranking, safetyProgress), rankingColor);
    }

    private MtBdd<Edge<AsymmetricRankingState>> edgeTree(
        AsymmetricRankingState macroState) {

      if (macroState.state().isFalse() && rejectingEdge != null) {
        return MtBdd.of(rejectingEdge);
      }

      // We obtain the successor of the state in the initial component.
      var successorTree = ldba.initialComponent()
          .edgeTree(macroState.state())
          .map(edges -> {
            if (edges.isEmpty() && rejectingEdge != null) {
              return Set.of(rejectingEdge.successor().state());
            } else {
              return Edges.successors(edges);
            }
          });

      var rankingEdgeTree
          = MtBddOperations.cartesianProductWithNull(macroState.ranking()
          .stream()
          .map(state -> ldba.acceptingComponent().edgeTree(state))
          .toList());

      return MtBddOperations.cartesianProduct(successorTree, rankingEdgeTree,
          (successor, rankingEdges) -> {
            if (successor.isFalse()) {
              return Objects.requireNonNull(rejectingEdge);
            }

            // If a SCC switch occurs, the ranking and the safety progress is reset.
            if (initialComponentSccs.stream()
                .anyMatch(x -> x.contains(macroState.state()) && !x.contains(successor))) {
              return edge(successor, List.of(), -1).withoutAcceptance();
            }

            return edge(successor, rankingEdges, macroState.safetyIndex());
          });
    }
  }
}
