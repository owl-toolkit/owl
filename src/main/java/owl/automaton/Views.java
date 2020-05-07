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

package owl.automaton;

import static com.google.common.base.Preconditions.checkArgument;
import static owl.automaton.Automaton.PreferredEdgeAccess.EDGE_TREE;

import com.google.auto.value.AutoValue;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.collections.ValuationSet;
import owl.collections.ValuationTree;
import owl.factories.ValuationSetFactory;
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
    }

    var rejectingEdge = Edge.of(trapState, acceptance.rejectingSet());
    return new Complete<>(automaton, rejectingEdge, acceptance);
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

  private static class Complete<S, A extends OmegaAcceptance, B extends OmegaAcceptance>
    implements Automaton<S, B> {

    private final Edge<S> sinkEdge;
    private final Set<Edge<S>> sinkEdgeSet;
    private final Automaton<S, A> automaton;
    private final S sink;
    private final Set<S> sinkSet;
    private final B acceptance;

    @Nullable
    private Map<S, ValuationSet> incompleteStates;

    Complete(Automaton<S, A> automaton, Edge<S> sinkEdge, B acceptance) {
      this.automaton = automaton;
      this.sink = sinkEdge.successor();
      this.sinkSet = Set.of(sink);
      this.sinkEdge = sinkEdge;
      this.sinkEdgeSet = Set.of(sinkEdge);
      this.acceptance = acceptance;
    }

    @Override
    public B acceptance() {
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

      return preferredEdgeAccess().get(0) == EDGE_TREE
         ? edgeTree(state).values()
         : edgeMap(state).keySet();
    }

    @Override
    public Map<Edge<S>, ValuationSet> edgeMap(S state) {
      ValuationSetFactory factory = automaton.factory();

      if (sink.equals(state)) {
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
      if (sink.equals(state)) {
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
}
