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

package owl.automaton;

import static com.google.common.base.Preconditions.checkArgument;
import static owl.automaton.Automaton.Property.DETERMINISTIC;
import static owl.automaton.acceptance.OmegaAcceptanceCast.isInstanceOf;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import jhoafparser.extensions.BooleanExpressions;
import owl.automaton.AbstractImmutableAutomaton.SemiDeterministicEdgesAutomaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedCoBuchiAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.OmegaAcceptanceCast;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.NullablePair;
import owl.collections.Pair;
import owl.collections.ValuationSet;
import owl.collections.ValuationTree;
import owl.collections.ValuationTrees;
import owl.factories.ValuationSetFactory;

/**
 * This class provides standard boolean operations (union, intersection) on automata. The returned
 * automata are live-views and are constructed on-the-fly.
 */
public final class BooleanOperations {

  private BooleanOperations() {}

  // TODO: Migrate to deterministicUnion + Inf set simplifications.
  @Deprecated
  public static <S> Automaton<List<S>, BuchiAcceptance> unionBuchi(
    List<Automaton<S, BuchiAcceptance>> automata) {
    checkArgument(!automata.isEmpty(), "No automaton was passed.");
    assert automata.stream().allMatch(x -> x.is(DETERMINISTIC));

    ValuationSetFactory factory = commonAlphabet(
      automata.stream().map(Automaton::factory).collect(Collectors.toList()), true).fst();
    List<Automaton<S, ? extends GeneralizedBuchiAcceptance>> buchi = new ArrayList<>(automata);

    return new SemiDeterministicEdgesAutomaton<>(factory,
      Set.of(buchi.stream()
        .map(Automaton::onlyInitialState)
        .collect(Collectors.toUnmodifiableList())), BuchiAcceptance.INSTANCE) {

      @Override
      public Edge<List<S>> edge(List<S> productState, BitSet valuation) {
        var productSuccessor = new ArrayList<S>(productState.size());
        BitSet acceptanceSets = new BitSet();

        for (int i = 0; i < productState.size(); i++) {
          S state = productState.get(i);

          if (state == null) {
            productSuccessor.add(null);
            continue;
          }

          var edge = buchi.get(i).edge(state, valuation);

          if (edge == null) {
            productSuccessor.add(null);
            continue;
          }

          productSuccessor.add(edge.successor());

          assert edge.largestAcceptanceSet() <= 0;

          if (edge.hasAcceptanceSets()) {
            acceptanceSets.set(0);
          }
        }

        productSuccessor.trimToSize();
        return Edge.of(Collections.unmodifiableList(productSuccessor), acceptanceSets);
      }
    };
  }

  public static <S, A extends OmegaAcceptance> Automaton<S, A> deterministicComplement(
    Automaton<S, ?> automaton,
    @Nullable S trapState,
    Class<A> expectedAcceptance) {
    var completeAutomaton = trapState == null ? automaton : Views.complete(automaton, trapState);

    checkArgument(completeAutomaton.is(Automaton.Property.COMPLETE), "Automaton is not complete.");
    checkArgument(!completeAutomaton.initialStates().isEmpty(), "Automaton is empty.");
    // Check is too costly.
    // checkArgument(completeAutomaton.is(DETERMINISTIC), "Automaton is not deterministic.");

    OmegaAcceptance acceptance = completeAutomaton.acceptance();
    OmegaAcceptance complementAcceptance = null;

    if (acceptance instanceof BuchiAcceptance) {
      checkArgument(isInstanceOf(CoBuchiAcceptance.class, expectedAcceptance));
      complementAcceptance = CoBuchiAcceptance.INSTANCE;
    } else if (acceptance instanceof CoBuchiAcceptance) {
      checkArgument(isInstanceOf(BuchiAcceptance.class, expectedAcceptance));
      complementAcceptance = BuchiAcceptance.INSTANCE;
    } else if (acceptance instanceof GeneralizedBuchiAcceptance) {
      checkArgument(isInstanceOf(GeneralizedCoBuchiAcceptance.class, expectedAcceptance));
      var castedAcceptance = (GeneralizedBuchiAcceptance) acceptance;
      complementAcceptance = GeneralizedCoBuchiAcceptance.of(castedAcceptance.size);
    } else if (acceptance instanceof GeneralizedCoBuchiAcceptance) {
      checkArgument(isInstanceOf(GeneralizedBuchiAcceptance.class, expectedAcceptance));
      var castedAcceptance = (GeneralizedCoBuchiAcceptance) acceptance;
      complementAcceptance = GeneralizedBuchiAcceptance.of(castedAcceptance.size);
    } else if (acceptance instanceof ParityAcceptance) {
      checkArgument(isInstanceOf(ParityAcceptance.class, expectedAcceptance));
      complementAcceptance = ((ParityAcceptance) automaton.acceptance()).complement();
    }

    if (complementAcceptance == null) {
      throw new UnsupportedOperationException("Cannot complement to " + expectedAcceptance);
    }

    return OmegaAcceptanceCast.cast(
      new ReplacedAcceptanceConditionView<>(completeAutomaton, complementAcceptance),
      expectedAcceptance);
  }

  public static <S1, S2> Automaton<Pair<S1, S2>, ?>
    intersection(Automaton<S1, ?> automaton1, Automaton<S2, ?> automaton2) {
    return intersection(automaton1, automaton2, false);
  }

  public static <S1, S2> Automaton<Pair<S1, S2>, ?>
    intersection(Automaton<S1, ?> automaton1, Automaton<S2, ?> automaton2,
    boolean ignoreSymbolicFactoryMismatch) {

    var factory = commonAlphabet(
      List.of(automaton1.factory(), automaton2.factory()),
      ignoreSymbolicFactoryMismatch);

    var intersection = new PairIntersectionAutomaton<>(automaton1, automaton2, factory);
    return OmegaAcceptanceCast.castHeuristically(intersection);
  }

  public static <S> Automaton<List<S>, ?> intersection(List<Automaton<S, ?>> automata) {
    return intersection(automata, false);
  }

  public static <S> Automaton<List<S>, ?>
    intersection(List<Automaton<S, ?>> automata, boolean ignoreSymbolicFactoryMismatch) {

    checkArgument(!automata.isEmpty(), "Automata is empty.");
    var factory = commonAlphabet(automata, ignoreSymbolicFactoryMismatch);
    var intersection = new ListIntersectionAutomaton<>(automata, factory);
    return OmegaAcceptanceCast.castHeuristically(intersection);
  }

  public static <S1, S2> Automaton<NullablePair<S1, S2>, ?>
    deterministicUnion(Automaton<S1, ?> automaton1, Automaton<S2, ?> automaton2) {

    return deterministicUnion(automaton1, automaton2, false);
  }

  public static <S1, S2> Automaton<NullablePair<S1, S2>, ?>
    deterministicUnion(Automaton<S1, ?> automaton1, Automaton<S2, ?> automaton2,
    boolean ignoreSymbolicFactoryMismatch) {

    var factory = commonAlphabet(
      List.of(automaton1.factory(), automaton2.factory()),
      ignoreSymbolicFactoryMismatch);

    var union = new NullablePairDeterministicUnionAutomaton<>(automaton1, automaton2, factory);
    return OmegaAcceptanceCast.castHeuristically(union);
  }

  public static <S> Automaton<Map<Integer, S>, ?>
    deterministicUnion(List<Automaton<S, ?>> automata) {

    return deterministicUnion(automata, false);
  }

  public static <S> Automaton<Map<Integer, S>, ?>
    deterministicUnion(List<Automaton<S, ?>> automata, boolean ignoreSymbolicFactoryMismatch) {

    checkArgument(!automata.isEmpty(), "Automata is empty.");
    var factory = commonAlphabet(automata, ignoreSymbolicFactoryMismatch);
    var union = new MapDeterministicUnionAutomaton<>(automata, factory);
    return OmegaAcceptanceCast.castHeuristically(union);
  }

  // Private implementations

  private static <S> Pair<ValuationSetFactory, Boolean> commonAlphabet(
    List<Automaton<S, ?>> automata, boolean ignoreSymbolicFactoryMismatch) {

    return commonAlphabet(
      automata.stream().map(Automaton::factory).collect(Collectors.toList()),
      ignoreSymbolicFactoryMismatch);
  }

  private static Pair<ValuationSetFactory, Boolean> commonAlphabet(
    Iterable<ValuationSetFactory> factories, boolean ignoreSymbolicFactoryMismatch) {

    Iterator<ValuationSetFactory> iterator = factories.iterator();
    ValuationSetFactory factory = iterator.next();
    boolean symbolicOperationsAllowed = true;

    while (iterator.hasNext()) {
      ValuationSetFactory otherFactory = iterator.next();

      symbolicOperationsAllowed = symbolicOperationsAllowed && factory.equals(otherFactory);

      if (!ignoreSymbolicFactoryMismatch && !symbolicOperationsAllowed) {
        throw new IllegalArgumentException("Symbolic factories are incompatible.");
      }

      if (Collections.indexOfSubList(otherFactory.alphabet(), factory.alphabet()) == 0) {
        factory = otherFactory;
      } else if (Collections.indexOfSubList(factory.alphabet(), otherFactory.alphabet()) != 0) {
        throw new IllegalArgumentException("Could not find shared alphabet.");
      }
    }

    return Pair.of(factory, symbolicOperationsAllowed);
  }

  private static <A extends OmegaAcceptance> EmersonLeiAcceptance
    intersectionAcceptance(List<A> acceptanceConditions) {

    var intersectionConjuncts = new ArrayList<BooleanExpression<AtomAcceptance>>();
    int intersectionAcceptanceSets = 0;

    for (A acceptance : acceptanceConditions) {
      var shifted =
        BooleanExpressions.shift(acceptance.booleanExpression(), intersectionAcceptanceSets);
      intersectionConjuncts.addAll(BooleanExpressions.getConjuncts(shifted));
      intersectionAcceptanceSets += acceptance.acceptanceSets();
    }

    var intersectionExpression = BooleanExpressions.createConjunction(intersectionConjuncts);
    return new EmersonLeiAcceptance(intersectionAcceptanceSets, intersectionExpression);
  }

  private static class PairIntersectionAutomaton<S1, S2>
    extends AbstractImmutableAutomaton<Pair<S1, S2>, EmersonLeiAcceptance> {

    private final Automaton<S1, ?> automaton1;
    private final Automaton<S2, ?> automaton2;
    private final boolean symbolicOperationsAllowed;
    private final int acceptance1Sets;

    private PairIntersectionAutomaton(
      Automaton<S1, ?> automaton1,
      Automaton<S2, ?> automaton2,
      Pair<ValuationSetFactory, Boolean> factory) {

      super(factory.fst(),
        Pair.of(automaton1.initialStates(), automaton2.initialStates()),
        intersectionAcceptance(List.of(automaton1.acceptance(), automaton2.acceptance())));

      this.automaton1 = automaton1;
      this.automaton2 = automaton2;
      this.acceptance1Sets = automaton1.acceptance().acceptanceSets();
      this.symbolicOperationsAllowed = factory.snd();
    }

    @Override
    public Set<Edge<Pair<S1, S2>>> edges(Pair<S1, S2> state, BitSet valuation) {
      var edges1 = automaton1.edges(state.fst(), valuation);
      var edges2 = automaton2.edges(state.snd(), valuation);
      var edges = new HashSet<Edge<Pair<S1, S2>>>();

      for (var edge1 : edges1) {
        for (var edge2 : edges2) {
          edges.add(combine(edge1, edge2));
        }
      }

      return edges;
    }

    @Override
    public Map<Edge<Pair<S1, S2>>, ValuationSet> edgeMap(Pair<S1, S2> state) {
      if (!symbolicOperationsAllowed) {
        return this.edgeTree(state).inverse(factory());
      }

      var edgeMap1 = automaton1.edgeMap(state.fst());
      var edgeMap2 = automaton2.edgeMap(state.snd());
      var edgeMap = new HashMap<Edge<Pair<S1, S2>>, ValuationSet>();

      edgeMap1.forEach((edge1, valuationSet1) -> {
        edgeMap2.forEach((edge2, valuationSet2) -> {
          var intersection = valuationSet1.intersection(valuationSet2);

          if (!intersection.isEmpty()) {
            edgeMap.merge(combine(edge1, edge2), intersection, ValuationSet::union);
          }
        });
      });

      return edgeMap;
    }

    @Override
    public ValuationTree<Edge<Pair<S1, S2>>> edgeTree(Pair<S1, S2> state) {
      var edgeTree1 = automaton1.edgeTree(state.fst());
      var edgeTree2 = automaton2.edgeTree(state.snd());
      return ValuationTrees.cartesianProduct(edgeTree1, edgeTree2, this::combine);
    }

    @Override
    public List<PreferredEdgeAccess> preferredEdgeAccess() {
      return automaton1.preferredEdgeAccess();
    }

    private Edge<Pair<S1, S2>> combine(Edge<S1> edge1, Edge<S2> edge2) {
      BitSet acceptance = edge1.acceptanceSets();
      edge2.forEachAcceptanceSet((int set) -> acceptance.set(set + acceptance1Sets));
      return Edge.of(Pair.of(edge1.successor(), edge2.successor()), acceptance);
    }
  }

  private static class ListIntersectionAutomaton<S>
    extends AbstractImmutableAutomaton<List<S>, EmersonLeiAcceptance> {

    private final List<Automaton<S, ?>> automata;
    private final boolean symbolicOperationsAllowed;

    private ListIntersectionAutomaton(
      List<Automaton<S, ?>> automata,
      Pair<ValuationSetFactory, Boolean> factory) {

      super(factory.fst(),
        initialStates(automata),
        intersectionAcceptance(
          automata.stream().map(Automaton::acceptance).collect(Collectors.toList())));
      this.automata = List.copyOf(automata);
      this.symbolicOperationsAllowed = factory.snd();
    }

    private static <S> Set<List<S>> initialStates(List<Automaton<S, ?>> automata) {
      return Sets.cartesianProduct(
        automata.stream().map(Automaton::initialStates).collect(Collectors.toList()));
    }

    @Override
    public Set<Edge<List<S>>> edges(List<S> state, BitSet valuation) {
      List<Set<Edge<S>>> edges = new ArrayList<>();

      for (int i = 0, s = automata.size(); i < s; i++) {
        edges.add(automata.get(i).edges(state.get(i), valuation));
      }

      return Sets.cartesianProduct(edges).stream()
        .map(this::combine)
        .collect(Collectors.toSet());
    }

    @Override
    public Map<Edge<List<S>>, ValuationSet> edgeMap(List<S> state) {
      if (!symbolicOperationsAllowed) {
        return edgeTree(state).inverse(factory());
      }

      List<Set<Map.Entry<Edge<S>, ValuationSet>>> edgeMaps = new ArrayList<>();

      for (int i = 0, s = automata.size(); i < s; i++) {
        edgeMaps.add(automata.get(i).edgeMap(state.get(i)).entrySet());
      }

      var edgeMap = new HashMap<Edge<List<S>>, ValuationSet>();

      for (List<Map.Entry<Edge<S>, ValuationSet>> edgeList : Sets.cartesianProduct(edgeMaps)) {
        var intersection = edgeList.stream()
          .map(Map.Entry::getValue)
          .reduce(ValuationSet::intersection)
          .orElseThrow();

        if (!intersection.isEmpty()) {
          var list = edgeList.stream().map(Map.Entry::getKey).collect(Collectors.toList());
          edgeMap.merge(combine(list), intersection, ValuationSet::union);
        }
      }

      return edgeMap;
    }

    @Override
    public ValuationTree<Edge<List<S>>> edgeTree(List<S> state) {
      List<ValuationTree<Edge<S>>> edgeTrees = new ArrayList<>();

      for (int i = 0, s = automata.size(); i < s; i++) {
        edgeTrees.add(automata.get(i).edgeTree(state.get(i)));
      }

      return ValuationTrees
        .cartesianProduct(edgeTrees)
        .map((Set<List<Edge<S>>> x) -> x.stream()
          .map(this::combine)
          .collect(Collectors.toUnmodifiableSet()));
    }

    @Override
    public List<PreferredEdgeAccess> preferredEdgeAccess() {
      return automata.get(0).preferredEdgeAccess();
    }

    private Edge<List<S>> combine(List<Edge<S>> edges) {
      List<S> successors = new ArrayList<>();
      BitSet acceptance = new BitSet();

      int offset = 0;

      for (int i = 0, s = automata.size(); i < s; i++) {
        int offsetFinal = offset;

        var edge = edges.get(i);
        successors.add(edge.successor());
        edge.forEachAcceptanceSet(set -> acceptance.set(set + offsetFinal));

        offset += automata.get(i).acceptance().acceptanceSets();
      }

      return Edge.of(List.copyOf(successors), acceptance);
    }
  }

  private static <A extends OmegaAcceptance> EmersonLeiAcceptance
    unionAcceptance(List<A> acceptanceConditions) {

    var unionDisjuncts = new ArrayList<BooleanExpression<AtomAcceptance>>();
    int unionAcceptanceSets = 0;

    for (A acceptance : acceptanceConditions) {
      var shifted = BooleanExpressions.shift(acceptance.booleanExpression(), unionAcceptanceSets);
      unionDisjuncts.addAll(BooleanExpressions.getDisjuncts(shifted));
      unionAcceptanceSets += acceptance.acceptanceSets();
    }

    var unionExpression = BooleanExpressions.createDisjunction(unionDisjuncts);
    return new EmersonLeiAcceptance(unionAcceptanceSets, unionExpression);
  }

  private static class NullablePairDeterministicUnionAutomaton<S1, S2>
    extends SemiDeterministicEdgesAutomaton<NullablePair<S1, S2>, EmersonLeiAcceptance> {

    private final Automaton<S1, ?> automaton1;
    private final Automaton<S2, ?> automaton2;

    private NullablePairDeterministicUnionAutomaton(
      Automaton<S1, ?> automaton1,
      Automaton<S2, ?> automaton2,
      Pair<ValuationSetFactory, Boolean> factory) {

      super(factory.fst(),
        Set.of(initialState(automaton1, automaton2)),
        unionAcceptance(List.of(automaton1.acceptance(), automaton2.acceptance())));
      this.automaton1 = automaton1;
      this.automaton2 = automaton2;
    }

    private static <S1, S2> NullablePair<S1, S2> initialState(
      Automaton<S1, ?> automata1, Automaton<S2, ?> automata2) {
      S1 initialState1 = Iterables.getOnlyElement(automata1.initialStates(), null);
      S2 initialState2 = Iterables.getOnlyElement(automata2.initialStates(), null);
      return NullablePair.of(initialState1, initialState2);
    }

    @Override
    public Edge<NullablePair<S1, S2>> edge(NullablePair<S1, S2> state, BitSet valuation) {
      Edge<S1> edge1 = state.fst() == null ? null : automaton1.edge(state.fst(), valuation);
      Edge<S2> edge2 = state.snd() == null ? null : automaton2.edge(state.snd(), valuation);
      return combine(edge1, edge2);
    }

    @Nullable
    private Edge<NullablePair<S1, S2>> combine(@Nullable Edge<S1> edge1, @Nullable Edge<S2> edge2) {
      S1 successor1 = null;
      S2 successor2 = null;
      BitSet acceptance = new BitSet();

      if (edge1 == null && edge2 == null) {
        return null;
      }

      if (edge1 != null) {
        successor1 = edge1.successor();
        edge1.forEachAcceptanceSet(acceptance::set);
      }

      if (edge2 != null) {
        successor2 = edge2.successor();
        int offset = automaton1.acceptance().acceptanceSets();
        edge2.forEachAcceptanceSet(i -> acceptance.set(i + offset));
      }

      return Edge.of(NullablePair.of(successor1, successor2), acceptance);
    }
  }

  private static class MapDeterministicUnionAutomaton<S>
    extends SemiDeterministicEdgesAutomaton<Map<Integer, S>, EmersonLeiAcceptance> {

    private final List<Automaton<S, ?>> automata;

    private MapDeterministicUnionAutomaton(
      List<Automaton<S, ?>> automata,
      Pair<ValuationSetFactory, Boolean> factory) {

      super(factory.fst(),
        Set.of(initialState(automata)),
        unionAcceptance(automata.stream().map(Automaton::acceptance).collect(Collectors.toList())));
      this.automata = List.copyOf(automata);
    }

    private static <S> Map<Integer, S> initialState(List<Automaton<S, ?>> automata) {
      Map<Integer, S> initialStates = new HashMap<>();

      for (int i = 0, s = automata.size(); i < s; i++) {
        var localInitialStates = automata.get(i).initialStates();

        if (localInitialStates.isEmpty()) {
          continue;
        }

        if (localInitialStates.size() >= 2) {
          throw new IllegalArgumentException("Given automaton is not deterministic.");
        }

        initialStates.put(i, localInitialStates.iterator().next());
      }

      return Map.copyOf(initialStates);
    }

    @Override
    public Edge<Map<Integer, S>> edge(Map<Integer, S> state, BitSet valuation) {
      Map<Integer, Edge<S>> edges = new HashMap<>();

      state.forEach(
        (index, localState) -> edges.put(index, automata.get(index).edge(localState, valuation)));

      return combine(edges);
    }

    @Nullable
    private Edge<Map<Integer, S>> combine(Map<Integer, Edge<S>> edges) {
      Map<Integer, S> successor = new HashMap<>();
      BitSet acceptance = new BitSet();

      int offset = 0;

      for (int i = 0, s = automata.size(); i < s; i++) {
        var edge = edges.get(i);

        if (edge != null) {
          successor.put(i, edge.successor());
          int offsetFinal = offset;
          edge.forEachAcceptanceSet(set -> acceptance.set(set + offsetFinal));
        }

        offset += automata.get(i).acceptance().acceptanceSets();
      }

      if (successor.isEmpty()) {
        return null;
      }

      return Edge.of(Map.copyOf(successor), acceptance);
    }
  }

  private static class ReplacedAcceptanceConditionView<S, A extends OmegaAcceptance>
    implements Automaton<S, A> {

    private final A acceptance;
    private final Automaton<S, ?> backingAutomaton;

    private ReplacedAcceptanceConditionView(Automaton<S, ?> backingAutomaton, A acceptance) {
      this.acceptance = acceptance;
      this.backingAutomaton = backingAutomaton;
    }

    @Override
    public Set<Edge<S>> edges(S state, BitSet valuation) {
      return backingAutomaton.edges(state, valuation);
    }

    @Override
    public Map<Edge<S>, ValuationSet> edgeMap(S state) {
      return backingAutomaton.edgeMap(state);
    }

    @Override
    public ValuationTree<Edge<S>> edgeTree(S state) {
      return backingAutomaton.edgeTree(state);
    }

    @Override
    public List<PreferredEdgeAccess> preferredEdgeAccess() {
      return backingAutomaton.preferredEdgeAccess();
    }

    @Override
    public A acceptance() {
      return acceptance;
    }

    @Override
    public ValuationSetFactory factory() {
      return backingAutomaton.factory();
    }

    @Override
    public Set<S> initialStates() {
      return backingAutomaton.initialStates();
    }

    @Override
    public Set<S> states() {
      return backingAutomaton.states();
    }
  }
}
