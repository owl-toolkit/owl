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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import de.tum.in.naturals.bitset.BitSets;
import it.unimi.dsi.fastutil.ints.IntIterable;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.automaton.edge.LabelledEdge;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;

public final class AutomatonFactory {
  private AutomatonFactory() {}

  public static <S> PowerSetAutomaton<S> createPowerSetAutomaton(
    Automaton<S, NoneAcceptance> automaton) {
    return new PowerSetAutomaton<>(automaton);
  }

  public static <S, U extends OmegaAcceptance> Automaton<List<S>, U>
  createProductAutomaton(U acceptance, List<S> initialState, Automaton<S, ?> automaton) {
    return createStreamingAutomaton(acceptance,
      initialState, automaton.getFactory(), (x, y) -> ProductAutomaton.explore(x, y, automaton));
  }

  public static <S, A extends OmegaAcceptance> Automaton<S, A> createStreamingAutomaton(
    A acceptance, S initialState, ValuationSetFactory factory,
    BiFunction<S, BitSet, Edge<S>> transitions) {
    return new StreamingAutomaton<>(acceptance, factory, ImmutableSet.of(initialState),
      transitions);
  }

  public static <S> Automaton<S, NoneAcceptance> empty(ValuationSetFactory factory) {
    return new EmptyAutomaton<>(factory);
  }

  public static <S, A extends OmegaAcceptance> Automaton<S, A> filter(Automaton<S, A> automaton,
    Set<S> states) {
    return new FilteredAutomaton<>(automaton, states, x -> true);
  }

  public static <S, A extends OmegaAcceptance> Automaton<S, A> filter(Automaton<S, A> automaton,
    Set<S> states, Predicate<Edge<S>> edgeFilter) {
    return new FilteredAutomaton<>(automaton, states, edgeFilter);
  }

  public static <S> Automaton<S, NoneAcceptance> singleton(S state, ValuationSetFactory factory) {
    return singleton(state, factory, NoneAcceptance.INSTANCE);
  }

  public static <S, A extends OmegaAcceptance> Automaton<S, A> singleton(S state,
    ValuationSetFactory factory, A acceptance) {
    return new SingletonAutomaton<>(state, factory, Collections.emptyMap(), acceptance);
  }

  public static <S, A extends OmegaAcceptance> Automaton<S, A> singleton(S state,
    ValuationSetFactory factory, A acceptance, IntSet acceptanceSet) {
    Map<IntIterable, ValuationSet> loopAcceptanceSets = Collections
      .singletonMap(acceptanceSet, factory.createUniverseValuationSet());
    return new SingletonAutomaton<>(state, factory, loopAcceptanceSets, acceptance);
  }

  public static <S> Automaton<S, AllAcceptance> universe(S state, ValuationSetFactory factory) {
    Map<IntIterable, ValuationSet> selfLoop = Collections.singletonMap(IntLists.EMPTY_LIST,
      factory.createUniverseValuationSet());
    return new SingletonAutomaton<>(state, factory, selfLoop, AllAcceptance.INSTANCE);
  }

  private static final class EmptyAutomaton<S> implements Automaton<S, NoneAcceptance> {
    private final ValuationSetFactory factory;

    EmptyAutomaton(ValuationSetFactory factory) {
      this.factory = factory;
    }

    @Override
    public NoneAcceptance getAcceptance() {
      return NoneAcceptance.INSTANCE;
    }

    @Override
    public ValuationSetFactory getFactory() {
      return factory;
    }

    @Override
    public Map<S, ValuationSet> getIncompleteStates() {
      return Collections.emptyMap();
    }

    @Override
    public Set<S> getInitialStates() {
      return Collections.emptySet();
    }

    @Override
    public Collection<LabelledEdge<S>> getLabelledEdges(S state) {
      return Collections.emptySet();
    }

    @Override
    public Set<S> getReachableStates(Collection<? extends S> start) {
      return Collections.emptySet();
    }

    @Override
    public Set<S> getStates() {
      return Collections.emptySet();
    }
  }

  private static final class FilteredAutomaton<S, A extends OmegaAcceptance>
    extends ForwardingAutomaton<S, A, A, Automaton<S, A>> {

    private final ImmutableSet<S> states;
    private final Predicate<LabelledEdge<S>> edgeFilter;

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

  abstract static class ForwardingAutomaton<S, A extends OmegaAcceptance,
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
    public Map<S, ValuationSet> getIncompleteStates() {
      return automaton.getIncompleteStates();
    }

    @Override
    public Set<S> getStates() {
      return automaton.getStates();
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
    public Map<Set<S>, ValuationSet> getIncompleteStates() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Set<Set<S>> getInitialStates() {
      return Collections.singleton(automaton.getInitialStates());
    }

    @Override
    public Collection<LabelledEdge<Set<S>>> getLabelledEdges(Set<S> state) {
      List<LabelledEdge<Set<S>>> edges = new ArrayList<>();

      for (BitSet valuation : BitSets.powerSet(getFactory().getSize())) {
        edges.add(new LabelledEdge<>(Edges.create(getSuccessor(state, valuation)),
          getFactory().createValuationSet(valuation)));
      }

      return edges;
    }

    @Override
    public Set<Set<S>> getStates() {
      return getReachableStates(getInitialStates());
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
      return Collections.singleton(getSuccessor(state, valuation));
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

      return Edges.create(successor, acceptance);
    }
  }

  private static final class SingletonAutomaton<S, A extends OmegaAcceptance>
    implements Automaton<S, A> {
    private final A acceptance;
    private final ValuationSetFactory factory;
    private final Collection<LabelledEdge<S>> selfLoopEdges;
    private final ValuationSet selfLoopValuations;
    private final S singletonState;

    SingletonAutomaton(S singletonState, ValuationSetFactory factory,
      Map<IntIterable, ValuationSet> acceptances, A acceptance) {
      this.singletonState = singletonState;
      this.factory = factory;
      this.acceptance = acceptance;

      if (acceptances.isEmpty()) {
        selfLoopValuations = factory.createEmptyValuationSet();
        selfLoopEdges = Collections.emptyList();
      } else {
        this.selfLoopValuations = factory.createEmptyValuationSet();
        ImmutableList.Builder<LabelledEdge<S>> builder = ImmutableList.builder();

        acceptances.forEach((edgeAcceptance, valuations) -> {
          Edge<S> edge = Edges.create(singletonState, edgeAcceptance.iterator());
          builder.add(new LabelledEdge<>(edge, valuations));
          selfLoopValuations.addAll(valuations);
        });

        selfLoopEdges = builder.build();
      }
    }

    @Override
    public void free() {
      selfLoopValuations.free();
      selfLoopEdges.forEach(LabelledEdge::free);
    }

    @Override
    public A getAcceptance() {
      return acceptance;
    }

    @Override
    public ValuationSetFactory getFactory() {
      return factory;
    }

    @Override
    public Map<S, ValuationSet> getIncompleteStates() {
      if (selfLoopValuations.isUniverse()) {
        return Collections.emptyMap();
      }
      return Collections.singletonMap(singletonState, selfLoopValuations.complement());
    }

    @Override
    public Set<S> getInitialStates() {
      return Collections.singleton(singletonState);
    }

    @Override
    public Collection<LabelledEdge<S>> getLabelledEdges(S state) {
      return selfLoopEdges;
    }

    @Override
    public Set<S> getReachableStates(Collection<? extends S> start) {
      return Collections.singleton(singletonState);
    }

    @Override
    public Set<S> getStates() {
      return Collections.singleton(singletonState);
    }
  }
}
