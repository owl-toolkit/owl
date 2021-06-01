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

package owl.translations.ltl2dra;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nullable;
import owl.automaton.AbstractMemoizingAutomaton;
import owl.automaton.AnnotatedStateOptimisation;
import owl.automaton.Automaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance.RabinPair;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.algorithm.SccDecomposition;
import owl.automaton.edge.Edge;
import owl.collections.Collections3;
import owl.ltl.EquivalenceClass;
import owl.ltl.LabelledFormula;
import owl.translations.BlockingElements;
import owl.translations.ltl2ldba.AnnotatedLDBA;
import owl.translations.ltl2ldba.SymmetricLDBAConstruction;
import owl.translations.ltl2ldba.SymmetricProductState;
import owl.translations.mastertheorem.SymmetricEvaluatedFixpoints;

/**
 * Implements the construction of {@link owl.Bibliography#LICS_18}.
 */
public final class SymmetricDRAConstruction<R extends GeneralizedRabinAcceptance>
  implements Function<LabelledFormula, Automaton<SymmetricRankingState, R>> {

  private final boolean optimizeInitialState;
  private final Class<R> acceptanceClass;
  private final SymmetricLDBAConstruction<?> ldbaConstruction;

  private SymmetricDRAConstruction(
      Class<R> acceptanceClass, boolean optimizeInitialState) {
    assert acceptanceClass.equals(GeneralizedRabinAcceptance.class)
      || acceptanceClass.equals(RabinAcceptance.class);

    var buchiAcceptance = acceptanceClass.equals(GeneralizedRabinAcceptance.class)
      ? GeneralizedBuchiAcceptance.class
      : BuchiAcceptance.class;

    this.acceptanceClass = acceptanceClass;
    this.optimizeInitialState = optimizeInitialState;
    this.ldbaConstruction = SymmetricLDBAConstruction.of(buchiAcceptance);
  }

  public static <R extends GeneralizedRabinAcceptance> SymmetricDRAConstruction<R>
    of(Class<R> clazz, boolean optimizeInitialState) {
    return new SymmetricDRAConstruction<>(clazz, optimizeInitialState);
  }

  @Override
  public Automaton<SymmetricRankingState, R> apply(LabelledFormula formula) {
    var ldba = ldbaConstruction.apply(formula);
    var builder = new Builder(ldba);
    var automaton = new AbstractMemoizingAutomaton.EdgeImplementation<>(
      ldba.acceptingComponent().atomicPropositions(),
      ldba.factory(),
      Collections3.ofNullable(builder.initialState),
      builder.acceptance) {

      @Override
      public Edge<SymmetricRankingState> edgeImpl(SymmetricRankingState state, BitSet valuation) {
        return builder.edge(state, valuation);
      }
    };

    return optimizeInitialState
      ? AnnotatedStateOptimisation.optimizeInitialState(automaton)
      : automaton;
  }

  private class Builder {
    private final R acceptance;
    private final Table<Integer, SymmetricEvaluatedFixpoints, RabinPair> pairs;
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
      this.initialComponentSccs = SccDecomposition.of(ldba.initialComponent()).sccs();
      this.ldba = ldba;
      this.pairs = HashBasedTable.create();

      var ldbaInitialState = ldba.initialComponent().initialStates().isEmpty()
        ? Map.<Integer, EquivalenceClass>of()
        : ldba.initialComponent().initialState();

      SortedSet<SymmetricEvaluatedFixpoints> fixpoints = new TreeSet<>(ldba.annotation());

      if (acceptanceClass.equals(RabinAcceptance.class)) {
        RabinAcceptance.Builder builder = new RabinAcceptance.Builder();
        fixpoints.forEach(
          x -> ldbaInitialState.keySet()
            .stream()
            .sorted()
            .forEach(y -> pairs.put(y, x, builder.add())));
        safetyRabinPair = builder.add();
        acceptance = acceptanceClass.cast(builder.build());
      } else {
        assert acceptanceClass.equals(GeneralizedRabinAcceptance.class);
        GeneralizedRabinAcceptance.Builder builder = new GeneralizedRabinAcceptance.Builder();
        int infSets = ldba.acceptance().acceptanceSets();
        fixpoints.forEach(
          x -> ldbaInitialState.keySet()
            .stream()
            .sorted()
            .forEach(y -> pairs.put(y, x, builder.add(infSets))));
        safetyRabinPair = builder.add(infSets);
        acceptance = acceptanceClass.cast(builder.build());
      }

      initialState = ldbaInitialState.isEmpty()
        ? null
        : edge(ldbaInitialState, ImmutableTable.of(), null).successor();
    }

    private Edge<SymmetricRankingState> edge(Map<Integer, EquivalenceClass> successor,
      Table<Integer, SymmetricEvaluatedFixpoints, SymmetricProductState> previousTable,
      @Nullable BitSet valuation) {

      for (EquivalenceClass clazz : successor.values()) {
        if (BlockingElements.isBlockedByTransient(clazz)
          || BlockingElements.isBlockedBySafety(clazz)) {

          if (safetyRabinPair.hasInfSet()) {
            BitSet acceptanceSets = new BitSet();
            safetyRabinPair.infSetStream().forEach(acceptanceSets::set);
            return Edge.of(SymmetricRankingState.of(successor), acceptanceSets);
          }

          return Edge.of(SymmetricRankingState.of(successor));
        }
      }

      var successorTable
        = HashBasedTable.<Integer, SymmetricEvaluatedFixpoints, SymmetricProductState>create();

      successor.forEach((index, clazz) -> {
        for (SymmetricProductState x : ldba.stateAnnotation().apply(index, clazz)) {
          if (!ldba.acceptingComponent().states().contains(x)) {
            continue;
          }

          var oldCell = successorTable.put(index, x.evaluatedFixpoints, x);
          assert oldCell == null;
        }
      });

      var acceptance = new BitSet();

      for (var entry : previousTable.cellSet()) {
        var index = entry.getRowKey();
        var state = entry.getValue();
        var fixpoints = entry.getColumnKey();

        assert valuation != null : "Valuation is only allowed to be null for empty rankings.";
        assert fixpoints != null && state != null && index != null : "Malformed data-structure";

        var edge = ldba.acceptingComponent().edge(state, valuation);
        var pair = pairs.get(index, fixpoints);

        if (edge == null || !successorTable.contains(index, fixpoints)) {
          acceptance.set(pair.finSet());
        } else {
          successorTable.put(index, fixpoints, edge.successor());
          edge.colours().forEach((int i) -> acceptance.set(pair.infSet(i)));
        }
      }

      return Edge.of(SymmetricRankingState.of(successor, successorTable), acceptance);
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
        return edge(successor, ImmutableTable.of(), valuation).withoutAcceptance();
      }

      return edge(successor, state.table(), valuation);
    }
  }
}
