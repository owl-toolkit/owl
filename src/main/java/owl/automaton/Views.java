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

import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import de.tum.in.naturals.bitset.BitSets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import owl.automaton.Automaton.Property;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.LabelledEdge;
import owl.collections.Collections3;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;

public final class Views {
  private Views() {
  }

  public static <S> Automaton<S, OmegaAcceptance> complement(Automaton<S, ?> automaton,
    @Nullable S trapState) {
    Automaton<S, ?> completeAutomaton =
      trapState != null ? new Complete<>(automaton, trapState) : automaton;
    assert completeAutomaton.is(Property.COMPLETE) : "Automaton is not complete.";
    OmegaAcceptance acceptance = completeAutomaton.getAcceptance();

    if (acceptance instanceof BuchiAcceptance) {
      return new ReplaceAcceptance<>(completeAutomaton, CoBuchiAcceptance.INSTANCE);
    }

    if (acceptance instanceof CoBuchiAcceptance) {
      return new ReplaceAcceptance<>(completeAutomaton, BuchiAcceptance.INSTANCE);
    }

    throw new UnsupportedOperationException();
  }

  public static <S, A extends OmegaAcceptance> Automaton<S, A> complete(Automaton<S, A> automaton,
    S trapState) {
    return new Complete<>(automaton, trapState);
  }

  public static <S> PowerSetAutomaton<S> createPowerSetAutomaton(
    Automaton<S, NoneAcceptance> automaton) {
    return new PowerSetAutomaton<>(automaton);
  }

  public static <S, U extends OmegaAcceptance> Automaton<List<S>, U>
  createProductAutomaton(U acceptance, List<S> initialState, Automaton<S, ?> automaton) {
    return AutomatonFactory.createStreamingAutomaton(acceptance,
      initialState, automaton.getFactory(), (x, y) -> ProductAutomaton.explore(x, y, automaton));
  }

  public static <S, A extends OmegaAcceptance> Automaton<S, A> filter(Automaton<S, A> automaton,
    Set<S> states) {
    return new FilteredAutomaton<>(automaton, states, x -> true);
  }

  public static <S, A extends OmegaAcceptance> Automaton<S, A> filter(Automaton<S, A> automaton,
    Set<S> states, Predicate<Edge<S>> edgeFilter) {
    return new FilteredAutomaton<>(automaton, states, edgeFilter);
  }

  static class Complete<S, A extends OmegaAcceptance>
    extends ForwardingAutomaton<S, A, A, Automaton<S, A>> {
    private final Edge<S> loop;
    private final S sink;

    Complete(Automaton<S, A> automaton, S sink) {
      super(automaton);
      this.sink = sink;
      this.loop = Edge.of(sink);
    }

    @Override
    public A getAcceptance() {
      return automaton.getAcceptance();
    }

    @Override
    public Set<Edge<S>> getEdges(S state, BitSet valuation) {
      if (sink.equals(state)) {
        return Set.of(loop);
      }

      Set<Edge<S>> edges = automaton.getEdges(state, valuation);

      if (edges.isEmpty()) {
        return Set.of(loop);
      }

      return edges;
    }

    @Override
    public Collection<LabelledEdge<S>> getLabelledEdges(S state) {
      if (sink.equals(state)) {
        return Set.of(LabelledEdge.of(loop, getFactory().createUniverseValuationSet()));
      }

      ValuationSet valuations = getFactory().createEmptyValuationSet();
      Collection<LabelledEdge<S>> labelledEdges = automaton.getLabelledEdges(state);
      labelledEdges.forEach(x -> valuations.addAll(x.valuations));

      ValuationSet complement = valuations.complement();
      valuations.free();

      if (!complement.isEmpty()) {
        return Collections3.concat(labelledEdges, Set.of(LabelledEdge.of(loop, complement)));
      }

      return labelledEdges;
    }

    @Override
    public Set<S> getStates() {
      return AutomatonUtil.getReachableStates(this);
    }
  }

  private static final class FilteredAutomaton<S, A extends OmegaAcceptance>
    extends ForwardingAutomaton<S, A, A, Automaton<S, A>> {

    private final Predicate<LabelledEdge<S>> edgeFilter;
    private final ImmutableSet<S> states;

    FilteredAutomaton(Automaton<S, A> automaton, Set<S> states,
      Predicate<Edge<S>> edgeFilter) {
      super(automaton);
      this.states = ImmutableSet.copyOf(states);
      this.edgeFilter = x -> states.contains(x.edge.getSuccessor()) && edgeFilter.test(x.edge);
    }

    @Override
    public A getAcceptance() {
      return automaton.getAcceptance();
    }

    @Override
    public Set<S> getInitialStates() {
      return Sets.intersection(automaton.getInitialStates(), states);
    }

    @Override
    public Collection<LabelledEdge<S>> getLabelledEdges(S state) {
      checkArgument(states.contains(state), "State %s not in set", state);
      return Collections2.filter(automaton.getLabelledEdges(state), edgeFilter::test);
    }

    @Override
    public Set<S> getStates() {
      return Sets.filter(states, automaton::containsState);
    }
  }

  public abstract static class ForwardingAutomaton<S, A extends OmegaAcceptance,
    B extends OmegaAcceptance, T extends Automaton<S, B>> implements Automaton<S, A> {
    protected final T automaton;

    ForwardingAutomaton(T automaton) {
      this.automaton = automaton;
    }

    @Override
    public ValuationSetFactory getFactory() {
      return automaton.getFactory();
    }

    @Override
    public Set<S> getInitialStates() {
      return automaton.getInitialStates();
    }

    @Override
    public Collection<LabelledEdge<S>> getLabelledEdges(S state) {
      return automaton.getLabelledEdges(state);
    }

    @Override
    public Set<S> getStates() {
      return automaton.getStates();
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
    public void addInitialStates(Collection<? extends S> states) {
      automaton.addInitialStates(states);
    }

    @Override
    public void addStates(Collection<? extends S> states) {
      automaton.addStates(states);
    }

    @Override
    public void free() {
      automaton.free();
    }

    @Override
    public void remapEdges(Set<? extends S> states, BiFunction<? super S, Edge<S>, Edge<S>> f) {
      automaton.remapEdges(states, f);
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
    public void setInitialStates(Collection<? extends S> states) {
      automaton.setInitialStates(states);
    }
  }

  private static final class PowerSetAutomaton<S> implements Automaton<Set<S>, NoneAcceptance> {
    private final Automaton<S, NoneAcceptance> automaton;

    PowerSetAutomaton(Automaton<S, NoneAcceptance> automaton) {
      this.automaton = automaton;
    }

    @Override
    public NoneAcceptance getAcceptance() {
      return automaton.getAcceptance();
    }

    @Override
    public ValuationSetFactory getFactory() {
      return automaton.getFactory();
    }

    @Override
    public Set<Set<S>> getInitialStates() {
      return Set.of(automaton.getInitialStates());
    }

    @Override
    public Collection<LabelledEdge<Set<S>>> getLabelledEdges(Set<S> state) {
      List<LabelledEdge<Set<S>>> edges = new ArrayList<>();

      for (BitSet valuation : BitSets.powerSet(getFactory().getSize())) {
        edges.add(LabelledEdge.of(getSuccessor(state, valuation),
          getFactory().createValuationSet(valuation)));
      }

      return edges;
    }

    @Override
    public Set<Set<S>> getStates() {
      return AutomatonUtil.getReachableStates(this, getInitialStates());
    }

    @Override
    @Nonnull
    public Set<S> getSuccessor(Set<S> state, BitSet valuation) {
      ImmutableSet.Builder<S> builder = ImmutableSet.builder();
      state.forEach(s -> builder.addAll(automaton.getSuccessors(s, valuation)));
      return builder.build();
    }

    @Override
    public Set<Set<S>> getSuccessors(Set<S> state, BitSet valuation) {
      return Set.of(getSuccessor(state, valuation));
    }

    @Override
    public Set<Set<S>> getSuccessors(Set<S> state) {
      ImmutableSet.Builder<Set<S>> builder = ImmutableSet.builder();

      for (BitSet valuation : BitSets.powerSet(getFactory().getSize())) {
        builder.add(getSuccessor(state, valuation));
      }

      return builder.build();
    }
  }

  private static final class ProductAutomaton {

    private ProductAutomaton() {
    }

    private static <S> Edge<List<S>> explore(List<S> state, BitSet valuation,
      Automaton<S, ?> automaton) {
      List<S> successor = new ArrayList<>(state.size());
      BitSet acceptance = new BitSet();
      Iterator<S> it = state.iterator();

      while (it.hasNext()) {
        S next = it.next();
        // TODO Non-determinism?
        Edge<S> edge = automaton.getEdge(next, valuation);

        if (edge == null) {
          continue;
        }

        if (it.hasNext()) {
          edge.acceptanceSetIterator().forEachRemaining((IntConsumer) acceptance::set);
        } else {
          edge.acceptanceSetIterator().forEachRemaining((int x) -> acceptance.set(x + 1));
        }

        successor.add(edge.getSuccessor());
      }

      return Edge.of(successor, acceptance);
    }
  }

  static class ReplaceAcceptance<S, A extends OmegaAcceptance,
    B extends OmegaAcceptance> extends ForwardingAutomaton<S, A,
    B, Automaton<S, B>> {
    private final A acceptance;

    public ReplaceAcceptance(Automaton<S, B> automaton, A acceptance) {
      super(automaton);
      this.acceptance = acceptance;
    }

    @Override
    public A getAcceptance() {
      return acceptance;
    }
  }
}