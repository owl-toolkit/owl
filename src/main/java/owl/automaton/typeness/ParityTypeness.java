/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
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

package owl.automaton.typeness;

import com.google.auto.value.AutoValue;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import owl.automaton.Automaton;
import owl.automaton.EmptyAutomaton;
import owl.automaton.HashMapAutomaton;
import owl.automaton.SuccessorFunction;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.acceptance.optimization.AcceptanceOptimizations;
import owl.automaton.algorithm.SccDecomposition;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.collections.Result;

public final class ParityTypeness {

  private ParityTypeness() {}

  public static <S> Optional<Automaton<S, ParityAcceptance>>
    apply(Automaton<S, ? extends RabinAcceptance> deterministicRabinAutomaton) {

    return impl(deterministicRabinAutomaton, true).map(Optional::of, y -> Optional.empty());
  }

  public static <S> Result<Automaton<S, ParityAcceptance>, List<Set<S>>>
    applyOrGiveCounterExample(Automaton<S, RabinAcceptance> deterministicRabinAutomaton) {

    return impl(deterministicRabinAutomaton, false);
  }

  private static <S> Result<Automaton<S, ParityAcceptance>, List<Set<S>>>
    impl(Automaton<S, ? extends RabinAcceptance> drw, boolean shortCircuit) {

    assert drw.is(Automaton.Property.DETERMINISTIC);

    if (drw.initialStates().isEmpty()) {
      return Result.success(
        EmptyAutomaton.of(
          drw.atomicPropositions(),
          new ParityAcceptance(0, ParityAcceptance.Parity.MIN_EVEN)));
    }

    var parityAcceptanceConditionTable
      = computeParityAcceptanceCondition(drw, shortCircuit);

    if (parityAcceptanceConditionTable.type() == Result.Type.FAILURE) {
      return parityAcceptanceConditionTable.propagateFailure();
    }

    var table = parityAcceptanceConditionTable.success();

    int largestPriority = table.values().stream().max(Integer::compareTo).orElse(2);

    var dpw = HashMapAutomaton.<S, ParityAcceptance>create(
      drw.atomicPropositions(),
      drw.factory(),
      new ParityAcceptance(largestPriority + 1, ParityAcceptance.Parity.MIN_EVEN));

    dpw.initialStates(Set.of(drw.onlyInitialState()));

    for (S drwState : drw.states()) {
      dpw.addState(drwState);
      drw.edgeMap(drwState).forEach((edge, valuationSet) ->
        dpw.addEdge(drwState, valuationSet, edge.withAcceptance(table.get(drwState, edge))));
    }

    dpw.trim();

    assert dpw.is(Automaton.Property.DETERMINISTIC);
    assert dpw.acceptance().isWellFormedAutomaton(dpw);

    return Result.success(AcceptanceOptimizations.optimize(dpw));
  }

  /**
   * Theorem 7.
   */
  private static <S> Result<Table<S, Edge<S>, Integer>, List<Set<S>>>
    computeParityAcceptanceCondition(
      Automaton<S, ? extends RabinAcceptance> automaton, boolean shortCircuit) {

    Map<S, Set<Edge<S>>> transitionMap = toTransitionMap(automaton);
    List<RabinPair> rabinAcceptanceCondition = RabinPair.of(automaton.acceptance());

    var hopelessTransitionMap = hopelessStatesAndEdges(transitionMap, rabinAcceptanceCondition);
    var hopefulTransitionMap = transitionMapDifference(transitionMap, hopelessTransitionMap);

    List<Table<S, Edge<S>, Integer>> successes = new ArrayList<>();
    List<Set<S>> failures = new ArrayList<>();

    for (Set<S> scc : SccDecomposition.of(hopefulTransitionMap.keySet(),
      x -> Edges.successors(hopefulTransitionMap.get(x))).sccs()) {

      var sccResult = computeParityAcceptanceCondition(
        Maps.filterKeys(hopefulTransitionMap, scc::contains),
        rabinAcceptanceCondition, shortCircuit);

      if (sccResult.type() == Result.Type.FAILURE) {
        if (shortCircuit) {
          return sccResult;
        }

        failures.addAll(sccResult.failure());
      } else {
        successes.add(sccResult.success());
      }
    }

    if (!failures.isEmpty()) {
      return Result.failure(failures);
    }

    successes.add(hopelessTransitionMapToTable(hopelessTransitionMap));
    return Result.success(combineAcceptanceConditions(transitionMap, successes));
  }

  /**
   * We assume that localStates are hopeless-free and form a MSCC.
   * Construction from Lemma 6 (plus code-path for failed executions)
   */
  private static <S> Result<Table<S, Edge<S>, Integer>, List<Set<S>>>
    computeParityAcceptanceCondition(
    Map<S, Set<Edge<S>>> transitionMap, List<RabinPair> rabinAcceptanceCondition,
    boolean shortCircuit) {

    assert !rabinAcceptanceCondition.isEmpty();

    if (rabinAcceptanceCondition.size() == 1) {
      Table<S, Edge<S>, Integer> parityCondition = HashBasedTable.create();

      var rabinPair = rabinAcceptanceCondition.get(0);

      transitionMap.forEach((state, edges) -> {
        for (Edge<S> edge : edges) {
          int colour;

          if (rabinPair.isRejecting(edge)) {
            colour = 1;
          } else if (edge.colours().contains(rabinPair.goodMark())) {
            colour = 2;
          } else {
            colour = 3;
          }

          parityCondition.put(state, edge, colour);
        }
      });

      return Result.success(parityCondition);
    }

    assert !transitionMap.isEmpty();
    assert SccDecomposition.of(transitionMap.keySet(),
      state -> Edges.successors(transitionMap.get(state))).sccs().size() == 1;
    assert hopelessStatesAndEdges(transitionMap, rabinAcceptanceCondition).isEmpty();

    int emptyBadSet = emptyBadSetIndex(transitionMap, rabinAcceptanceCondition);

    // Automaton is not DPA-type, since there is no empty bad set for the given states.
    if (emptyBadSet < 0) {
      return Result.failure(List.of(transitionMap.keySet()));
    }

    // Rabin pair used to split the acceptance condition.
    RabinPair rabinPairWithEmptyBadSet = rabinAcceptanceCondition.get(emptyBadSet);

    // Compute gamma_1.
    var updatedRabinAcceptanceCondition = new ArrayList<>(rabinAcceptanceCondition);
    updatedRabinAcceptanceCondition.remove(emptyBadSet);
    updatedRabinAcceptanceCondition.replaceAll(x ->
      x.withBadMark(rabinPairWithEmptyBadSet.goodMark()));

    var hopelessTransitionMap
      = hopelessStatesAndEdges(transitionMap, updatedRabinAcceptanceCondition);
    var hopefulTransitionMap = transitionMapDifference(transitionMap, hopelessTransitionMap);

    List<Table<S, Edge<S>, Integer>> successes = new ArrayList<>();
    List<Set<S>> failures = new ArrayList<>();

    for (Set<S> scc : SccDecomposition.of(hopefulTransitionMap.keySet(),
      x -> Edges.successors(hopefulTransitionMap.get(x))).sccs()) {
      var sccResult = computeParityAcceptanceCondition(
        Maps.filterKeys(hopefulTransitionMap, scc::contains),
        updatedRabinAcceptanceCondition, shortCircuit);

      if (sccResult.type() == Result.Type.FAILURE) {
        if (shortCircuit) {
          return sccResult;
        }

        failures.addAll(sccResult.failure());
      } else {
        successes.add(sccResult.success());
      }
    }

    if (!failures.isEmpty()) {
      return Result.failure(failures);
    }

    successes.add(hopelessTransitionMapToTable(hopelessTransitionMap));

    var gamma1 = combineAcceptanceConditions(transitionMap, successes);

    // Compute gamma and gamma2.
    Table<S, Edge<S>, Integer> gamma = HashBasedTable.create();

    for (Table.Cell<S, Edge<S>, Integer> gamma1Cell : gamma1.cellSet()) {
      S state = Objects.requireNonNull(gamma1Cell.getRowKey());
      Edge<S> edge = Objects.requireNonNull(gamma1Cell.getColumnKey());

      assert !rabinPairWithEmptyBadSet.isRejecting(edge);

      if (rabinPairWithEmptyBadSet.isAccepting(edge)) {
        gamma.put(state, edge, 2);
      } else {
        gamma.put(state, edge, gamma1Cell.getValue() + 2);
      }
    }

    return Result.success(gamma);
  }

  private static <S> boolean isUniqueAssignment(
    List<Table<S, Edge<S>, Integer>> parityAcceptanceConditions) {

    Table<S, Edge<S>, Integer> combinedTable = HashBasedTable.create();

    for (var table : parityAcceptanceConditions) {
      for (var cell : table.cellSet()) {
        if (combinedTable.put(cell.getRowKey(), cell.getColumnKey(), cell.getValue()) != null) {
          return false;
        }
      }
    }

    return true;
  }

  private static <S> boolean isValidAssignment(
    Map<S, Set<Edge<S>>> transitionMap, Table<S, Edge<S>, Integer> parityAcceptanceCondition) {

    for (var entry : transitionMap.entrySet()) {
      for (var value : entry.getValue()) {
        if (!parityAcceptanceCondition.contains(entry.getKey(), value)) {
          assert false : "Missing cell for (" + entry + ", " + value + ")";
          return false;
        }
      }
    }

    for (var cell : parityAcceptanceCondition.cellSet()) {
      if (!transitionMap.get(cell.getRowKey()).contains(cell.getColumnKey())) {
        return false;
      }
    }

    return true;
  }

  private static <S> Table<S, Edge<S>, Integer> hopelessTransitionMapToTable(
    Map<S, Set<Edge<S>>>  hopelessTransitionMap) {

    Table<S, Edge<S>, Integer> table = HashBasedTable.create();
    hopelessTransitionMap.forEach((
      state, edges) -> edges.forEach(
        edge -> table.put(state, edge, 1)));

    return table;
  }

  // Implementation of Lemma 4
  private static <S> Table<S, Edge<S>, Integer> combineAcceptanceConditions(
    Map<S, Set<Edge<S>>> transitionMap,
    List<Table<S, Edge<S>, Integer>> parityAcceptanceConditions) {

    assert isUniqueAssignment(parityAcceptanceConditions);

    Table<S, Edge<S>, Integer> parityAcceptanceCondition = HashBasedTable.create();
    parityAcceptanceConditions.forEach(parityAcceptanceCondition::putAll);

    assert isValidAssignment(transitionMap, parityAcceptanceCondition);

    return parityAcceptanceCondition;
  }

  // TODO: Migrate return type to BitSet.
  private static <S> int emptyBadSetIndex(
    Map<S, Set<Edge<S>>> transitionMap, List<RabinPair> rabinAcceptanceCondition) {

    BitSet seenIndices = new BitSet();

    transitionMap.values().forEach(
      edges -> edges.forEach(
        edge -> edge.colours().forEach((IntConsumer) seenIndices::set)));

    for (int i = 0, s = rabinAcceptanceCondition.size(); i < s; i++) {
      var pair = rabinAcceptanceCondition.get(i);

      if (pair.badMarks().stream().noneMatch(seenIndices::get)) {
        return i;
      }
    }

    return -1;
  }

  public static <S> Set<S> hopelessStates(Automaton<S, ? extends RabinAcceptance> automaton) {
    return hopelessStatesAndEdges(toTransitionMap(automaton),
      RabinPair.of(automaton.acceptance())).keySet();
  }

  static <S> Map<S, Set<Edge<S>>> hopelessStatesAndEdges(
    Map<S, Set<Edge<S>>> transitionMap, List<RabinPair> rabinAcceptanceCondition) {

    var hopelessTransitionMap = new HashMap<S, Set<Edge<S>>>(transitionMap.size());
    transitionMap.forEach((state, edges) -> hopelessTransitionMap.put(state, new HashSet<>(edges)));

    var successorFunction = toSuccessorFunction(transitionMap);
    var sccDecomposition = SccDecomposition.of(transitionMap.keySet(), successorFunction);

    for (Set<S> scc : sccDecomposition.sccs()) {
      // Need to deal with transient states -> implicit hopeless.

      for (RabinPair rabinPair : rabinAcceptanceCondition) {

        var filteredSuccessorFunction = toSuccessorFunction(
          transitionMap, Predicate.not(rabinPair::isRejecting));

        for (Set<S> filteredScc : SccDecomposition.of(scc, filteredSuccessorFunction).sccs()) {

          Map<S, Set<Edge<S>>> filteredSccTransitionMap = new AbstractMap<S, Set<Edge<S>>>() {
            @Override
            public Set<Entry<S, Set<Edge<S>>>> entrySet() {
              return new AbstractSet<>() {
                @Override
                public Iterator<Entry<S, Set<Edge<S>>>> iterator() {
                  return filteredScc.stream()
                    .map(state -> Map.entry(
                      state,
                      Sets.filter(
                        transitionMap.get(state),
                        x -> filteredScc.contains(x.successor()) && !rabinPair.isRejecting(x))))
                    .iterator();
                }

                @Override
                public int size() {
                  return filteredScc.size();
                }
              };
            }
          };

          boolean acceptingEdgeInFilteredScc = filteredSccTransitionMap
            .entrySet()
            .stream()
            .anyMatch(entry -> entry.getValue().stream().anyMatch(rabinPair::isAccepting));

          if (acceptingEdgeInFilteredScc) {
            filteredSccTransitionMap.forEach((state, edges) -> {
              var set = hopelessTransitionMap.get(state);

              if (set != null) {
                set.removeAll(edges);

                if (set.isEmpty()) {
                  hopelessTransitionMap.remove(state);
                }
              }
            });
          }
        }
      }
    }

    return hopelessTransitionMap;
  }

  // NB: this is not compatible SetMulitmap.
  static <S> Map<S, Set<Edge<S>>> transitionMapDifference(
    Map<S, Set<Edge<S>>> map1, Map<S, Set<Edge<S>>> map2) {
    Map<S, Set<Edge<S>>> differenceMap = new HashMap<>(map1.size());
    map1.forEach((state, edges) -> differenceMap.put(state, new HashSet<>(edges)));

    map2.forEach((state, edges) -> {
      var differenceEdges = differenceMap.get(state);

      if (differenceEdges != null) {
        differenceEdges.removeAll(edges);

        if (differenceEdges.isEmpty()) {
          differenceMap.remove(state);
        }
      }
    });

    return differenceMap;
  }

  static <S> Map<S, Set<Edge<S>>> toTransitionMap(Automaton<S, ?> automaton) {
    return new AbstractMap<S, Set<Edge<S>>>() {
      @Override
      public Set<Entry<S, Set<Edge<S>>>> entrySet() {
        return new AbstractSet<>() {
          @Override
          public Iterator<Entry<S, Set<Edge<S>>>> iterator() {
            return automaton.states().stream()
              .map(state -> Map.entry(state, automaton.edges(state)))
              .iterator();
          }

          @Override
          public int size() {
            return automaton.states().size();
          }
        };
      }

      @Override
      public int size() {
        return automaton.states().size();
      }

      @Override
      public boolean containsKey(Object key) {
        return automaton.states().contains(key);
      }

      @Override
      public Set<Edge<S>> get(Object key) {
        return automaton.edges((S) key);
      }

      @Override
      public Set<S> keySet() {
        return automaton.states();
      }
    };
  }

  static <S> SuccessorFunction<S> toSuccessorFunction(Map<S, Set<Edge<S>>> transitionMap) {
    return x -> Edges.successors(transitionMap.get(x));
  }

  static <S> SuccessorFunction<S> toSuccessorFunction(Map<S, Set<Edge<S>>> transitionMap,
    Predicate<Edge<S>> edgeFilter) {
    return state -> transitionMap.get(state).stream()
      .filter(edgeFilter)
      .map(Edge::successor)
      .collect(Collectors.toList());
  }

  @AutoValue
  abstract static class RabinPair {
    abstract int goodMark();

    abstract Set<Integer> badMarks();

    static RabinPair of(GeneralizedRabinAcceptance.RabinPair rabinPair) {
      return new AutoValue_ParityTypeness_RabinPair(
        rabinPair.infSet(), Set.of(rabinPair.finSet()));
    }

    static List<RabinPair> of(RabinAcceptance acceptance) {
      return acceptance.pairs().stream()
        .map(RabinPair::of)
        .collect(Collectors.toUnmodifiableList());
    }

    RabinPair withBadMark(int badMark) {
      return new AutoValue_ParityTypeness_RabinPair(goodMark(),
        Sets.union(Set.of(badMark), badMarks()).immutableCopy());
    }

    <S> boolean isAccepting(Edge<S> edge) {
      return edge.colours().contains(goodMark())
        && Collections.disjoint(edge.colours(), badMarks());
    }

    <S> boolean isRejecting(Edge<S> edge) {
      return !Collections.disjoint(edge.colours(), badMarks());
    }
  }
}
