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
import static owl.automaton.acceptance.OmegaAcceptanceCast.cast;
import static owl.automaton.acceptance.OmegaAcceptanceCast.isInstanceOf;

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
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedCoBuchiAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.collections.Collections3;
import owl.collections.ValuationSet;
import owl.collections.ValuationTree;
import owl.factories.ValuationSetFactory;
import owl.run.modules.OwlModule;

@SuppressWarnings("PMD.CouplingBetweenObjects")
public final class Views {
  public static final OwlModule<OwlModule.Transformer> COMPLETE_MODULE = OwlModule.of(
    "complete",
    "Make the transition relation of an automaton complete by adding a sink-state.",
    (commandLine, environment) -> (input) -> Views
      .complete((Automaton<Object, ?>) input, new MutableAutomatonUtil.Sink()));

  private Views() {}

  public static <S, A extends OmegaAcceptance> Automaton<S, A> complement(
    Automaton<S, ?> automaton,
    @Nullable S trapState,
    Class<A> expectedAcceptance) {
    var completeAutomaton = trapState == null ? automaton : complete(automaton, trapState);

    checkArgument(completeAutomaton.is(Automaton.Property.COMPLETE), "Automaton is not complete.");
    checkArgument(!completeAutomaton.initialStates().isEmpty(), "Automaton is empty.");
    // Check is too costly.
    // checkArgument(completeAutomaton.is(DETERMINISTIC), "Automaton is not deterministic.");

    var acceptance = completeAutomaton.acceptance();

    if (acceptance instanceof BuchiAcceptance) {
      checkArgument(isInstanceOf(CoBuchiAcceptance.class, expectedAcceptance));

      var complement = new AutomatonView<>(completeAutomaton,
        ViewSettings.<S, OmegaAcceptance>builder().acceptance(CoBuchiAcceptance.INSTANCE).build());
      return cast(complement, expectedAcceptance);
    }

    if (acceptance instanceof CoBuchiAcceptance) {
      checkArgument(isInstanceOf(BuchiAcceptance.class, expectedAcceptance));

      var complement = new AutomatonView<>(completeAutomaton,
        ViewSettings.<S, OmegaAcceptance>builder().acceptance(BuchiAcceptance.INSTANCE).build());
      return cast(complement, expectedAcceptance);
    }

    if (acceptance instanceof GeneralizedBuchiAcceptance) {
      checkArgument(isInstanceOf(GeneralizedCoBuchiAcceptance.class, expectedAcceptance));

      var complementAcceptance
        = GeneralizedCoBuchiAcceptance.of(((GeneralizedBuchiAcceptance) acceptance).size);
      var complement = new AutomatonView<>(completeAutomaton,
        ViewSettings.<S, OmegaAcceptance>builder().acceptance(complementAcceptance).build());
      return cast(complement, expectedAcceptance);
    }

    if (acceptance instanceof GeneralizedCoBuchiAcceptance) {
      checkArgument(isInstanceOf(GeneralizedBuchiAcceptance.class, expectedAcceptance));

      var complementAcceptance
        = GeneralizedBuchiAcceptance.of(((GeneralizedCoBuchiAcceptance) acceptance).size);
      var complement = new AutomatonView<>(completeAutomaton,
        ViewSettings.<S, OmegaAcceptance>builder().acceptance(complementAcceptance).build());
      return cast(complement, expectedAcceptance);
    }

    if (acceptance instanceof ParityAcceptance) {
      checkArgument(isInstanceOf(ParityAcceptance.class, expectedAcceptance));

      var complementAcceptance
        = ((ParityAcceptance) automaton.acceptance()).complement();
      var complement = new AutomatonView<>(completeAutomaton,
        ViewSettings.<S, OmegaAcceptance>builder().acceptance(complementAcceptance).build());
      return cast(complement, expectedAcceptance);
    }

    throw new UnsupportedOperationException();
  }

  public static <S> Automaton<S, ?> complete(Automaton<S, ?> automaton, S trapState) {
    OmegaAcceptance acceptance = automaton.acceptance();

    if (acceptance instanceof AllAcceptance) {
      return new Complete<>(automaton, Edge.of(trapState, 0), CoBuchiAcceptance.INSTANCE);
    }

    return new Complete<>(automaton, Edge.of(trapState, acceptance.rejectingSet()), acceptance);
  }

  public static <S, A extends OmegaAcceptance> Automaton<Set<S>, A> createPowerSetAutomaton(
    Automaton<S, ?> automaton, A acceptance, boolean dropEmptySet) {
    return AutomatonFactory.create(automaton.factory(), automaton.initialStates(), acceptance,
      (Set<S> states, BitSet valuation) -> {
        Set<S> successors = states.stream()
          .flatMap(x -> automaton.successors(x, valuation).stream())
          .collect(Collectors.toUnmodifiableSet());
        return dropEmptySet && successors.isEmpty() ? null : Edge.of(successors);
      }
    );
  }

  public static <S, A extends OmegaAcceptance> Automaton<S, A> filter(Automaton<S, A> automaton,
    Predicate<S> statePredicate) {
    return createView(automaton, ViewSettings.<S, A>builder()
      .stateFilter(statePredicate)
      .build());
  }

  public static <S, A extends OmegaAcceptance> Automaton<S, A> filter(Automaton<S, A> automaton,
    @Nullable Predicate<S> states, Predicate<Edge<S>> edgeFilter) {
    return createView(automaton, ViewSettings.<S, A>builder()
      .edgeFilter((s, e) -> edgeFilter.test(e))
      .stateFilter(states)
      .build());
  }

  public static <S, A extends OmegaAcceptance> Automaton<S, A> filter(Automaton<S, A> automaton,
    @Nullable Predicate<S> states, BiPredicate<S, Edge<S>> edgeFilter) {
    return createView(automaton, ViewSettings.<S, A>builder()
      .edgeFilter(edgeFilter)
      .stateFilter(states)
      .build());
  }

  public static <S, A extends OmegaAcceptance> Automaton<S, A> remap(Automaton<S, A> automaton,
    IntUnaryOperator remappingOperator) {
    return createView(automaton, ViewSettings.<S, A>builder()
      .edgeRewriter(edge -> edge.withAcceptance(remappingOperator))
      .build());
  }

  public static <S, A extends OmegaAcceptance> Automaton<S, A> replaceInitialState(
    Automaton<S, A> automaton, Set<S> initialStates) {
    return createView(automaton, ViewSettings.<S, A>builder()
      .initialStates(Set.copyOf(initialStates))
      .build());
  }

  static <S, A extends OmegaAcceptance> Automaton<S, A> createView(
    Automaton<S, ?> automaton, ViewSettings<S, A> settings) {
    return new AutomatonView<>(automaton, settings);
  }

  static class Complete<S, A extends OmegaAcceptance, B extends OmegaAcceptance>
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
  abstract static class ViewSettings<S, A extends OmegaAcceptance> {
    @Nullable
    abstract A acceptance();

    @Nullable
    abstract Set<S> initialStates();

    @Nullable
    abstract Predicate<S> stateFilter();

    @Nullable
    abstract BiPredicate<S, Edge<S>> edgeFilter();

    @Nullable
    abstract Function<Edge<S>, Edge<S>> edgeRewriter();

    static <S, A extends OmegaAcceptance> Builder<S, A> builder() {
      return new AutoValue_Views_ViewSettings.Builder<>();
    }

    @AutoValue.Builder
    abstract static class Builder<S, A extends OmegaAcceptance> {
      abstract Builder<S, A> acceptance(@Nullable A acceptance);

      abstract Builder<S, A> initialStates(@Nullable Set<S> initialStates);

      abstract Builder<S, A> stateFilter(@Nullable Predicate<S> filter);

      abstract Builder<S, A> edgeFilter(@Nullable BiPredicate<S, Edge<S>> filter);

      abstract Builder<S, A> edgeRewriter(@Nullable Function<Edge<S>, Edge<S>> rewriter);

      abstract ViewSettings<S, A> build();
    }
  }

  public static final class AutomatonView<S, A extends OmegaAcceptance>
    extends AbstractImplicitAutomaton<S, A> {

    private final Automaton<S, ?> backingAutomaton;
    private final ViewSettings<S, A> settings;

    private AutomatonView(Automaton<S, ?> automaton, ViewSettings<S, A> settings) {
      super(automaton.factory(),
        initialStates(automaton, settings),
        acceptance(automaton, settings));
      this.backingAutomaton = automaton;
      this.settings = settings;
    }

    @SuppressWarnings("unchecked")
    private static <S, A extends OmegaAcceptance> A acceptance(
      Automaton<S, ?> automaton, ViewSettings<S, A> settings) {
      A acceptance = settings.acceptance();
      return acceptance == null ? (A) automaton.acceptance() : acceptance;
    }

    private static <S> Set<S> initialStates(
      Automaton<S, ?> automaton, ViewSettings<S, ?> settings) {
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
      var filter = settings.stateFilter();
      return filter == null || filter.test(state);
    }

    private Predicate<Edge<S>> edgeFilter(S state) {
      var filter = settings.edgeFilter();
      return edge -> (filter == null || filter.test(state, edge)) && stateFilter(edge.successor());
    }

    private boolean filterRequired() {
      return settings.stateFilter() != null || settings.edgeFilter() != null;
    }

    @Override
    public List<PreferredEdgeAccess> preferredEdgeAccess() {
      return backingAutomaton.preferredEdgeAccess();
    }

    @Override
    public Set<Edge<S>> edges(S state, BitSet valuation) {
      checkArgument(stateFilter(state));
      var filteredEdges = filterRequired()
        ? backingAutomaton.edges(state, valuation).stream()
            .filter(edgeFilter(state)).collect(Collectors.toSet())
        : backingAutomaton.edges(state, valuation);

      var edgeRewriter = settings.edgeRewriter();
      return edgeRewriter == null
        ? filteredEdges
        : Collections3.transformSet(filteredEdges, edgeRewriter);
    }

    @Override
    public Set<Edge<S>> edges(S state) {
      checkArgument(stateFilter(state));
      var filteredEdges = filterRequired()
        ? backingAutomaton.edges(state).stream()
            .filter(edgeFilter(state)).collect(Collectors.toSet())
        : backingAutomaton.edges(state);

      var edgeRewriter = settings.edgeRewriter();
      return edgeRewriter == null
        ? filteredEdges
        : Collections3.transformSet(filteredEdges, edgeRewriter);
    }

    @Override
    public Map<Edge<S>, ValuationSet> edgeMap(S state) {
      checkArgument(stateFilter(state));
      var filteredEdges = filterRequired()
        ? Maps.filterKeys(backingAutomaton.edgeMap(state), x -> edgeFilter(state).test(x))
        : backingAutomaton.edgeMap(state);

      var edgeRewriter = settings.edgeRewriter();
      return edgeRewriter == null
        ? filteredEdges
        : Collections3.transformMap(filteredEdges, edgeRewriter);
    }

    @Override
    public ValuationTree<Edge<S>> edgeTree(S state) {
      checkArgument(stateFilter(state));

      var edges = backingAutomaton.edgeTree(state);
      var edgeRewriter = settings.edgeRewriter();

      @Nullable
      Function<Set<Edge<S>>, Set<Edge<S>>> mapper;

      if (filterRequired()) {
        if (edgeRewriter == null) {
          mapper = x -> Sets.filter(x, y -> edgeFilter(state).test(y));
        } else {
          mapper = x -> Collections3.transformSet(
            Sets.filter(x, y -> edgeFilter(state).test(y)),
            edgeRewriter);
        }
      } else {
        if (edgeRewriter == null) {
          mapper = null;
        } else {
          mapper = x -> Collections3.transformSet(x, edgeRewriter);
        }
      }

      return mapper == null ? edges : edges.map(mapper);
    }
  }
}