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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Sets;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.edge.Colours;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.bdd.BddSet;
import owl.bdd.BddSetFactory;
import owl.bdd.MtBdd;
import owl.bdd.MtBddOperations;
import owl.collections.Pair;
import owl.run.modules.OwlModule;
import owl.run.modules.OwlModule.AutomatonTransformer;

@SuppressWarnings("PMD.CouplingBetweenObjects")
public final class Views {
  public static final OwlModule<OwlModule.Transformer> COMPLETE_MODULE = OwlModule.of(
    "complete",
    "Make the transition relation of an automaton complete by adding a sink-state.",
    (commandLine, environment) ->
      AutomatonTransformer.of(automaton ->
        Views.complete(automaton, new MutableAutomatonUtil.Sink())));

  private Views() {}

  public static <S> Automaton<S, ?> complete(Automaton<S, ?> automaton, S trapState) {
    var acceptance = automaton.acceptance();

    // Patch acceptance, if necessary.
    if (acceptance instanceof AllAcceptance) {
      acceptance = CoBuchiAcceptance.INSTANCE;
    } else if (acceptance instanceof ParityAcceptance && acceptance.acceptanceSets() <= 1) {
      acceptance = ((ParityAcceptance) acceptance).withAcceptanceSets(2);
    }

    EmersonLeiAcceptance finalAcceptance = acceptance;
    BitSet rejectingSet = acceptance.rejectingSet()
      .orElseThrow(() -> new NoSuchElementException("No rejecting set for " + finalAcceptance));
    return new Complete<>(automaton, Edge.of(trapState, rejectingSet), acceptance);
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
  public static <S, A extends EmersonLeiAcceptance> Automaton<S, A> filtered(
    Automaton<S, A> automaton, Filter<S> filter) {
    return new AutomatonView<>(automaton, filter);
  }

  private static class Complete<S, A extends EmersonLeiAcceptance, B extends EmersonLeiAcceptance>
    implements Automaton<S, B> {

    private final Edge<S> sinkEdge;
    private final Set<Edge<S>> sinkEdgeSet;
    private final Automaton<S, A> automaton;
    private final S sink;
    private final Set<S> sinkSet;
    private final B acceptance;

    @Nullable
    private Map<S, BddSet> incompleteStates;

    Complete(Automaton<S, A> automaton, Edge<S> sinkEdge, B acceptance) {
      this.automaton = automaton;
      this.sink = sinkEdge.successor();
      this.sinkSet = Set.of(sink);
      this.sinkEdge = sinkEdge;
      this.sinkEdgeSet = Set.of(sinkEdge);
      this.acceptance = acceptance;
    }

    @Override
    public List<String> atomicPropositions() {
      return automaton.atomicPropositions();
    }

    @Override
    public B acceptance() {
      return acceptance;
    }

    @Override
    public BddSetFactory factory() {
      return automaton.factory();
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
      if (sink.equals(state)) {
        return sinkSet;
      }

      if (incompleteStates != null && incompleteStates.containsKey(state)) {
        return Sets.union(automaton.states(), sinkSet);
      }

      return Edges.successors(edgeMap(state).keySet());
    }

    @Override
    public Set<Edge<S>> edges(S state, BitSet valuation) {
      if (sink.equals(state)) {
        return sinkEdgeSet;
      }

      Set<Edge<S>> edges = automaton.edges(state, valuation);
      return edges.isEmpty() ? sinkEdgeSet : edges;
    }

    @Override
    public Set<Edge<S>> edges(S state) {
      if (sink.equals(state)) {
        return sinkEdgeSet;
      }

      if (incompleteStates != null && incompleteStates.containsKey(state)) {
        return Sets.union(automaton.edges(state), sinkEdgeSet);
      }

      return edgeTree(state).flatValues();
    }

    @Override
    public Map<Edge<S>, BddSet> edgeMap(S state) {
      BddSetFactory factory = automaton.factory();

      if (sink.equals(state)) {
        return Map.of(sinkEdge, factory.of(true));
      }

      if (incompleteStates != null && !incompleteStates.containsKey(state)) {
        return automaton.edgeMap(state);
      }

      Map<Edge<S>, BddSet> edges = new HashMap<>(automaton.edgeMap(state));
      BddSet valuationSet = incompleteStates == null
        ? edges.values().stream().reduce(factory.of(false), BddSet::union).complement()
        : incompleteStates.get(state);

      if (!valuationSet.isEmpty()) {
        edges.put(sinkEdge, valuationSet);
      }

      return edges;
    }

    @Override
    public MtBdd<Edge<S>> edgeTree(S state) {
      if (sink.equals(state)) {
        return MtBdd.of(sinkEdgeSet);
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

  private static final class AutomatonView<S, A extends EmersonLeiAcceptance>
    extends AbstractMemoizingAutomaton.EdgeTreeImplementation<S, A> {

    private final Automaton<S, A> backingAutomaton;

    @Nullable
    private final Predicate<S> stateFilter;

    @Nullable
    private final BiPredicate<S, Edge<S>> edgeFilter;

    private AutomatonView(Automaton<S, A> automaton, Filter<S> settings) {
      super(
        automaton.atomicPropositions(),
        automaton.factory(),
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

      if (initialStates == null) {
        initialStates = automaton.initialStates();
      }

      Predicate<S> stateFilter = settings.stateFilter();

      if (stateFilter == null) {
        return initialStates;
      }

      return Sets.filter(initialStates, stateFilter::test);
    }

    private boolean stateFilter(S state) {
      return stateFilter == null || stateFilter.test(state);
    }

    private boolean edgeFilter(S state, Edge<S> edge) {
      return edgeFilter == null || edgeFilter.test(state, edge);
    }

    @Override
    public MtBdd<Edge<S>> edgeTreeImpl(S state) {
      checkArgument(stateFilter(state));

      var edges = backingAutomaton.edgeTree(state);

      if (stateFilter == null && edgeFilter == null) {
        return edges;
      }

      return edges.map((Set<Edge<S>> set) -> set.stream()
        .filter(edge -> edgeFilter(state, edge) && stateFilter(edge.successor()))
        .collect(Collectors.toUnmodifiableSet()));
    }
  }

  /**
   * This is essentially {@code fmap :: (S -> T) -> Automaton<S,A> -> Automaton<T,A>}.
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
  public static <S, T, A extends EmersonLeiAcceptance> Automaton<T, A>
    quotientAutomaton(Automaton<S, A> automaton, Function<? super S, ? extends T> mappingFunction) {

    Map<S, T> mapping = new HashMap<>();
    ImmutableListMultimap.Builder<T, S> reverseMappingBuilder = ImmutableListMultimap.builder();

    for (var state : automaton.states()) {
      var mappedState = mappingFunction.apply(state);
      mapping.put(state, mappedState);
      reverseMappingBuilder.put(mappedState, state);
    }

    return new QuotientAutomaton<>(automaton, Map.copyOf(mapping), reverseMappingBuilder.build());
  }

  private static class QuotientAutomaton<S, T, A extends EmersonLeiAcceptance>
    extends AbstractMemoizingAutomaton.EdgeTreeImplementation<T, A> {

    private final Automaton<S, A> automaton;
    private final Map<S, T> mapping;
    private final ImmutableListMultimap<T, S> reverseMapping;

    private QuotientAutomaton(Automaton<S, A> automaton, Map<S, T> mapping,
      ImmutableListMultimap<T, S> reverseMapping) {
      super(
        automaton.atomicPropositions(),
        automaton.factory(),
        automaton.initialStates().stream().map(mapping::get).collect(Collectors.toSet()),
        automaton.acceptance());

      this.automaton = automaton;
      this.mapping = Map.copyOf(mapping);
      this.reverseMapping = reverseMapping;
    }

    @Override
    public MtBdd<Edge<T>> edgeTreeImpl(T state) {
      return MtBddOperations.cartesianProduct(
        reverseMapping.get(state).stream().map(automaton::edgeTree).collect(Collectors.toList())
      ).map(x -> {
        Set<Edge<T>> set = new HashSet<>();
        for (List<Edge<S>> edges : x) {
          for (Edge<S> edge : edges) {
            set.add(edge.mapSuccessor(mapping::get));
          }
        }
        return set;
      });
    }
  }

  public static <S, A extends EmersonLeiAcceptance> Automaton<Pair<S, Colours>, A>
  stateAcceptanceAutomaton(Automaton<S, A> automaton) {
    return new AbstractMemoizingAutomaton.EdgeTreeImplementation<>(
      automaton.atomicPropositions(),
      automaton.factory(),
      automaton.initialStates().stream().map(state ->
        Pair.of(state, Colours.of())
      ).collect(Collectors.toSet()),
      automaton.acceptance()
    ) {
      @Override
      protected MtBdd<Edge<Pair<S, Colours>>> edgeTreeImpl(
        Pair<S, Colours> state) {
        return automaton.edgeTree(state.fst()).map(edges ->
          edges.stream()
            .map(edge -> Edge.of(Pair.of(edge.successor(), edge.colours()), edge.colours()))
            .collect(Collectors.toSet()
            ));
      }
    };
  }
}
