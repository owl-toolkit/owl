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
import static owl.automaton.Automaton.PreferredEdgeAccess.EDGE_TREE;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.collections.Collections3;
import owl.collections.ValuationSet;
import owl.collections.ValuationTree;
import owl.collections.ValuationTrees;
import owl.factories.ValuationSetFactory;
import owl.run.modules.OwlModule;
import owl.run.modules.OwlModule.AutomatonTransformer;

@SuppressWarnings("PMD.CouplingBetweenObjects")
public final class Views {
  public static final OwlModule<OwlModule.Transformer> COMPLETE_MODULE = OwlModule.of(
    "complete",
    "Make the transition relation of an automaton complete by adding a sink-state.",
    (commandLine, environment) -> AutomatonTransformer.of(Views::completeWithOptional));

  private Views() {}

  public static <S> Automaton<S, ?> complete(Automaton<S, ?> automaton, S rejectingSink) {
    assert !automaton.states().contains(rejectingSink);
    var acceptance = automaton.acceptance();

    // Patch acceptance, if necessary.
    if (acceptance instanceof AllAcceptance) {
      acceptance = CoBuchiAcceptance.INSTANCE;
    } else if (acceptance instanceof ParityAcceptance && acceptance.acceptanceSets() <= 1) {
      acceptance = ((ParityAcceptance) acceptance).withAcceptanceSets(2);
    }

    OmegaAcceptance finalAcceptance = acceptance;
    BitSet rejectingSet = acceptance.rejectingSet()
      .orElseThrow(() -> new NoSuchElementException("No rejecting set for " + finalAcceptance));
    return new Complete<>(automaton, Edge.of(rejectingSink, rejectingSet), acceptance);
  }

  public static <S> Automaton<Optional<S>, ?> completeWithOptional(Automaton<S, ?> automaton) {
    var optionalAutomaton = new Automaton<Optional<S>, OmegaAcceptance>() {
      @Override
      public OmegaAcceptance acceptance() {
        return automaton.acceptance();
      }

      @Override
      public ValuationSetFactory factory() {
        return automaton.factory();
      }

      @Override
      public Set<Optional<S>> initialStates() {
        return Collections3.transformSetWithOptionalOf(automaton.initialStates());
      }

      @Override
      public Set<Optional<S>> states() {
        return Collections3.transformSetWithOptionalOf(automaton.states());
      }

      @Override
      public Set<Edge<Optional<S>>> edges(Optional<S> state, BitSet valuation) {
        if (state.isEmpty()) {
          throw new IllegalArgumentException("state is empty");
        }

        return transformEdges(automaton.edges(state.get(), valuation));
      }

      @Override
      public Map<Edge<Optional<S>>, ValuationSet> edgeMap(Optional<S> state) {
        if (state.isEmpty()) {
          throw new IllegalArgumentException("state is empty");
        }

        return Collections3.transformMap(
          automaton.edgeMap(state.get()),
          edge -> edge.withSuccessor(Optional.of(edge.successor())),
          (edge1, edge2) -> {
            throw new AssertionError("should not merge.");
          });
      }

      @Override
      public ValuationTree<Edge<Optional<S>>> edgeTree(Optional<S> state) {
        if (state.isEmpty()) {
          throw new IllegalArgumentException("state is empty");
        }

        return automaton.edgeTree(state.get()).map(this::transformEdges);
      }

      @Override
      public List<PreferredEdgeAccess> preferredEdgeAccess() {
        return automaton.preferredEdgeAccess();
      }

      private Set<Edge<Optional<S>>> transformEdges(Set<Edge<S>> edges) {
        return Collections3.transformSet(edges,
          edge -> edge.withSuccessor(Optional.of(edge.successor())));
      }

      @Override
      public boolean is(Property property) {
        return automaton.is(property);
      }
    };

    return complete(optionalAutomaton, Optional.empty());
  }

  /**
   * Create a filtered view on the passed automaton. The returned automaton only contains
   * states that are reachable from the initial states. States can be protected from removal by
   * marking them as initial. It is assumed that passed automaton is not changed.
   *
   * @param automaton the backing automaton
   * @param filter the filter defined on the automaton
   * @param <S> the type of the states
   * @param <A> the type of
   * @return a on-the-fly generated view on the automaton.
   */
  public static <S, A extends OmegaAcceptance> Automaton<S, A> filtered(
    Automaton<S, A> automaton, Filter<S> filter) {
    return new AutomatonView<>(automaton, filter);
  }

  private static class Complete<S, A extends OmegaAcceptance> implements Automaton<S, A> {

    private final Automaton<S, ?> automaton;
    private final A acceptance;

    private final Set<S> sinkSet;
    private final Edge<S> sinkEdge;
    private final Set<Edge<S>> sinkEdgeSet;

    @Nullable
    private Map<S, ValuationSet> incompleteStates;

    Complete(Automaton<S, ?> automaton, Edge<S> sinkEdge, A acceptance) {
      this.automaton = automaton;
      this.acceptance = acceptance;
      this.sinkSet = Set.of(sinkEdge.successor());
      this.sinkEdge = sinkEdge;
      this.sinkEdgeSet = Set.of(sinkEdge);
    }

    @Override
    public A acceptance() {
      return acceptance;
    }

    @Override
    public ValuationSetFactory factory() {
      return automaton.factory();
    }

    @Override
    public List<PreferredEdgeAccess> preferredEdgeAccess() {
      return automaton.preferredEdgeAccess();
    }

    @Override
    public Set<S> initialStates() {
      return automaton.initialStates();
    }

    @Override
    public Set<S> states() {
      if (incompleteStates == null) {
        incompleteStates = AutomatonUtil.getIncompleteStates(automaton);
      }

      if (incompleteStates.isEmpty()) {
        return automaton.states();
      } else {
        return Sets.union(automaton.states(), sinkSet);
      }
    }

    @Override
    public Set<S> successors(S state) {
      if (sinkEdge.successor().equals(state)) {
        return sinkSet;
      }

      if (incompleteStates != null && incompleteStates.containsKey(state)) {
        return Sets.union(automaton.states(), sinkSet);
      }

      return Edges.successors(edgeMap(state).keySet());
    }

    @Override
    public Set<Edge<S>> edges(S state, BitSet valuation) {
      if (sinkEdge.successor().equals(state)) {
        return sinkEdgeSet;
      }

      Set<Edge<S>> edges = automaton.edges(state, valuation);
      return edges.isEmpty() ? sinkEdgeSet : edges;
    }

    @Override
    public Set<Edge<S>> edges(S state) {
      if (sinkEdge.successor().equals(state)) {
        return sinkEdgeSet;
      }

      if (incompleteStates != null && incompleteStates.containsKey(state)) {
        return Sets.union(automaton.edges(state), sinkEdgeSet);
      }

      return preferredEdgeAccess().get(0) == EDGE_TREE
         ? edgeTree(state).flatValues()
         : edgeMap(state).keySet();
    }

    @Override
    public Map<Edge<S>, ValuationSet> edgeMap(S state) {
      ValuationSetFactory factory = automaton.factory();

      if (sinkEdge.successor().equals(state)) {
        return Map.of(sinkEdge, factory.universe());
      }

      if (incompleteStates != null && !incompleteStates.containsKey(state)) {
        return automaton.edgeMap(state);
      }

      Map<Edge<S>, ValuationSet> edges = new HashMap<>(automaton.edgeMap(state));
      ValuationSet valuationSet = incompleteStates == null
        ? edges.values().stream().reduce(factory.empty(), ValuationSet::union).complement()
        : incompleteStates.get(state);

      if (!valuationSet.isEmpty()) {
        edges.put(sinkEdge, valuationSet);
      }

      return edges;
    }

    @Override
    public ValuationTree<Edge<S>> edgeTree(S state) {
      if (sinkEdge.successor().equals(state)) {
        return ValuationTree.of(sinkEdgeSet);
      }

      var valuationTree = automaton.edgeTree(state);

      if (incompleteStates != null && !incompleteStates.containsKey(state)) {
        return valuationTree;
      }

      return valuationTree.map(x -> x.isEmpty() ? sinkEdgeSet : x);
    }

    @Override
    public boolean is(Property property) {
      return property == Property.COMPLETE || automaton.is(property);
    }
  }

  @AutoValue
  public abstract static class Filter<S> {
    @Nullable
    abstract Set<S> initialStates();

    @Nullable
    abstract Predicate<S> stateFilter();

    @Nullable
    abstract BiPredicate<S, Edge<S>> edgeFilter();

    public static <S> Builder<S> builder() {
      return new AutoValue_Views_Filter.Builder<>();
    }

    public static <S> Filter<S> of(Set<S> initialStates) {
      Builder<S> builder = builder();
      return builder.initialStates(initialStates).build();
    }

    public static <S> Filter<S> of(Set<S> initialStates, Predicate<S> stateFilter) {
      Builder<S> builder = builder();
      return builder.initialStates(initialStates).stateFilter(stateFilter).build();
    }

    public static <S> Filter<S> of(Predicate<S> stateFilter) {
      Builder<S> builder = builder();
      return builder.stateFilter(stateFilter).build();
    }

    public static <S> Filter<S> of(Predicate<S> stateFilter, BiPredicate<S, Edge<S>> edgeFilter) {
      Builder<S> builder = builder();
      return builder.stateFilter(stateFilter).edgeFilter(edgeFilter).build();
    }

    @AutoValue.Builder
    public abstract static class Builder<S> {
      public abstract Builder<S> initialStates(@Nullable Set<S> initialStates);

      public abstract Builder<S> stateFilter(@Nullable Predicate<S> filter);

      public abstract Builder<S> edgeFilter(@Nullable BiPredicate<S, Edge<S>> filter);

      public abstract Filter<S> build();
    }
  }

  private static final class AutomatonView<S, A extends OmegaAcceptance>
    extends AbstractImmutableAutomaton<S, A> {

    private final Automaton<S, A> backingAutomaton;

    @Nullable
    private final Predicate<S> stateFilter;

    @Nullable
    private final BiPredicate<S, Edge<S>> edgeFilter;

    private AutomatonView(Automaton<S, A> automaton, Filter<S> settings) {
      super(automaton.factory(),
        initialStates(automaton, settings),
        automaton.acceptance());

      if (automaton instanceof AutomatonView) {
        var castedAutomaton = (AutomatonView<S, A>) automaton;
        this.backingAutomaton = castedAutomaton.backingAutomaton;
        this.stateFilter = and(castedAutomaton.stateFilter, settings.stateFilter());
        this.edgeFilter = and(castedAutomaton.edgeFilter, settings.edgeFilter());
      } else {
        this.backingAutomaton = automaton;
        this.stateFilter = settings.stateFilter();
        this.edgeFilter = settings.edgeFilter();
      }
    }

    @Nullable
    static <T> Predicate<T> and(
      @Nullable Predicate<T> predicate1,
      @Nullable Predicate<T> predicate2) {
      if (predicate1 == null) {
        return predicate2;
      }

      if (predicate2 == null) {
        return predicate1;
      }

      return predicate1.and(predicate2);
    }

    @Nullable
    static <T, U> BiPredicate<T, U> and(
      @Nullable BiPredicate<T, U> predicate1,
      @Nullable BiPredicate<T, U> predicate2) {
      if (predicate1 == null) {
        return predicate2;
      }

      if (predicate2 == null) {
        return predicate1;
      }

      return predicate1.and(predicate2);
    }

    private static <S> Set<S> initialStates(
      Automaton<S, ?> automaton, Filter<S> settings) {
      Set<S> initialStates = settings.initialStates();

      if (initialStates != null) {
        return initialStates;
      }

      Predicate<S> stateFilter = settings.stateFilter();

      if (stateFilter == null) {
        return automaton.initialStates();
      }

      return Sets.filter(automaton.initialStates(), stateFilter::test);
    }

    private boolean stateFilter(S state) {
      return stateFilter == null || stateFilter.test(state);
    }

    private Predicate<Edge<S>> edgeFilter(S state) {
      return edge ->
        (edgeFilter == null || edgeFilter.test(state, edge)) && stateFilter(edge.successor());
    }

    private boolean filterRequired() {
      return stateFilter != null || edgeFilter != null;
    }

    @Override
    public List<PreferredEdgeAccess> preferredEdgeAccess() {
      return backingAutomaton.preferredEdgeAccess();
    }

    @Override
    public Set<Edge<S>> edges(S state, BitSet valuation) {
      checkArgument(stateFilter(state));
      return filterRequired()
        ? backingAutomaton.edges(state, valuation).stream()
            .filter(edgeFilter(state)).collect(Collectors.toSet())
        : backingAutomaton.edges(state, valuation);
    }

    @Override
    public Set<Edge<S>> edges(S state) {
      checkArgument(stateFilter(state));
      return filterRequired()
        ? backingAutomaton.edges(state).stream()
            .filter(edgeFilter(state)).collect(Collectors.toSet())
        : backingAutomaton.edges(state);
    }

    @Override
    public Map<Edge<S>, ValuationSet> edgeMap(S state) {
      checkArgument(stateFilter(state));
      return filterRequired()
        ? Maps.filterKeys(backingAutomaton.edgeMap(state), x -> edgeFilter(state).test(x))
        : backingAutomaton.edgeMap(state);
    }

    @Override
    public ValuationTree<Edge<S>> edgeTree(S state) {
      checkArgument(stateFilter(state));

      var edges = backingAutomaton.edgeTree(state);

      @Nullable
      Function<Set<Edge<S>>, Set<Edge<S>>> mapper;

      if (filterRequired()) {
        mapper = x -> Sets.filter(x, y -> edgeFilter(state).test(y));
      } else {
        mapper = null;
      }

      return mapper == null ? edges : edges.map(mapper);
    }
  }

  /**
   * This is essentially {@code fmap :: (S -> T) -> Automaton<S, A> -> Automaton<T, A>}.
   * When the function is injective, the effect is just replacing states of type S with states of
   * type T. If it is not, the result will be a quotient wrt. the equivalence classes induced by
   * the preimages.
   *
   * @param <S> input state type
   * @param <T> output state type
   * @param <A> acceptance condition
   * @param automaton input automaton
   * @param mappingFunction function from S to T
   * @return Output automaton where states are mapped from S to T (and possibly quotiented)
   */
  public static <S, T, A extends OmegaAcceptance> Automaton<T,A>
    quotientAutomaton(Automaton<S,A> automaton, Function<S,T> mappingFunction) {

    Map<S, T> mapping = new HashMap<>();
    ImmutableListMultimap.Builder<T, S> reverseMappingBuilder = ImmutableListMultimap.builder();

    for (var state : automaton.states()) {
      var mappedState = mappingFunction.apply(state);
      mapping.put(state, mappedState);
      reverseMappingBuilder.put(mappedState, state);
    }

    return new QuotientAutomaton<>(automaton, Map.copyOf(mapping), reverseMappingBuilder.build());
  }

  private static class QuotientAutomaton<S, T, A extends OmegaAcceptance>
    extends AbstractImmutableAutomaton.NonDeterministicEdgeTreeAutomaton<T, A> {

    private final Automaton<S, A> automaton;
    private final Map<S, T> mapping;
    private final ImmutableListMultimap<T, S> reverseMapping;

    private QuotientAutomaton(Automaton<S, A> automaton, Map<S, T> mapping,
      ImmutableListMultimap<T, S> reverseMapping) {
      super(automaton.factory(),
        automaton.initialStates().stream().map(mapping::get).collect(Collectors.toSet()),
        automaton.acceptance());

      this.automaton = automaton;
      this.mapping = Map.copyOf(mapping);
      this.reverseMapping = reverseMapping;
    }

    @Override
    public Set<Edge<T>> edges(T state, BitSet valuation) {
      return reverseMapping.get(state).stream()
        .flatMap((S sState) -> automaton.edges(sState, valuation).stream())
        .map((Edge<S> sEdge) -> sEdge.withSuccessor(mapping.get(sEdge.successor())))
        .collect(Collectors.toSet());
    }

    @Override
    public ValuationTree<Edge<T>> edgeTree(T state) {
      return ValuationTrees.cartesianProduct(
        reverseMapping.get(state).stream().map(automaton::edgeTree).collect(Collectors.toList())
      ).map(x -> {
        Set<Edge<T>> set = new HashSet<>();
        for (List<Edge<S>> edges : x) {
          for (Edge<S> edge : edges) {
            set.add(edge.withSuccessor(mapping.get(edge.successor())));
          }
        }
        return set;
      });
    }

    @Override
    public List<PreferredEdgeAccess> preferredEdgeAccess() {
      return automaton.preferredEdgeAccess();
    }
  }

  public static <S> Automaton<S, AllAcceptance>
    transitionStructure(Automaton<S, ?> automaton) {

    return new Automaton<>() {
      @Override
      public AllAcceptance acceptance() {
        return AllAcceptance.INSTANCE;
      }

      @Override
      public ValuationSetFactory factory() {
        return automaton.factory();
      }

      @Override
      public Set<S> initialStates() {
        return automaton.initialStates();
      }

      @Override
      public Set<S> states() {
        return automaton.states();
      }

      @Override
      public Set<Edge<S>> edges(S state, BitSet valuation) {
        return Collections3.transformSet(
          automaton.edges(state, valuation),
          Edge::withoutAcceptance);
      }

      @Override
      public Map<Edge<S>, ValuationSet> edgeMap(S state) {
        return Collections3.transformMap(
          automaton.edgeMap(state),
          Edge::withoutAcceptance);
      }

      @Override
      public ValuationTree<Edge<S>> edgeTree(S state) {
        return automaton.edgeTree(state)
          .map(x -> Collections3.transformSet(x, Edge::withoutAcceptance));
      }

      @Override
      public List<PreferredEdgeAccess> preferredEdgeAccess() {
        return automaton.preferredEdgeAccess();
      }
    };
  }
}
