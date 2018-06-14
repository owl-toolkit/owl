/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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

import com.google.common.collect.Collections2;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashSet;
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
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance.RabinPair;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.ParityAcceptance.Parity;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.LabelledEdge;
import owl.automaton.edge.LabelledEdges;
import owl.collections.Collections3;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;

public final class Views {
  private Views() {}

  public static <S> Automaton<S, OmegaAcceptance> complement(Automaton<S, ?> automaton) {
    return complement(automaton, null);
  }

  public static <S> Automaton<S, OmegaAcceptance> complement(Automaton<S, ?> automaton,
    @Nullable S trapState) {
    var completeAutomaton = trapState == null ? automaton : complete(automaton, trapState);

    checkArgument(completeAutomaton.is(COMPLETE), "Automaton is not complete.");
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

  public static <S, A extends OmegaAcceptance> Automaton<S, A> complete(Automaton<S, A> automaton,
    S trapState) {
    A acceptance = automaton.acceptance();
    return new Complete<>(automaton, Edge.of(trapState, acceptance.rejectingSet()), acceptance);
  }

  public static <S> Automaton<S, CoBuchiAcceptance> completeAllAcceptance(
    Automaton<S, AllAcceptance> automaton, S trapState) {
    return new Complete<>(automaton, Edge.of(trapState, 0), CoBuchiAcceptance.INSTANCE);
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

    throw new UnsupportedOperationException();
  }

  public static <S> Automaton<S, NoneAcceptance> viewAsLts(Automaton<S, ?> automaton) {
    var remapping = Views.<S, NoneAcceptance>builder()
      .acceptance(NoneAcceptance.INSTANCE)
      .edgeRewriter(edge -> Edge.of(edge.successor()))
      .build();

    return createView(automaton, remapping);
  }

  static class Complete<S, A extends OmegaAcceptance, B extends OmegaAcceptance>
    implements Automaton<S, B> {

    private final Edge<S> sinkEdge;
    private final Automaton<S, A> automaton;
    private final S sink;
    private final B acceptance;

    @Nullable
    private Map<S, ValuationSet> incompleteStates;

    Complete(Automaton<S, A> automaton, Edge<S> sinkEdge, B acceptance) {
      this.automaton = automaton;
      this.sink = sinkEdge.successor();
      this.sinkEdge = sinkEdge;
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
    public boolean prefersLabelled() {
      return automaton.prefersLabelled();
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
        return Sets.union(automaton.states(), Set.of(sink));
      }
    }

    @Override
    public Set<S> successors(S state) {
      if (sink.equals(state)) {
        return Set.of(sink);
      }

      if (incompleteStates != null && incompleteStates.containsKey(state)) {
        return Sets.union(automaton.states(), Set.of(sink));
      }

      return new HashSet<>(LabelledEdges.successors(labelledEdges(state)));
    }

    @Override
    public Collection<Edge<S>> edges(S state, BitSet valuation) {
      if (sink.equals(state)) {
        return List.of(sinkEdge);
      }

      Collection<Edge<S>> edges = automaton.edges(state, valuation);
      return edges.isEmpty() ? List.of(sinkEdge) : edges;
    }

    @Override
    public Collection<Edge<S>> edges(S state) {
      if (sink.equals(state)) {
        return List.of(sinkEdge);
      }

      if (incompleteStates != null && incompleteStates.containsKey(state)) {
        return Collections3.append(automaton.edges(state), sinkEdge);
      }

      return LabelledEdges.edges(labelledEdges(state));
    }

    @Override
    public Collection<LabelledEdge<S>> labelledEdges(S state) {
      ValuationSetFactory factory = automaton.factory();

      if (sink.equals(state)) {
        return List.of(LabelledEdge.of(sinkEdge, factory.universe()));
      }

      if (incompleteStates != null && incompleteStates.containsKey(state)) {
        return Collections3.append(automaton.labelledEdges(state),
          LabelledEdge.of(sinkEdge, incompleteStates.get(state)));
      }

      List<LabelledEdge<S>> edges = new ArrayList<>(automaton.labelledEdges(state));
      ValuationSet complement = factory.union(LabelledEdges.valuations(edges)).complement();

      if (!complement.isEmpty()) {
        edges.add(LabelledEdge.of(sinkEdge, complement));
      }

      return edges;
    }

    @Override
    public boolean is(Property property) {
      return property == COMPLETE || automaton.is(property);
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
    extends ImplicitCachedStatesAutomaton<S, A> {

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

    @Override
    public boolean prefersLabelled() {
      return backingAutomaton.prefersLabelled();
    }

    @Override
    public Collection<Edge<S>> edges(S state, BitSet valuation) {
      checkArgument(stateFilter(state));
      var filteredEdges = Collections2.filter(
        backingAutomaton.edges(state, valuation), this::edgeFilter);
      var edgeRewriter = settings.edgeRewriter();
      return edgeRewriter == null
        ? filteredEdges
        : Collections2.transform(filteredEdges, edgeRewriter::apply);
    }

    @Override
    public Collection<Edge<S>> edges(S state) {
      checkArgument(stateFilter(state));
      var filteredEdges = Collections2.filter(backingAutomaton.edges(state), this::edgeFilter);
      var edgeRewriter = settings.edgeRewriter();
      return edgeRewriter == null
        ? filteredEdges
        : Collections2.transform(filteredEdges, edgeRewriter::apply);
    }

    @Override
    public Collection<LabelledEdge<S>> labelledEdges(S state) {
      checkArgument(stateFilter(state));
      var filteredEdges = Collections2.filter(
        backingAutomaton.labelledEdges(state), x -> edgeFilter(x.edge));
      var edgeRewriter = settings.edgeRewriter();
      return edgeRewriter == null
        ? filteredEdges
        : Collections2.transform(filteredEdges, labelledEdge -> labelledEdge.map(edgeRewriter));
    }

    @Override
    public Set<S> successors(S state) {
      return edges(state).stream().map(Edge::successor).collect(Collectors.toSet());
    }

    @Override
    public boolean is(Property property) {
      Boolean value = settings.properties().get(property);
      return value == null ? super.is(property) : value;
    }
  }
}