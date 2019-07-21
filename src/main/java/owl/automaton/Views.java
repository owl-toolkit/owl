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

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.immutables.value.Value;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance.RabinPair;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.ParityAcceptance.Parity;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.collections.Collections3;
import owl.collections.ValuationSet;
import owl.collections.ValuationTree;
import owl.factories.ValuationSetFactory;
import owl.run.modules.ImmutableTransformerParser;
import owl.run.modules.OwlModuleParser;
import owl.run.modules.Transformer;

public final class Views {
  public static final Transformer COMPLETE = environment -> (input) ->
    Views.complete(AutomatonUtil.cast(input), new MutableAutomatonUtil.Sink());

  public static final OwlModuleParser.TransformerParser COMPLETE_CLI = ImmutableTransformerParser
    .builder()
    .key("complete")
    .description("Make the transition relation of an automaton complet by adding a sink-state.")
    .parser(settings -> COMPLETE)
    .build();

  private Views() {}

  public static <S> Automaton<S, OmegaAcceptance> complement(Automaton<S, ?> automaton) {
    return complement(automaton, null);
  }

  public static <S> Automaton<S, OmegaAcceptance> complement(Automaton<S, ?> automaton,
    @Nullable S trapState) {
    var completeAutomaton = trapState == null ? automaton : complete(automaton, trapState);

    checkArgument(completeAutomaton.is(Automaton.Property.COMPLETE), "Automaton is not complete.");
    checkArgument(!completeAutomaton.initialStates().isEmpty(), "Automaton is empty.");
    // Check is too costly.
    // checkArgument(completeAutomaton.is(DETERMINISTIC), "Automaton is not deterministic.");

    var acceptance = completeAutomaton.acceptance();

    if (acceptance instanceof BuchiAcceptance) {
      return createView(completeAutomaton, Views.<S, OmegaAcceptance>builder()
        .acceptance(CoBuchiAcceptance.INSTANCE).build());
    }

    if (acceptance instanceof CoBuchiAcceptance) {
      return createView(completeAutomaton, Views.<S, OmegaAcceptance>builder()
        .acceptance(BuchiAcceptance.INSTANCE).build());
    }

    if (acceptance instanceof ParityAcceptance) {
      var parityAcceptance = (ParityAcceptance) automaton.acceptance();
      return createView(completeAutomaton, Views.<S, OmegaAcceptance>builder()
        .acceptance(parityAcceptance.complement()).build());
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
    Set<S> states) {
    return createView(automaton, Views.<S, A>builder()
      .stateFilter(states::contains)
      .build());
  }

  public static <S, A extends OmegaAcceptance> Automaton<S, A> filter(Automaton<S, A> automaton,
    Set<S> states, Predicate<Edge<S>> edgeFilter) {
    return createView(automaton, Views.<S, A>builder()
      .edgeFilter(edgeFilter)
      .stateFilter(states::contains)
      .build());
  }

  public static <S, A extends OmegaAcceptance> Automaton<S, A> remap(Automaton<S, A> automaton,
    IntUnaryOperator remappingOperator) {
    return createView(automaton, Views.<S, A>builder()
      .edgeRewriter(edge -> edge.withAcceptance(remappingOperator))
      .build());
  }

  public static <S, A extends OmegaAcceptance> Automaton<S, A> replaceInitialState(
    Automaton<S, A> automaton, Set<S> initialStates) {
    return createView(automaton, Views.<S, A>builder().initialStates(initialStates).build());
  }

  static <S, A extends OmegaAcceptance> Automaton<S, A> createView(
    Automaton<S, ?> automaton, ViewSettings<S, A> settings) {
    return new AutomatonView<>(automaton, settings);
  }

  static <S, A extends OmegaAcceptance> ImmutableViewSettings.Builder<S, A> builder() {
    return ImmutableViewSettings.builder();
  }

  public static <S, A extends OmegaAcceptance> Automaton<S, A> viewAs(Automaton<S, ?> automaton,
    Class<A> acceptanceClazz) {
    if (acceptanceClazz.isInstance(automaton.acceptance())) {
      return AutomatonUtil.cast(automaton, acceptanceClazz);
    }

    if (ParityAcceptance.class.equals(acceptanceClazz)) {
      checkArgument(automaton.acceptance() instanceof BuchiAcceptance);

      var remapping = Views.<S, ParityAcceptance>builder()
        .acceptance(new ParityAcceptance(2, Parity.MIN_EVEN))
        .edgeRewriter(edge -> edge.inSet(0) ? edge : Edge.of(edge.successor(), 1))
        .build();

      return AutomatonUtil.cast(createView(automaton, remapping), acceptanceClazz);
    }

    if (RabinAcceptance.class.equals(acceptanceClazz)) {
      checkArgument(automaton.acceptance() instanceof BuchiAcceptance);

      var remapping = Views.<S, RabinAcceptance>builder()
        .acceptance(RabinAcceptance.of(RabinPair.of(0)))
        .edgeRewriter(edge -> edge.withAcceptance(x -> x + 1))
        .build();

      return AutomatonUtil.cast(createView(automaton, remapping), acceptanceClazz);
    }

    if (GeneralizedRabinAcceptance.class.equals(acceptanceClazz)) {
      checkArgument(automaton.acceptance() instanceof GeneralizedBuchiAcceptance);

      int sets = automaton.acceptance().acceptanceSets();
      var remapping = Views.<S, GeneralizedRabinAcceptance>builder()
        .acceptance(GeneralizedRabinAcceptance.of(RabinPair.ofGeneralized(0, sets)))
        .edgeRewriter(edge -> edge.withAcceptance(x -> x + 1))
        .build();

      return AutomatonUtil.cast(createView(automaton, remapping), acceptanceClazz);
    }

    if (BuchiAcceptance.class.equals(acceptanceClazz)
      || GeneralizedBuchiAcceptance.class.equals(acceptanceClazz)) {
      checkArgument(automaton.acceptance() instanceof AllAcceptance);

      var remapping = Views.<S, BuchiAcceptance>builder()
        .acceptance(BuchiAcceptance.INSTANCE)
        .edgeRewriter(edge -> edge.withAcceptance(0))
        .build();

      return AutomatonUtil.cast(createView(automaton, remapping), acceptanceClazz);
    }

    if (EmersonLeiAcceptance.class.equals(acceptanceClazz)) {
      var acceptance = automaton.acceptance();
      var remapping = Views.<S, EmersonLeiAcceptance>builder()
        .acceptance(
          new EmersonLeiAcceptance(acceptance.acceptanceSets(), acceptance.booleanExpression()))
        .build();

      return AutomatonUtil.cast(createView(automaton, remapping), acceptanceClazz);
    }

    throw new UnsupportedOperationException();
  }

  public static <S> Automaton<S, NoneAcceptance> viewAsLts(Automaton<S, ?> automaton) {
    var remapping = Views.<S, NoneAcceptance>builder()
      .acceptance(NoneAcceptance.INSTANCE)
      .edgeRewriter(Edge::withoutAcceptance)
      .build();

    return createView(automaton, remapping);
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
      return property == Automaton.Property.COMPLETE || automaton.is(property);
    }
  }

  @Value.Immutable
  abstract static class ViewSettings<S, A extends OmegaAcceptance> {
    @Nullable
    abstract A acceptance();

    @Nullable
    abstract Set<S> initialStates();

    @Nullable
    abstract Predicate<S> stateFilter();

    @Nullable
    abstract Predicate<Edge<S>> edgeFilter();

    @Nullable
    abstract Function<Edge<S>, Edge<S>> edgeRewriter();

    @Value.Default
    Map<Automaton.Property, Boolean> properties() {
      return Map.of();
    }
  }

  public static class AutomatonView<S, A extends OmegaAcceptance>
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

    private boolean edgeFilter(Edge<S> edge) {
      var filter = settings.edgeFilter();
      return (filter == null || filter.test(edge)) && stateFilter(edge.successor());
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
        ? Sets.filter(backingAutomaton.edges(state, valuation), this::edgeFilter)
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
        ? Sets.filter(backingAutomaton.edges(state), this::edgeFilter)
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
        ? Maps.filterKeys(backingAutomaton.edgeMap(state), this::edgeFilter)
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
          mapper = x -> Sets.filter(x, this::edgeFilter);
        } else {
          mapper = x -> Collections3.transformSet(Sets.filter(x, this::edgeFilter), edgeRewriter);
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

    @Override
    public boolean is(Property property) {
      Boolean value = settings.properties().get(property);
      return value == null ? super.is(property) : value;
    }
  }
}