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
import static owl.automaton.Automaton.Property.COMPLETE;
import static owl.automaton.acceptance.OmegaAcceptanceCast.isInstanceOf;
import static owl.logic.propositional.PropositionalFormula.Conjunction;
import static owl.logic.propositional.PropositionalFormula.Variable;

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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.automaton.AbstractImmutableAutomaton.SemiDeterministicEdgesAutomaton;
import owl.automaton.acceptance.AllAcceptance;
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
import owl.logic.propositional.PropositionalFormula;
import owl.logic.propositional.PropositionalFormula.Disjunction;

/**
 * This class provides standard boolean operations (union, intersection, complementation) on
 * automata. The returned automata are constructed on-the-fly and it assumed that the
 * underlying automata are not changed during the lifetime of the returned objects.
 */
public final class BooleanOperations {

  private BooleanOperations() {}

  public static <S, A extends OmegaAcceptance> Automaton<S, A> deterministicComplement(
    Automaton<S, ?> automaton,
    @Nullable S trapState,
    Class<A> expectedAcceptance) {
    var completeAutomaton = trapState == null ? automaton : Views.complete(automaton, trapState);

    checkArgument(completeAutomaton.is(COMPLETE), "Automaton is not complete.");
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
    } else if (isInstanceOf(EmersonLeiAcceptance.class, expectedAcceptance)) {
      complementAcceptance = new EmersonLeiAcceptance(
        acceptance.acceptanceSets(),
        PropositionalFormula.Negation.of(acceptance.booleanExpression()));
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

  public static <S, A extends OmegaAcceptance> Automaton<List<S>, ?>
    intersection(List<? extends Automaton<S, ?>> automata) {

    return intersection(automata, false);
  }

  public static <S> Automaton<List<S>, ?>
    intersection(List<? extends Automaton<S, ?>> automata,
      boolean ignoreSymbolicFactoryMismatch) {

    checkArgument(!automata.isEmpty(), "List of automata is empty.");
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

    Automaton<S1, ?> normalizedAutomaton1;
    Automaton<S2, ?> normalizedAutomaton2;

    // If all runs on automaton1 are accepting, transform to BuchiAcceptance to have access
    // to an rejecting acceptance set.
    if (automaton1.acceptance().rejectingSet().isEmpty()) {
      Automaton<S1, AllAcceptance> castedAutomaton = OmegaAcceptanceCast.cast(
        OmegaAcceptanceCast.castHeuristically(automaton1), AllAcceptance.class);
      normalizedAutomaton1 = OmegaAcceptanceCast.cast(castedAutomaton, BuchiAcceptance.class);
    } else {
      normalizedAutomaton1 = automaton1;
    }

    // If all runs on automaton2 are accepting, transform to BuchiAcceptance to have access
    // to an rejecting acceptance set.
    if (automaton2.acceptance().rejectingSet().isEmpty()) {
      Automaton<S2, AllAcceptance> castedAutomaton = OmegaAcceptanceCast.cast(
        OmegaAcceptanceCast.castHeuristically(automaton2), AllAcceptance.class);
      normalizedAutomaton2 = OmegaAcceptanceCast.cast(castedAutomaton, BuchiAcceptance.class);
    } else {
      normalizedAutomaton2 = automaton2;
    }

    var union = new NullablePairDeterministicUnionAutomaton<>(
      normalizedAutomaton1,
      normalizedAutomaton2,
      factory,
      normalizedAutomaton1.acceptance().rejectingSet().orElseThrow(),
      normalizedAutomaton2.acceptance().rejectingSet().orElseThrow());

    return OmegaAcceptanceCast.castHeuristically(union);
  }

  public static <S> Automaton<Map<Integer, S>, ?>
    deterministicUnion(List<? extends Automaton<S, ?>> automata) {

    return deterministicUnion(automata, false);
  }

  public static <S> Automaton<Map<Integer, S>, ?>
    deterministicUnion(List<? extends Automaton<S, ?>> automata,
      boolean ignoreSymbolicFactoryMismatch) {

    checkArgument(!automata.isEmpty(), "List of automata is empty.");
    var factory = commonAlphabet(automata, ignoreSymbolicFactoryMismatch);

    List<Automaton<S, ?>> automataCopy = new ArrayList<>(automata.size());
    List<BitSet> rejectingSets = new ArrayList<>(automata.size());

    automata.forEach(automaton -> {
      Automaton<S, ?> normalisedAutomaton;

      if (automaton.acceptance().rejectingSet().isEmpty()) {
        Automaton<S, AllAcceptance> castedAutomaton = OmegaAcceptanceCast.cast(
          OmegaAcceptanceCast.castHeuristically(automaton), AllAcceptance.class);
        normalisedAutomaton = OmegaAcceptanceCast.cast(castedAutomaton, BuchiAcceptance.class);
      } else {
        normalisedAutomaton = automaton;
      }

      automataCopy.add(normalisedAutomaton);
      rejectingSets.add(normalisedAutomaton.acceptance().rejectingSet().orElseThrow());
    });

    var union = new MapDeterministicUnionAutomaton<>(automataCopy, factory, rejectingSets);
    return OmegaAcceptanceCast.castHeuristically(union);
  }

  // Private implementations

  private static Pair<ValuationSetFactory, Boolean> commonAlphabet(
    List<? extends Automaton<?, ?>> automata, boolean ignoreSymbolicFactoryMismatch) {

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

      if (Collections.indexOfSubList(
        otherFactory.atomicPropositions(),
        factory.atomicPropositions()) == 0) {
        factory = otherFactory;
      } else if (Collections.indexOfSubList(
        factory.atomicPropositions(),
        otherFactory.atomicPropositions()) != 0) {
        throw new IllegalArgumentException("Could not find shared alphabet.");
      }
    }

    return Pair.of(factory, symbolicOperationsAllowed);
  }

  private static <A extends OmegaAcceptance> EmersonLeiAcceptance
    intersectionAcceptance(List<A> acceptanceConditions) {

    var intersectionConjuncts = new ArrayList<PropositionalFormula<Integer>>();
    int intersectionAcceptanceSets = 0;

    for (A acceptance : acceptanceConditions) {
      var fIntersectionAcceptanceSets = intersectionAcceptanceSets;
      var shiftedExpression = acceptance.booleanExpression()
        .substitute(x -> Optional.of(Variable.of(x + fIntersectionAcceptanceSets)));
      intersectionConjuncts.add(shiftedExpression);
      intersectionAcceptanceSets += acceptance.acceptanceSets();
    }

    var intersectionExpression = Conjunction.of(intersectionConjuncts);
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
        Pair.allPairs(automaton1.initialStates(), automaton2.initialStates()),
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

    private final List<? extends Automaton<S, ?>> automata;
    private final boolean symbolicOperationsAllowed;

    private ListIntersectionAutomaton(
      List<? extends Automaton<S, ?>> automata,
      Pair<ValuationSetFactory, Boolean> factory) {

      super(factory.fst(),
        initialStates(automata),
        intersectionAcceptance(
          automata.stream().map(Automaton::acceptance).collect(Collectors.toList())));
      this.automata = List.copyOf(automata);
      this.symbolicOperationsAllowed = factory.snd();
    }

    private static <S> Set<List<S>>
      initialStates(List<? extends Automaton<S, ?>> automata) {

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

    var unionDisjuncts = new ArrayList<PropositionalFormula<Integer>>();
    int unionAcceptanceSets = 0;

    for (A acceptance : acceptanceConditions) {
      var fUnionAcceptanceSets = unionAcceptanceSets;
      var shiftedExpression = acceptance.booleanExpression()
        .substitute(x -> Optional.of(Variable.of(x + fUnionAcceptanceSets)));
      unionDisjuncts.add(shiftedExpression);
      unionAcceptanceSets += acceptance.acceptanceSets();
    }

    var unionExpression = Disjunction.of(unionDisjuncts);
    return new EmersonLeiAcceptance(unionAcceptanceSets, unionExpression);
  }

  private static class NullablePairDeterministicUnionAutomaton<S1, S2>
    extends SemiDeterministicEdgesAutomaton<NullablePair<S1, S2>, EmersonLeiAcceptance> {

    private final Automaton<S1, ?> automaton1;
    private final Automaton<S2, ?> automaton2;

    private final BitSet rejectingSet1;
    private final BitSet rejectingSet2;

    private NullablePairDeterministicUnionAutomaton(
      Automaton<S1, ?> automaton1,
      Automaton<S2, ?> automaton2,
      Pair<ValuationSetFactory, Boolean> factory,
      BitSet rejectingSet1,
      BitSet rejectingSet2) {

      super(factory.fst(),
        Set.of(initialState(automaton1, automaton2)),
        unionAcceptance(List.of(automaton1.acceptance(), automaton2.acceptance())));
      this.automaton1 = automaton1;
      this.automaton2 = automaton2;
      this.rejectingSet1 = rejectingSet1;
      this.rejectingSet2 = rejectingSet2;
    }

    private static <S1, S2> NullablePair<S1, S2> initialState(
      Automaton<? extends S1, ?> automata1, Automaton<? extends S2, ?> automata2) {
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
      if (edge1 == null && edge2 == null) {
        return null;
      }

      S1 successor1;
      BitSet acceptance = new BitSet();

      if (edge1 == null) {
        successor1 = null;
        acceptance.or(rejectingSet1);
      } else {
        successor1 = edge1.successor();
        edge1.forEachAcceptanceSet(acceptance::set);
      }

      S2 successor2;
      int offset = automaton1.acceptance().acceptanceSets();

      if (edge2 == null) {
        successor2 = null;
        rejectingSet2.stream().forEach(i -> acceptance.set(i + offset));
      } else {
        successor2 = edge2.successor();
        edge2.forEachAcceptanceSet(i -> acceptance.set(i + offset));
      }

      return Edge.of(NullablePair.of(successor1, successor2), acceptance);
    }
  }

  private static class MapDeterministicUnionAutomaton<S>
    extends SemiDeterministicEdgesAutomaton<Map<Integer, S>, EmersonLeiAcceptance> {

    private final List<? extends Automaton<S, ?>> automata;
    private final List<BitSet> rejectingSets;

    private MapDeterministicUnionAutomaton(
      List<? extends Automaton<S, ?>> automata,
      Pair<ValuationSetFactory, Boolean> factory,
      List<BitSet> rejectingSets) {

      super(factory.fst(),
        Set.of(initialState(automata)),
        unionAcceptance(automata.stream().map(Automaton::acceptance).collect(Collectors.toList())));
      this.automata = List.copyOf(automata);
      this.rejectingSets = List.copyOf(rejectingSets);
    }

    private static <S> Map<Integer, S> initialState(List<? extends Automaton<S, ?>> automata) {

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
        int offsetFinal = offset;

        if (edge == null) {
          rejectingSets.get(i).stream().forEach(set -> acceptance.set(set + offsetFinal));
        } else {
          successor.put(i, edge.successor());
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

    @Override
    public void accept(EdgeVisitor<S> visitor) {
      backingAutomaton.accept(visitor);
    }

    @Override
    public void accept(EdgeMapVisitor<S> visitor) {
      backingAutomaton.accept(visitor);
    }

    @Override
    public void accept(EdgeTreeVisitor<S> visitor) {
      backingAutomaton.accept(visitor);
    }

    @Override
    public void accept(Visitor<S> visitor) {
      backingAutomaton.accept(visitor);
    }

    @Override
    public boolean is(Property property) {
      return backingAutomaton.is(property);
    }
  }
}
