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

package owl.translations.ltl2dra;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Table;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.AutomatonFactory;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance.RabinPair;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.algorithms.SccDecomposition;
import owl.automaton.edge.Edge;
import owl.automaton.util.AnnotatedStateOptimisation;
import owl.collections.Collections3;
import owl.ltl.EquivalenceClass;
import owl.ltl.LabelledFormula;
import owl.ltl.SyntacticFragments;
import owl.run.Environment;
import owl.translations.ltl2ldba.AnnotatedLDBA;
import owl.translations.ltl2ldba.SymmetricLDBAConstruction;
import owl.translations.ltl2ldba.SymmetricProductState;
import owl.translations.mastertheorem.SymmetricEvaluatedFixpoints;

public final class SymmetricDRAConstruction<R extends GeneralizedRabinAcceptance>
  implements Function<LabelledFormula, Automaton<SymmetricRankingState, R>> {

  private final boolean optimizeInitialState;
  private final Class<? extends R> acceptanceClass;
  private final SymmetricLDBAConstruction<?> translation;

  private SymmetricDRAConstruction(
    Environment environment, Class<? extends R> acceptanceClass, boolean optimizeInitialState) {
    assert acceptanceClass.equals(GeneralizedRabinAcceptance.class)
      || acceptanceClass.equals(RabinAcceptance.class);

    var buchiAcceptance = acceptanceClass.equals(GeneralizedRabinAcceptance.class)
      ? GeneralizedBuchiAcceptance.class
      : BuchiAcceptance.class;

    this.acceptanceClass = acceptanceClass;
    this.optimizeInitialState = optimizeInitialState;
    this.translation = SymmetricLDBAConstruction.of(environment, buchiAcceptance);
  }

  public static <R extends GeneralizedRabinAcceptance> SymmetricDRAConstruction<R>
    of(Environment environment, Class<? extends R> clazz, boolean optimizeInitialState) {
    return new SymmetricDRAConstruction<>(environment, clazz, optimizeInitialState);
  }

  @Override
  public Automaton<SymmetricRankingState, R> apply(LabelledFormula formula) {
    var ldba = translation.apply(formula);
    var builder = new Builder(ldba);
    var automaton = AutomatonFactory.<SymmetricRankingState, R>create(ldba.factory(),
      Collections3.ofNullable(builder.initialState), builder.acceptance, builder::edge);
    return optimizeInitialState
      ? AnnotatedStateOptimisation.optimizeInitialState(automaton)
      : automaton;
  }

  private class Builder {
    private final R acceptance;
    private final Map<SymmetricEvaluatedFixpoints, RabinPair> pairs;
    private final RabinPair safetyRabinPair;
    @Nullable
    private final SymmetricRankingState initialState;
    private final List<Set<Map<Integer, EquivalenceClass>>> initialComponentSccs;
    private final AnnotatedLDBA<Map<Integer, EquivalenceClass>, SymmetricProductState, ?,
          SortedSet<SymmetricEvaluatedFixpoints>, BiFunction<Integer, EquivalenceClass,
          Set<SymmetricProductState>>> ldba;

    private Builder(AnnotatedLDBA<Map<Integer, EquivalenceClass>,
          SymmetricProductState, ?, SortedSet<SymmetricEvaluatedFixpoints>,
          BiFunction<Integer, EquivalenceClass, Set<SymmetricProductState>>> ldba) {
      this.initialComponentSccs = SccDecomposition.computeSccs(ldba.initialComponent());
      this.ldba = ldba;
      this.pairs = new HashMap<>();

      var ldbaInitialState = ldba.initialComponent().initialStates().isEmpty()
        ? Map.<Integer, EquivalenceClass>of()
        : ldba.initialComponent().onlyInitialState();

      SortedSet<SymmetricEvaluatedFixpoints> fixpoints = ldba.annotation()
        .stream()
        .filter(Predicate.not(SymmetricEvaluatedFixpoints::isEmpty))
        .collect(Collectors.toCollection(TreeSet::new));

      if (acceptanceClass.equals(RabinAcceptance.class)) {
        RabinAcceptance.Builder builder = new RabinAcceptance.Builder();
        fixpoints.forEach(x -> pairs.put(x, builder.add()));
        safetyRabinPair = builder.add();
        acceptance = acceptanceClass.cast(builder.build());
      } else {
        assert acceptanceClass.equals(GeneralizedRabinAcceptance.class);
        GeneralizedRabinAcceptance.Builder builder = new GeneralizedRabinAcceptance.Builder();
        int infSets = ldba.acceptance().acceptanceSets();
        fixpoints.forEach(x -> pairs.put(x, builder.add(infSets)));
        safetyRabinPair = builder.add(infSets);
        acceptance = acceptanceClass.cast(builder.build());
      }

      initialState = ldbaInitialState.isEmpty()
        ? null
        : edge(ldbaInitialState, ImmutableTable.of(), 0, -1, null).successor();
    }

    private Edge<SymmetricRankingState> edge(Map<Integer, EquivalenceClass> successor,
      Table<Integer, SymmetricEvaluatedFixpoints, SymmetricProductState> previousTable,
      int safetyBucket, int safetyBucketIndex, @Nullable BitSet valuation) {

      for (EquivalenceClass clazz : successor.values()) {
        if (SyntacticFragments.isSafety(clazz.modalOperators())) {
          if (safetyRabinPair.hasInfSet()) {
            BitSet acceptanceSets = new BitSet();
            safetyRabinPair.infSetIterator().forEachRemaining((IntConsumer) acceptanceSets::set);
            return Edge.of(SymmetricRankingState.of(successor), acceptanceSets);
          }

          return Edge.of(SymmetricRankingState.of(successor));
        }
      }

      var safetySuccessorMap
        = new TreeMap<Integer, TreeMap<SymmetricEvaluatedFixpoints, SymmetricProductState>>();
      var successorMap
        = HashBasedTable.<Integer, SymmetricEvaluatedFixpoints, SymmetricProductState>create();

      successor.forEach((index, clazz) -> {
        for (SymmetricProductState x : ldba.stateAnnotation().apply(index, clazz)) {
          if (!ldba.acceptingComponent().states().contains(x)) {
            continue;
          }

          if (x.evaluatedFixpoints.isSafety()) {
            safetySuccessorMap.putIfAbsent(index, new TreeMap<>());
            var oldCell = safetySuccessorMap.get(index).put(x.evaluatedFixpoints, x);
            assert oldCell == null;
          } else {
            var oldCell = successorMap.put(index, x.evaluatedFixpoints, x);
            assert oldCell == null;
          }
        }
      });

      var acceptance = new BitSet();
      boolean activeSafetyComponent = false;

      Set<SymmetricEvaluatedFixpoints> seenEvaluatedFixpoints = new HashSet<>();

      for (var entry : previousTable.cellSet()) {
        var fixpoints = entry.getColumnKey();
        var state = entry.getValue();
        var index = entry.getRowKey();

        assert valuation != null : "Valuation is only allowed to be null for empty rankings.";
        assert fixpoints != null && state != null && index != null
          : "Malformed internal data-structure";

        var edge = ldba.acceptingComponent().edge(state, valuation);
        RabinPair pair = fixpoints.isEmpty() ? safetyRabinPair : pairs.get(fixpoints);

        assert fixpoints.isSafety() || seenEvaluatedFixpoints.add(fixpoints)
          : "Non-safety fixpoint already encountered: " + fixpoints;

        if (edge == null || (!fixpoints.isSafety() && !successorMap.contains(index, fixpoints))) {
          acceptance.set(pair.finSet());
        } else {
          successorMap.put(index, fixpoints, edge.successor());
          edge.acceptanceSetIterator().forEachRemaining((int i) -> acceptance.set(pair.infSet(i)));

          if (fixpoints.isSafety()) {
            activeSafetyComponent = true;
            assert safetyBucket > 0 && safetyBucketIndex >= 0;
          }
        }
      }

      // Find next safety-like component.
      int successorSafetyBucket = 0;
      int successorSafetyBucketIndex = -1;

      if (activeSafetyComponent) {
        successorSafetyBucket = safetyBucket;
        successorSafetyBucketIndex = safetyBucketIndex;
      } else {
        acceptance.set(safetyRabinPair.finSet());

        var bucket = safetySuccessorMap.get(safetyBucket);

        // Bucket is present.
        if (bucket != null) {
          var evaluatedFixpoints = Iterables.get(ldba.annotation(), safetyBucketIndex);
          var safetyStateEntry = bucket.tailMap(evaluatedFixpoints, false).firstEntry();

          if (safetyStateEntry == null) {
            bucket = null;
          } else {
            // There exits a safety component in the bucket.
            successorSafetyBucket = safetyBucket;
            successorSafetyBucketIndex = ldba.annotation()
              .headSet(safetyStateEntry.getKey()).size();
            successorMap.put(0, safetyStateEntry.getKey(), safetyStateEntry.getValue());
          }
        }

        // Find a new bucket.
        if (bucket == null) {
          var bucketEntry = Iterables.getFirst(Iterables.concat(
            safetySuccessorMap.tailMap(safetyBucket, false).entrySet(),
            safetySuccessorMap.headMap(safetyBucket, true).entrySet()), null);

          if (bucketEntry != null) {
            var safetyStateEntry = bucketEntry.getValue().firstEntry();
            successorSafetyBucket = bucketEntry.getKey();
            successorSafetyBucketIndex = ldba.annotation()
              .headSet(safetyStateEntry.getKey()).size();
            successorMap.put(0, safetyStateEntry.getKey(), safetyStateEntry.getValue());
          }
        }
      }

      return Edge.of(SymmetricRankingState.of(successor, successorMap, successorSafetyBucket,
        successorSafetyBucketIndex), acceptance);
    }

    @Nullable
    private Edge<SymmetricRankingState> edge(SymmetricRankingState state, BitSet valuation) {
      // We obtain the successor of the state in the initial component.
      var successor = ldba.initialComponent().successor(state.state(), valuation);

      // The initial component moved to a rejecting sink. Thus all runs die.
      if (successor == null) {
        return null;
      }

      // If a SCC switch occurs, the componentMap and the safety progress is reset.
      if (initialComponentSccs.stream()
        .anyMatch(x -> x.contains(state.state()) && !x.contains(successor))) {
        return edge(successor, ImmutableTable.of(), 0, -1, valuation).withoutAcceptance();
      }

      return edge(successor, state.table(), state.safetyBucket(), state.safetyBucketIndex(),
        valuation);
    }
  }
}
