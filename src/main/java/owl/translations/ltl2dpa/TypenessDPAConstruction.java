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

package owl.translations.ltl2dpa;

import static owl.automaton.BooleanOperations.deterministicComplement;
import static owl.automaton.BooleanOperations.deterministicProduct;

import com.google.auto.value.AutoValue;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.EmptyAutomaton;
import owl.automaton.HashMapAutomaton;
import owl.automaton.MutableAutomaton;
import owl.automaton.SuccessorFunction;
import owl.automaton.Views;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.acceptance.optimization.AcceptanceOptimizations;
import owl.automaton.algorithm.SccDecomposition;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.ltl.LabelledFormula;
import owl.run.Environment;
import owl.translations.ltl2dra.SymmetricDRAConstruction;

public class TypenessDPAConstruction<S> implements
  Function<LabelledFormula, Automaton<?, ParityAcceptance>> {

  // add portfolio.
  private final Function<LabelledFormula, Automaton<S, RabinAcceptance>> draConstruction;

  public TypenessDPAConstruction(
    Function<LabelledFormula, Automaton<S, RabinAcceptance>> draConstruction) {
    this.draConstruction = draConstruction;
  }

  public static TypenessDPAConstruction<owl.translations.ltl2dra.SymmetricRankingState>
    of(Environment environment) {

    return new TypenessDPAConstruction<>(
      SymmetricDRAConstruction.of(environment, RabinAcceptance.class, true));
  }

  @Override
  public Automaton<?, ParityAcceptance> apply(LabelledFormula formula) {

    var dra1 = draConstruction.apply(formula);
    var dpa1Optional = typeness(dra1);

    var dra2 = draConstruction.apply(formula.not());
    var dpa2Optional = typeness(dra2);

    // Since none of the DRAs is DPA-type, we need the build product that is guaranteed to be
    // DPA-type.
    if (dpa1Optional.isEmpty() && dpa2Optional.isEmpty()) {
      var product = deterministicProduct(
        dra1, Views.transitionStructure(dra2), dra1.acceptance(), true);
      return typeness(product).orElseThrow(() -> new AssertionError("Internal Error."));
    }

    if (dpa1Optional.isEmpty()) {
      return deterministicComplement(dpa2Optional.get(), ParityAcceptance.class);
    }

    if (dpa2Optional.isEmpty()) {
      return dpa1Optional.get();
    }

    var dpa1 = dpa1Optional.get();
    var dpa2 = deterministicComplement(dpa2Optional.get(), ParityAcceptance.class);

    if (dpa1.size() < dpa2.size()) {
      return dpa1;
    }

    if (dpa2.size() < dpa1.size()) {
      return dpa2;
    }

    if (dpa1.acceptance().acceptanceSets() <= dpa2.acceptance().acceptanceSets()) {
      return dpa1;
    }

    return dpa2;
  }

  private static <S> Optional<Automaton<S, ParityAcceptance>> typeness(
    Automaton<S, RabinAcceptance> deterministicRabinAutomaton) {

    assert deterministicRabinAutomaton.is(Automaton.Property.DETERMINISTIC);

    if (deterministicRabinAutomaton.initialStates().isEmpty()) {
      return Optional.of(
        EmptyAutomaton.of(
          deterministicRabinAutomaton.factory(),
          new ParityAcceptance(0, ParityAcceptance.Parity.MIN_EVEN)));
    }

    var parityAcceptanceConditionTable
      = computeParityAcceptanceCondition(deterministicRabinAutomaton);

    if (parityAcceptanceConditionTable == null) {
      return Optional.empty();
    }

    int largestPriority = parityAcceptanceConditionTable.values()
      .stream().mapToInt(Integer::intValue).max().orElse(2);
    var parityAcceptanceCondition
      = new ParityAcceptance(largestPriority + 1, ParityAcceptance.Parity.MIN_EVEN);

    MutableAutomaton<S, ParityAcceptance> deterministicParityAutomaton
      = HashMapAutomaton.of(parityAcceptanceCondition, deterministicRabinAutomaton.factory());

    deterministicParityAutomaton.initialStates(
      Set.of(deterministicRabinAutomaton.onlyInitialState()));
    deterministicRabinAutomaton.accept((Automaton.EdgeMapVisitor<S>) (state, edgeMap) -> {
      deterministicParityAutomaton.addState(state);
      edgeMap.forEach((edge, valuationSet) -> deterministicParityAutomaton.addEdge(
        state,
        valuationSet,
        edge.withAcceptance(
          Objects.requireNonNullElse(parityAcceptanceConditionTable.get(state, edge), 1))));
    });
    deterministicParityAutomaton.trim();

    assert deterministicParityAutomaton.is(Automaton.Property.DETERMINISTIC);
    assert parityAcceptanceCondition.isWellFormedAutomaton(deterministicParityAutomaton);
    return Optional.of(AcceptanceOptimizations.optimize(deterministicParityAutomaton));
  }

  /**
   * Theorem 7.
   */
  @Nullable
  private static <S> Table<S, Edge<S>, Integer> computeParityAcceptanceCondition(
    Automaton<S, RabinAcceptance> automaton) {

    Map<S, Set<Edge<S>>> transitionMap = toTransitionMap(automaton);
    List<RabinPair> rabinAcceptanceCondition = RabinPair.of(automaton.acceptance());

    var hopelessTransitionMap = hopelessStatesAndEdges(transitionMap, rabinAcceptanceCondition);
    var hopefulTransitionMap = transitionMapDifference(transitionMap, hopelessTransitionMap);

    List<Table<S, Edge<S>, Integer>> sccParityConditions = new ArrayList<>();

    for (Set<S> scc : SccDecomposition.of(hopefulTransitionMap.keySet(),
      x -> Edges.successors(hopefulTransitionMap.get(x))).sccs()) {
      var sccParityCondition = computeParityAcceptanceCondition(
        Maps.filterKeys(hopefulTransitionMap, scc::contains),
        rabinAcceptanceCondition);

      if (sccParityCondition == null) {
        return null;
      }

      sccParityConditions.add(sccParityCondition);
    }

    sccParityConditions.add(hopelessTransitionMapToTable(hopelessTransitionMap));

    return combineAcceptanceConditions(transitionMap, sccParityConditions);
  }

  /**
   * We assume that localStates are hopeless-free and form a MSCC.
   * Construction from Lemma 6 (plus code-path for failed executions)
   */
  @Nullable
  private static <S> Table<S, Edge<S>, Integer> computeParityAcceptanceCondition(
    Map<S, Set<Edge<S>>> transitionMap, List<RabinPair> rabinAcceptanceCondition) {

    assert !rabinAcceptanceCondition.isEmpty();

    if (rabinAcceptanceCondition.size() == 1) {
      Table<S, Edge<S>, Integer> parityCondition = HashBasedTable.create();

      var rabinPair = rabinAcceptanceCondition.get(0);

      transitionMap.forEach((state, edges) -> {
        for (Edge<S> edge : edges) {
          int colour;

          if (rabinPair.isRejecting(edge)) {
            colour = 1;
          } else if (edge.inSet(rabinPair.goodMark())) {
            colour = 2;
          } else {
            colour = 3;
          }

          parityCondition.put(state, edge, colour);
        }
      });

      return parityCondition;
    }

    assert !transitionMap.isEmpty();
    assert SccDecomposition.of(transitionMap.keySet(),
      state -> Edges.successors(transitionMap.get(state))).sccs().size() == 1;
    assert hopelessStatesAndEdges(transitionMap, rabinAcceptanceCondition).isEmpty();

    int emptyBadSet = emptyBadSetIndex(transitionMap, rabinAcceptanceCondition);

    // Automaton is not DPA-type, since there is no empty bad set for the given states.
    if (emptyBadSet < 0) {
      return null;
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

    List<Table<S, Edge<S>, Integer>> sccParityConditions = new ArrayList<>();

    for (Set<S> scc : SccDecomposition.of(hopefulTransitionMap.keySet(),
      x -> Edges.successors(hopefulTransitionMap.get(x))).sccs()) {
      var sccParityCondition = computeParityAcceptanceCondition(
        Maps.filterKeys(hopefulTransitionMap, scc::contains),
        updatedRabinAcceptanceCondition);

      if (sccParityCondition == null) {
        return null;
      }

      sccParityConditions.add(sccParityCondition);
    }

    sccParityConditions.add(hopelessTransitionMapToTable(hopelessTransitionMap));

    var gamma1 = combineAcceptanceConditions(transitionMap, sccParityConditions);

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

    return gamma;
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
        edge -> edge.forEachAcceptanceSet(seenIndices::set)));

    for (int i = 0, s = rabinAcceptanceCondition.size(); i < s; i++) {
      var pair = rabinAcceptanceCondition.get(i);

      if (pair.badMarks().stream().noneMatch(seenIndices::get)) {
        return i;
      }
    }

    return -1;
  }

  static <S> Set<S> hopelessStates(Automaton<S, RabinAcceptance> automaton) {
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
              return new AbstractSet<Entry<S, Set<Edge<S>>>>() {
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
      return new AutoValue_TypenessDPAConstruction_RabinPair(
        rabinPair.infSet(), Set.of(rabinPair.finSet()));
    }

    static List<RabinPair> of(RabinAcceptance acceptance) {
      return acceptance.pairs().stream()
        .map(RabinPair::of)
        .collect(Collectors.toUnmodifiableList());
    }

    RabinPair withBadMark(int badMark) {
      return new AutoValue_TypenessDPAConstruction_RabinPair(goodMark(),
        Sets.union(Set.of(badMark), badMarks()).immutableCopy());
    }

    <S> boolean isAccepting(Edge<S> edge) {
      return edge.inSet(goodMark()) && !isRejecting(edge);
    }

    <S> boolean isRejecting(Edge<S> edge) {
      return badMarks().stream().anyMatch(edge::inSet);
    }
  }
}
