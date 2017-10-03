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
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
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
    return new StreamingAutomaton<>(acceptance, factory,
      ImmutableSet.of(initialState), transitions);
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

  public static <S> Automaton<S, AllAcceptance> universe(S state, ValuationSetFactory factory) {
    return new AllAutomaton<>(state, factory);
  }

  private static final class AllAutomaton<S> implements Automaton<S, AllAcceptance> {
    private final ValuationSetFactory factory;
    private final LabelledEdge<S> loop;
    private final S singletonState;
    private List<String> variables;

    AllAutomaton(S singletonState, ValuationSetFactory factory) {
      this.singletonState = singletonState;
      this.factory = factory;
      loop = new LabelledEdge<>(Edges.create(singletonState), factory.createUniverseValuationSet());
      variables = ImmutableList.of();
    }

    @Override
    public AllAcceptance getAcceptance() {
      return new AllAcceptance();
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
      return Collections.singleton(singletonState);
    }

    @Override
    public Collection<LabelledEdge<S>> getLabelledEdges(S state) {
      return Collections.singleton(loop);
    }

    @Override
    public Set<S> getReachableStates(Collection<? extends S> start) {
      return start.contains(singletonState) ? getStates() : Collections.emptySet();
    }

    @Override
    public Set<S> getStates() {
      return Collections.singleton(singletonState);
    }

    @Override
    public List<String> getVariables() {
      return variables;
    }

    @Override
    public void setVariables(List<String> variables) {
      this.variables = ImmutableList.copyOf(variables);
    }
  }

  private static final class EmptyAutomaton<S> implements Automaton<S, NoneAcceptance> {
    private final ValuationSetFactory factory;
    private List<String> variables;

    EmptyAutomaton(ValuationSetFactory factory) {
      this.factory = factory;
      variables = ImmutableList.of();
    }

    @Override
    public NoneAcceptance getAcceptance() {
      return new NoneAcceptance();
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

    @Override
    public List<String> getVariables() {
      return variables;
    }

    @Override
    public void setVariables(List<String> variables) {
      this.variables = ImmutableList.copyOf(variables);
    }
  }

  private static final class FilteredAutomaton<S, A extends OmegaAcceptance>
    extends ForwardingAutomaton<S, A, A, Automaton<S, A>> {

    private final ImmutableSet<S> states;
    private final Predicate<LabelledEdge<S>> edgeFilter;

    private FilteredAutomaton(Automaton<S, A> automaton, Set<S> states,
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
      checkArgument(getStates().contains(state), "State %s not in automaton", state);
      return Collections2.filter(automaton.getLabelledEdges(state), edgeFilter::test);
    }

    @Override
    public Set<S> getStates() {
      return Sets.intersection(states, automaton.getStates());
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

    @Override
    public List<String> getVariables() {
      return automaton.getVariables();
    }

    @Override
    public void setVariables(List<String> variables) {
      automaton.setVariables(variables);
    }
  }

  private static final class PowerSetAutomaton<S> implements Automaton<Set<S>, NoneAcceptance> {

    private final Automaton<S, NoneAcceptance> automaton;

    private PowerSetAutomaton(Automaton<S, NoneAcceptance> automaton) {
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

    @Override
    public List<String> getVariables() {
      return automaton.getVariables();
    }

    @Override
    public void setVariables(List<String> variables) {
      automaton.setVariables(variables);
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
        Edge<S> edge = automaton.getEdge(next, valuation);

        if (it.hasNext()) {
          edge.acceptanceSetStream().forEach(acceptance::set);
        } else {
          edge.acceptanceSetStream().forEach(x -> acceptance.set(x + 1));
        }

        successor.add(edge.getSuccessor());
      }

      return Edges.create(successor, acceptance);
    }
  }
}
