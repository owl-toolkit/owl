/*
 * Copyright (C) 2016  (See AUTHORS)
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
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
      return new ReplaceAcceptance<>(completeAutomaton, CoBuchiAcceptance.INSTANCE);
    }

    if (acceptance instanceof CoBuchiAcceptance) {
      return new ReplaceAcceptance<>(completeAutomaton, BuchiAcceptance.INSTANCE);
    }

    if (acceptance instanceof ParityAcceptance) {
      var parityAcceptance = (ParityAcceptance) automaton.acceptance();
      return new ReplaceAcceptance<>(completeAutomaton, parityAcceptance.complement());
    }

    throw new UnsupportedOperationException();
  }

  public static <S, A extends OmegaAcceptance> Automaton<S, A> complete(Automaton<S, A> automaton,
    S trapState) {
    var edge = Edge.of(trapState);

    // Patching parity automata
    if (automaton.acceptance() instanceof ParityAcceptance) {
      checkArgument(((ParityAcceptance) automaton.acceptance()).parity() == Parity.MIN_ODD);
      checkArgument(automaton.acceptance().acceptanceSets() >= 1);
      edge = edge.withAcceptance(0);
    }

    return new Complete<>(automaton, edge, automaton.acceptance());
  }

  public static <S> Automaton<S, CoBuchiAcceptance> completeAllAcceptance(
    Automaton<S, AllAcceptance> automaton, S trapState) {
    return new Complete<>(automaton, Edge.of(trapState, 0), CoBuchiAcceptance.INSTANCE);
  }

  public static <S> Automaton<Set<S>, NoneAcceptance> createPowerSetAutomaton(
    Automaton<S, NoneAcceptance> automaton) {
    return AutomatonFactory.create(automaton.initialStates(), automaton.factory(), (x, y) -> {
      Set<S> builder = new HashSet<>();
      x.forEach(s -> builder.addAll(automaton.successors(s, y)));
      return Edge.of(Set.copyOf(builder));
    }, NoneAcceptance.INSTANCE);
  }

  public static <S, A extends OmegaAcceptance> Automaton<S, A> filter(Automaton<S, A> automaton,
    Set<S> states) {
    // TODO Add option to pass initial states too - maybe FilterBuilder pattern?
    // Note: FilteredAutomaton always adds the "successor is in states"-edge-filter.
    return new FilteredAutomaton<>(automaton, states, e -> true);
  }

  public static <S, A extends OmegaAcceptance> Automaton<S, A> filter(Automaton<S, A> automaton,
    Set<S> states, Predicate<Edge<S>> edgeFilter) {
    return new FilteredAutomaton<>(automaton, states, edgeFilter);
  }

  public static <S, A extends OmegaAcceptance> Automaton<S, A> remap(Automaton<S, A> automaton,
    IntUnaryOperator remappingOperator) {
    return AutomatonFactory.create(automaton.initialState(), automaton.factory(),
      (state, valuation) -> automaton.edge(state, valuation).withAcceptance(remappingOperator),
      automaton.acceptance());
  }

  public static <S, A extends OmegaAcceptance> Automaton<S, A> replaceInitialState(
    Automaton<S, A> automaton, Set<S> initialStates) {
    Set<S> immutableInitialStates = Set.copyOf(initialStates);
    return new ForwardingAutomaton<>(automaton) {
      @Override
      public A acceptance() {
        return automaton.acceptance();
      }

      @Override
      public Set<S> initialStates() {
        return immutableInitialStates;
      }
    };
  }

  static class Complete<S, A extends OmegaAcceptance, B extends OmegaAcceptance>
    extends ForwardingAutomaton<S, B, A, Automaton<S, A>> {
    private final Edge<S> loop;
    private final S sink;
    private final B acceptance;

    Complete(Automaton<S, A> automaton, Edge<S> loop, B acceptance) {
      super(automaton);
      this.sink = loop.successor();
      this.loop = loop;
      this.acceptance = acceptance;
    }

    @Override
    public B acceptance() {
      return acceptance;
    }

    @Override
    public Set<Edge<S>> edges(S state, BitSet valuation) {
      if (sink.equals(state)) {
        return Set.of(loop);
      }

      Set<Edge<S>> edges = automaton.edges(state, valuation);

      if (edges.isEmpty()) {
        return Set.of(loop);
      }

      return edges;
    }

    @Override
    public Collection<LabelledEdge<S>> labelledEdges(S state) {
      ValuationSetFactory factory = factory();

      if (sink.equals(state)) {
        return Set.of(LabelledEdge.of(loop, factory.universe()));
      }

      List<LabelledEdge<S>> edges = new ArrayList<>(automaton.labelledEdges(state));
      ValuationSet complement = factory.union(LabelledEdges.valuations(edges)).complement();

      if (!complement.isEmpty()) {
        return Collections3.concat(edges, List.of(LabelledEdge.of(loop, complement)));
      }

      return edges;
    }

    @Override
    public boolean is(Property property) {
      return property == COMPLETE || super.is(property);
    }

    @Override
    public Set<S> states() {
      return AutomatonUtil.getReachableStates(this);
    }
  }

  private static final class FilteredAutomaton<S, A extends OmegaAcceptance>
    extends ForwardingAutomaton<S, A, A, Automaton<S, A>> {

    private final Predicate<LabelledEdge<S>> edgeFilter;
    private final Set<S> states;

    FilteredAutomaton(Automaton<S, A> automaton, Set<S> states, Predicate<Edge<S>> edgeFilter) {
      super(automaton);
      this.states = Set.copyOf(states);
      this.edgeFilter = x -> states.contains(x.edge.successor()) && edgeFilter.test(x.edge);
    }

    @Override
    public A acceptance() {
      return automaton.acceptance();
    }

    @Override
    public Set<S> initialStates() {
      return Sets.intersection(automaton.initialStates(), states);
    }

    @Override
    public Collection<LabelledEdge<S>> labelledEdges(S state) {
      checkArgument(states.contains(state), "State %s not in set", state);
      return Collections2.filter(automaton.labelledEdges(state), edgeFilter::test);
    }

    @Override
    public Set<S> states() {
      return Sets.intersection(states, automaton.states());
    }
  }

  public abstract static class ForwardingAutomaton<S, A extends OmegaAcceptance,
    B extends OmegaAcceptance, T extends Automaton<S, B>> implements Automaton<S, A> {
    protected final T automaton;

    protected ForwardingAutomaton(T automaton) {
      this.automaton = automaton;
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
    public Collection<LabelledEdge<S>> labelledEdges(S state) {
      return automaton.labelledEdges(state);
    }

    @Override
    public Set<S> states() {
      return automaton.states();
    }
  }

  public abstract static class ForwardingMutableAutomaton<S, A extends OmegaAcceptance,
    B extends OmegaAcceptance>
    extends ForwardingAutomaton<S, A, B, MutableAutomaton<S, B>>
    implements MutableAutomaton<S, A> {

    protected ForwardingMutableAutomaton(MutableAutomaton<S, B> automaton) {
      super(automaton);
    }

    @Override
    public void addEdge(S source, BitSet valuation, Edge<? extends S> edge) {
      automaton.addEdge(source, valuation, edge);
    }

    @Override
    public void addEdge(S source, ValuationSet valuations, Edge<? extends S> edge) {
      automaton.addEdge(source, valuations, edge);
    }

    @Override
    public void addStates(Collection<? extends S> states) {
      automaton.addStates(states);
    }

    @Override
    public void updateEdges(Set<? extends S> states, BiFunction<? super S, Edge<S>, Edge<S>> f) {
      automaton.updateEdges(states, f);
    }

    @Override
    public void removeEdge(S source, BitSet valuation, S destination) {
      automaton.removeEdge(source, valuation, destination);
    }

    @Override
    public void removeEdge(S source, ValuationSet valuations, S destination) {
      automaton.removeEdge(source, valuations, destination);
    }

    @Override
    public void removeEdges(S source, S destination) {
      automaton.removeEdges(source, destination);
    }

    @Override
    public boolean removeStates(Predicate<? super S> states) {
      return automaton.removeStates(states);
    }

    @Override
    public void removeUnreachableStates(Collection<? extends S> start,
      Consumer<? super S> removedStatesConsumer) {
      automaton.removeUnreachableStates(start, removedStatesConsumer);
    }

    @Override
    public void initialStates(Collection<? extends S> states) {
      automaton.initialStates(states);
    }
  }

  static class ReplaceAcceptance<S, A extends OmegaAcceptance,
    B extends OmegaAcceptance> extends ForwardingAutomaton<S, A,
    B, Automaton<S, B>> {
    private final A acceptance;

    ReplaceAcceptance(Automaton<S, B> automaton, A acceptance) {
      super(automaton);
      this.acceptance = acceptance;
    }

    @Override
    public A acceptance() {
      return acceptance;
    }
  }

  // TODO: Merge with cast?
  public static <S, A extends OmegaAcceptance> Automaton<S, A> viewAs(Automaton<S, ?> automaton,
    Class<A> acceptanceClazz) {
    if (ParityAcceptance.class.equals(acceptanceClazz)) {
      checkArgument(automaton.acceptance() instanceof BuchiAcceptance);
      return AutomatonUtil.cast(new Buchi2Parity<>(
        AutomatonUtil.cast(automaton, BuchiAcceptance.class)), acceptanceClazz);
    }

    if (RabinAcceptance.class.equals(acceptanceClazz)) {
      checkArgument(automaton.acceptance() instanceof BuchiAcceptance);
      return AutomatonUtil.cast(new Buchi2Rabin<>(
        AutomatonUtil.cast(automaton, BuchiAcceptance.class)), acceptanceClazz);
    }

    if (GeneralizedRabinAcceptance.class.equals(acceptanceClazz)) {
      checkArgument(automaton.acceptance() instanceof GeneralizedBuchiAcceptance);
      return AutomatonUtil.cast(new GenBuchi2GenRabin<>(
        AutomatonUtil.cast(automaton, GeneralizedBuchiAcceptance.class)), acceptanceClazz);
    }

    throw new UnsupportedOperationException();
  }

  private static final class Buchi2Parity<S> extends
    ForwardingAutomaton<S, ParityAcceptance, BuchiAcceptance, Automaton<S, BuchiAcceptance>> {
    private final ParityAcceptance acceptance;

    Buchi2Parity(Automaton<S, BuchiAcceptance> backingAutomaton) {
      super(backingAutomaton);
      acceptance = new ParityAcceptance(2, Parity.MIN_EVEN);
    }

    private Edge<S> convertBuchiToParity(Edge<S> edge) {
      return edge.inSet(0) ? edge : Edge.of(edge.successor(), 1);
    }

    @Override
    public ParityAcceptance acceptance() {
      return acceptance;
    }

    @Override
    public Collection<LabelledEdge<S>> labelledEdges(S state) {
      return Collections2.transform(super.labelledEdges(state), labelledEdge ->
        LabelledEdge.of(convertBuchiToParity(labelledEdge.edge), labelledEdge.valuations));
    }

    @Nullable
    @Override
    public S successor(S state, BitSet valuation) {
      return automaton.successor(state, valuation);
    }

    @Override
    public Set<S> successors(S state) {
      return automaton.successors(state);
    }

    @Override
    public boolean is(@Nonnull Property property) {
      return property.equals(Property.COLOURED) || super.is(property);
    }
  }

  private static final class Buchi2Rabin<S> extends
    ForwardingAutomaton<S, RabinAcceptance, BuchiAcceptance, Automaton<S, BuchiAcceptance>> {
    private final RabinAcceptance acceptance;

    Buchi2Rabin(Automaton<S, BuchiAcceptance> backingAutomaton) {
      super(backingAutomaton);
      acceptance = RabinAcceptance.of(RabinPair.of(0));
    }

    private Edge<S> convertBuchiToRabin(Edge<S> edge) {
      return edge.withAcceptance(x -> x + 1);
    }

    @Override
    public RabinAcceptance acceptance() {
      return acceptance;
    }

    @Override
    public Collection<LabelledEdge<S>> labelledEdges(S state) {
      return Collections2.transform(super.labelledEdges(state), labelledEdge ->
        LabelledEdge.of(convertBuchiToRabin(labelledEdge.edge), labelledEdge.valuations));
    }

    @Nullable
    @Override
    public S successor(S state, BitSet valuation) {
      return automaton.successor(state, valuation);
    }

    @Override
    public Set<S> successors(S state) {
      return automaton.successors(state);
    }
  }

  private static final class GenBuchi2GenRabin<S> extends
    ForwardingAutomaton<S, GeneralizedRabinAcceptance, GeneralizedBuchiAcceptance,
      Automaton<S, GeneralizedBuchiAcceptance>> {
    private final GeneralizedRabinAcceptance acceptance;

    GenBuchi2GenRabin(Automaton<S, GeneralizedBuchiAcceptance> backingAutomaton) {
      super(backingAutomaton);
      int sets = backingAutomaton.acceptance().acceptanceSets();
      acceptance = GeneralizedRabinAcceptance.of(RabinPair.ofGeneralized(0, sets));
    }

    @Override
    public GeneralizedRabinAcceptance acceptance() {
      return acceptance;
    }

    @Override
    public Collection<LabelledEdge<S>> labelledEdges(S state) {
      return Collections2.transform(super.labelledEdges(state),
        labelledEdge -> LabelledEdge
          .of(labelledEdge.edge.withAcceptance(x -> x + 1), labelledEdge.valuations));
    }

    @Nullable
    @Override
    public S successor(S state, BitSet valuation) {
      return automaton.successor(state, valuation);
    }

    @Override
    public Set<S> successors(S state) {
      return automaton.successors(state);
    }
  }
}
