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

package owl.automaton;

import static com.google.common.base.Preconditions.checkArgument;
import static owl.automaton.acceptance.OmegaAcceptanceCast.isInstanceOf;
import static owl.logic.propositional.PropositionalFormula.Conjunction;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.automaton.AbstractMemoizingAutomaton.EdgeImplementation;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedCoBuchiAcceptance;
import owl.automaton.acceptance.OmegaAcceptanceCast;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.edge.Edge;
import owl.bdd.MtBdd;
import owl.bdd.MtBddOperations;
import owl.collections.ImmutableBitSet;
import owl.collections.NullablePair;
import owl.collections.Pair;
import owl.logic.propositional.PropositionalFormula;
import owl.logic.propositional.PropositionalFormula.Disjunction;

/**
 * This class provides standard boolean operations (union, intersection, complementation) on
 * automata. The returned automata are constructed on-the-fly and it assumed that the
 * underlying automata are not changed during the lifetime of the returned objects.
 */
public final class BooleanOperations {

  private BooleanOperations() {}

  public static <S> Automaton<S, ?>
    deterministicComplementOfCompleteAutomaton(
      Automaton<S, ?> completeAutomaton) {

    return deterministicComplementOfCompleteAutomaton(
      completeAutomaton,
      EmersonLeiAcceptance.class);
  }

  public static <S, A extends EmersonLeiAcceptance> Automaton<S, ? extends A>
    deterministicComplementOfCompleteAutomaton(
      Automaton<S, ?> completeAutomaton,
      Class<? extends A> expectedAcceptance) {

    int initialStatesSize = completeAutomaton.initialStates().size();

    checkArgument(initialStatesSize >= 1, "Automaton has no initial state");
    checkArgument(initialStatesSize <= 1, "Automaton has multiple initial states");

    EmersonLeiAcceptance acceptance = completeAutomaton.acceptance();
    EmersonLeiAcceptance complementAcceptance = null;

    if (acceptance instanceof BuchiAcceptance) {
      checkArgument(isInstanceOf(CoBuchiAcceptance.class, expectedAcceptance));
      complementAcceptance = CoBuchiAcceptance.INSTANCE;
    } else if (acceptance instanceof CoBuchiAcceptance) {
      checkArgument(isInstanceOf(BuchiAcceptance.class, expectedAcceptance));
      complementAcceptance = BuchiAcceptance.INSTANCE;
    } else if (acceptance instanceof GeneralizedBuchiAcceptance generalizedBuchiAcceptance) {
      checkArgument(isInstanceOf(GeneralizedCoBuchiAcceptance.class, expectedAcceptance));
      complementAcceptance
        = GeneralizedCoBuchiAcceptance.of(generalizedBuchiAcceptance.acceptanceSets());
    } else if (acceptance instanceof GeneralizedCoBuchiAcceptance generalizedCoBuchiAcceptance) {
      checkArgument(isInstanceOf(GeneralizedBuchiAcceptance.class, expectedAcceptance));
      complementAcceptance
        = GeneralizedBuchiAcceptance.of(generalizedCoBuchiAcceptance.acceptanceSets());
    } else if (acceptance instanceof ParityAcceptance) {
      checkArgument(isInstanceOf(ParityAcceptance.class, expectedAcceptance));
      complementAcceptance = ((ParityAcceptance) completeAutomaton.acceptance()).complement();
    } else if (isInstanceOf(EmersonLeiAcceptance.class, expectedAcceptance)) {
      complementAcceptance = EmersonLeiAcceptance.of(
        PropositionalFormula.Negation.of(acceptance.booleanExpression()));
    }

    if (complementAcceptance == null) {
      throw new UnsupportedOperationException("Cannot complement to " + expectedAcceptance);
    }

    return OmegaAcceptanceCast.cast(
      new OverrideAcceptanceCondition<>(completeAutomaton, complementAcceptance, true),
      expectedAcceptance);
  }

  public static <S> Automaton<Optional<S>, ?>
    deterministicComplement(Automaton<S, ?> automaton) {

    return deterministicComplement(automaton, EmersonLeiAcceptance.class);
  }

  public static <S, A extends EmersonLeiAcceptance> Automaton<Optional<S>, ? extends A>
    deterministicComplement(Automaton<S, ?> automaton, Class<? extends A> expectedAcceptance) {

    return deterministicComplementOfCompleteAutomaton(
      Views.complete(automaton), expectedAcceptance);
  }

  public static <S1, S2> Automaton<Pair<S1, S2>, ?>
    intersection(Automaton<S1, ?> automaton1, Automaton<S2, ?> automaton2) {

    return new PairIntersectionAutomaton<>(
      unifyAtomicPropositions(List.of(automaton1, automaton2)),
      automaton1,
      automaton2);
  }

  public static <S> Automaton<List<S>, ?>
    intersection(List<? extends Automaton<S, ?>> automata) {

    return new ListIntersectionAutomaton<>(
      unifyAtomicPropositions(automata),
      automata);
  }

  public static <S1, S2> Automaton<NullablePair<S1, S2>, ?>
    deterministicUnion(Automaton<S1, ?> automaton1, Automaton<S2, ?> automaton2) {

    Automaton<S1, ?> normalizedAutomaton1;
    Automaton<S2, ?> normalizedAutomaton2;

    // If all runs on automaton1 are accepting, transform to BuchiAcceptance to have access
    // to an rejecting acceptance set.
    if (automaton1.acceptance().rejectingSet().isEmpty()) {
      normalizedAutomaton1 = OmegaAcceptanceCast.cast(
        new OverrideAcceptanceCondition<>(automaton1, AllAcceptance.INSTANCE, false),
        BuchiAcceptance.class);
    } else {
      normalizedAutomaton1 = automaton1;
    }

    // If all runs on automaton2 are accepting, transform to BuchiAcceptance to have access
    // to an rejecting acceptance set.
    if (automaton2.acceptance().rejectingSet().isEmpty()) {
      normalizedAutomaton2 = OmegaAcceptanceCast.cast(
        new OverrideAcceptanceCondition<>(automaton2, AllAcceptance.INSTANCE, false),
        BuchiAcceptance.class);
    } else {
      normalizedAutomaton2 = automaton2;
    }

    return new NullablePairDeterministicUnionAutomaton<>(
      unifyAtomicPropositions(List.of(automaton1, automaton2)),
      normalizedAutomaton1,
      normalizedAutomaton2,
      normalizedAutomaton1.acceptance().rejectingSet().orElseThrow(),
      normalizedAutomaton2.acceptance().rejectingSet().orElseThrow());
  }

  public static <S> Automaton<Map<Integer, S>, ?>
    deterministicUnion(List<? extends Automaton<S, ?>> automata) {

    checkArgument(!automata.isEmpty(), "List of automata is empty.");

    List<Automaton<S, ?>> automataCopy = new ArrayList<>(automata.size());
    List<ImmutableBitSet> rejectingSets = new ArrayList<>(automata.size());

    automata.forEach(automaton -> {
      Automaton<S, ?> normalisedAutomaton;

      if (automaton.acceptance().rejectingSet().isEmpty()) {
        normalisedAutomaton = OmegaAcceptanceCast.cast(
          new OverrideAcceptanceCondition<>(automaton, AllAcceptance.INSTANCE, false),
          BuchiAcceptance.class);
      } else {
        normalisedAutomaton = automaton;
      }

      automataCopy.add(normalisedAutomaton);
      rejectingSets.add(normalisedAutomaton.acceptance().rejectingSet().orElseThrow());
    });

    return new MapDeterministicUnionAutomaton<>(
      unifyAtomicPropositions(automata),
      automataCopy,
      rejectingSets);
  }

  // Private implementations

  private static List<String> unifyAtomicPropositions(List<? extends Automaton<?, ?>> automata) {
    List<String> atomicPropositions = List.of();

    for (Automaton<?, ?> automaton : automata) {
      List<String> otherAtomicPropositions = automaton.atomicPropositions();

      if (Collections.indexOfSubList(otherAtomicPropositions, atomicPropositions) == 0) {
        atomicPropositions = otherAtomicPropositions;
      } else if (Collections.indexOfSubList(atomicPropositions, otherAtomicPropositions) != 0) {
        throw new IllegalArgumentException("Could not find shared set of atomic propositions.");
      }
    }

    return atomicPropositions;
  }

  private static EmersonLeiAcceptance intersectionAcceptance(
    List<? extends EmersonLeiAcceptance> acceptanceConditions) {

    var intersectionConjuncts = new ArrayList<PropositionalFormula<Integer>>();
    int intersectionAcceptanceSets = 0;

    for (EmersonLeiAcceptance acceptance : acceptanceConditions) {
      var fIntersectionAcceptanceSets = intersectionAcceptanceSets;
      var shiftedExpression = acceptance.booleanExpression()
        .map(x -> x + fIntersectionAcceptanceSets);
      intersectionConjuncts.add(shiftedExpression);
      intersectionAcceptanceSets += acceptance.acceptanceSets();
    }

    var intersectionExpression = Conjunction.of(intersectionConjuncts);
    return EmersonLeiAcceptance.of(intersectionExpression);
  }

  private static class PairIntersectionAutomaton<S1, S2>
    extends AbstractMemoizingAutomaton.EdgeTreeImplementation<Pair<S1, S2>, EmersonLeiAcceptance> {

    @Nullable
    private Automaton<S1, ?> automaton1;
    @Nullable
    private Automaton<S2, ?> automaton2;

    private final int acceptance1Sets;

    private PairIntersectionAutomaton(
      List<String> atomicPropositions,
      Automaton<S1, ?> automaton1,
      Automaton<S2, ?> automaton2) {

      super(atomicPropositions,
        Pair.allPairs(automaton1.initialStates(), automaton2.initialStates()),
        intersectionAcceptance(List.of(automaton1.acceptance(), automaton2.acceptance())));

      this.automaton1 = automaton1;
      this.automaton2 = automaton2;
      this.acceptance1Sets = automaton1.acceptance().acceptanceSets();
    }

    @Override
    protected MtBdd<Edge<Pair<S1, S2>>> edgeTreeImpl(Pair<S1, S2> state) {
      var edgeTree1 = automaton1.edgeTree(state.fst());
      var edgeTree2 = automaton2.edgeTree(state.snd());
      return MtBddOperations.cartesianProduct(edgeTree1, edgeTree2, this::combine);
    }

    private Edge<Pair<S1, S2>> combine(Edge<? extends S1> edge1, Edge<? extends S2> edge2) {
      BitSet acceptance = edge1.colours().copyInto(new BitSet());
      edge2.colours().forEach((int set) -> acceptance.set(set + acceptance1Sets));
      // Remove colours not appearing in the acceptance condition.
      acceptance.clear(acceptance().acceptanceSets(), Integer.MAX_VALUE);
      return Edge.of(Pair.of(edge1.successor(), edge2.successor()), acceptance);
    }

    @Override
    protected void explorationCompleted() {
      automaton1 = null;
      automaton2 = null;
    }
  }

  private static class ListIntersectionAutomaton<S>
    extends AbstractMemoizingAutomaton.EdgeTreeImplementation<List<S>, EmersonLeiAcceptance> {

    @Nullable
    private List<? extends Automaton<S, ?>> automata;

    private ListIntersectionAutomaton(
      List<String> atomicPropositions, List<? extends Automaton<S, ?>> automata) {

      super(atomicPropositions,
        initialStates(automata),
        intersectionAcceptance(
          automata.stream().map(Automaton::acceptance).toList()));
      this.automata = List.copyOf(automata);
    }

    private static <S> Set<List<S>>
      initialStates(List<? extends Automaton<S, ?>> automata) {

      return Sets.cartesianProduct(
        automata.stream().map(Automaton::initialStates).toList());
    }

    @Override
    protected MtBdd<Edge<List<S>>> edgeTreeImpl(List<S> state) {
      List<MtBdd<Edge<S>>> edgeTrees = new ArrayList<>();

      for (int i = 0, s = automata.size(); i < s; i++) {
        edgeTrees.add(automata.get(i).edgeTree(state.get(i)));
      }

      return MtBddOperations
        .cartesianProduct(edgeTrees)
        .map((Set<List<Edge<S>>> x) -> x.stream()
          .map(this::combine)
          .collect(Collectors.toUnmodifiableSet()));
    }

    private Edge<List<S>> combine(List<? extends Edge<S>> edges) {
      List<S> successors = new ArrayList<>();
      BitSet acceptance = new BitSet();

      int offset = 0;

      for (int i = 0, s = automata.size(); i < s; i++) {
        int offsetFinal = offset;

        var edge = edges.get(i);
        successors.add(edge.successor());
        edge.colours().forEach((IntConsumer) set -> acceptance.set(set + offsetFinal));

        offset += automata.get(i).acceptance().acceptanceSets();
      }

      // Remove colours not appearing in the acceptance condition.
      acceptance.clear(acceptance().acceptanceSets(), Integer.MAX_VALUE);
      return Edge.of(List.copyOf(successors), acceptance);
    }

    @Override
    protected void explorationCompleted() {
      automata = null;
    }
  }

  private static EmersonLeiAcceptance unionAcceptance(
    List<? extends EmersonLeiAcceptance> acceptanceConditions) {

    var unionDisjuncts = new ArrayList<PropositionalFormula<Integer>>();
    int unionAcceptanceSets = 0;

    for (EmersonLeiAcceptance acceptance : acceptanceConditions) {
      var fUnionAcceptanceSets = unionAcceptanceSets;
      var shiftedExpression = acceptance.booleanExpression().map(x -> x + fUnionAcceptanceSets);
      unionDisjuncts.add(shiftedExpression);
      unionAcceptanceSets += acceptance.acceptanceSets();
    }

    var unionExpression = Disjunction.of(unionDisjuncts);
    return EmersonLeiAcceptance.of(unionExpression);
  }

  private static class NullablePairDeterministicUnionAutomaton<S1, S2>
    extends EdgeImplementation<NullablePair<S1, S2>, EmersonLeiAcceptance> {

    @Nullable
    private Automaton<S1, ?> automaton1;
    @Nullable
    private Automaton<S2, ?> automaton2;

    private final ImmutableBitSet rejectingSet1;
    private final ImmutableBitSet rejectingSet2;

    private NullablePairDeterministicUnionAutomaton(
      List<String> atomicPropositions,
      Automaton<S1, ?> automaton1,
      Automaton<S2, ?> automaton2,
      ImmutableBitSet rejectingSet1,
      ImmutableBitSet rejectingSet2) {

      super(atomicPropositions,
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
    public Edge<NullablePair<S1, S2>> edgeImpl(NullablePair<S1, S2> state, BitSet valuation) {
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
        rejectingSet1.copyInto(acceptance);
      } else {
        successor1 = edge1.successor();
        edge1.colours().copyInto(acceptance);
      }

      S2 successor2;
      int offset = automaton1.acceptance().acceptanceSets();

      if (edge2 == null) {
        successor2 = null;
        rejectingSet2.forEach((int i) -> acceptance.set(i + offset));
      } else {
        successor2 = edge2.successor();
        edge2.colours().forEach((int i) -> acceptance.set(i + offset));
      }

      // Remove colours not appearing in the acceptance condition.
      acceptance.clear(acceptance().acceptanceSets(), Integer.MAX_VALUE);
      return Edge.of(NullablePair.of(successor1, successor2), acceptance);
    }

    @Override
    protected void explorationCompleted() {
      automaton1 = null;
      automaton2 = null;
    }
  }

  private static class MapDeterministicUnionAutomaton<S>
    extends EdgeImplementation<Map<Integer, S>, EmersonLeiAcceptance> {

    @Nullable
    private List<? extends Automaton<S, ?>> automata;
    private final List<ImmutableBitSet> rejectingSets;

    private MapDeterministicUnionAutomaton(
      List<String> atomicPropositions,
      List<? extends Automaton<S, ?>> automata,
      List<ImmutableBitSet> rejectingSets) {

      super(atomicPropositions,
        Set.of(initialState(automata)),
        unionAcceptance(automata.stream().map(Automaton::acceptance).toList()));
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
    public Edge<Map<Integer, S>> edgeImpl(Map<Integer, S> state, BitSet valuation) {
      Map<Integer, Edge<S>> edges = new HashMap<>();

      state.forEach(
        (index, localState) -> edges.put(index, automata.get(index).edge(localState, valuation)));

      return combine(edges);
    }

    @Nullable
    private Edge<Map<Integer, S>> combine(Map<Integer, ? extends Edge<S>> edges) {
      Map<Integer, S> successor = new HashMap<>();
      BitSet acceptance = new BitSet();

      int offset = 0;

      for (int i = 0, s = automata.size(); i < s; i++) {
        var edge = edges.get(i);
        int offsetFinal = offset;

        if (edge == null) {
          rejectingSets.get(i).forEach((int set) -> acceptance.set(set + offsetFinal));
        } else {
          successor.put(i, edge.successor());
          edge.colours().forEach((int set) -> acceptance.set(set + offsetFinal));
        }

        offset += automata.get(i).acceptance().acceptanceSets();
      }

      if (successor.isEmpty()) {
        return null;
      }

      // Remove colours not appearing in the acceptance condition.
      acceptance.clear(acceptance().acceptanceSets(), Integer.MAX_VALUE);
      return Edge.of(Map.copyOf(successor), acceptance);
    }

    @Override
    protected void explorationCompleted() {
      automata = null;
    }
  }

  private static final class OverrideAcceptanceCondition<S, A extends EmersonLeiAcceptance>
    extends AbstractMemoizingAutomaton.EdgeTreeImplementation<S, A> {

    @Nullable
    private Automaton<S, ?> backingAutomaton;
    private final boolean complete;

    private OverrideAcceptanceCondition(
      Automaton<S, ?> backingAutomaton, A acceptance, boolean complete) {

      super(
        backingAutomaton.atomicPropositions(),
        backingAutomaton.factory(),
        backingAutomaton.initialStates(),
        acceptance);

      this.backingAutomaton = backingAutomaton;
      this.complete = complete;

      if (complete && initialStates().isEmpty()) {
        throw new IllegalArgumentException("Automaton is not complete.");
      }

      if (initialStates().size() > 1) {
        throw new IllegalArgumentException("Automaton is non-deterministic.");
      }
    }

    @Override
    protected MtBdd<Edge<S>> edgeTreeImpl(S state) {
      // Remove colours from underlying automaton that do not appear in the acceptance condition.
      int acceptanceSets = acceptance().acceptanceSets();

      return Objects.requireNonNull(backingAutomaton, "freezeMemoizedEdgesNotify already called.")
        .edgeTree(state).map(edges -> {
          switch (edges.size()) {
            case 0:
              if (complete) {
                throw new IllegalArgumentException("Automaton is not complete.");
              }

              return Set.of();

            case 1:
              return Set.of(
                edges.iterator().next().mapAcceptance(i -> i < acceptanceSets ? i : -1));

            default:
              throw new IllegalArgumentException("Automaton is not deterministic.");
          }
        });
    }

    @Override
    protected void explorationCompleted() {
      backingAutomaton = null;
    }

    @Override
    public boolean is(Property property) {
      return switch (property) {
        case DETERMINISTIC, SEMI_DETERMINISTIC -> true;
        case COMPLETE -> complete || super.is(Property.COMPLETE);
        case LIMIT_DETERMINISTIC -> super.is(Property.LIMIT_DETERMINISTIC);
      };
    }
  }
}
