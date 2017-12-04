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

import com.google.common.collect.ImmutableList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.automaton.edge.LabelledEdge;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;

public final class AutomatonFactory {
  private AutomatonFactory() {}

  public static <S, A extends OmegaAcceptance> Automaton<S, A> createStreamingAutomaton(
    A acceptance, S initialState, ValuationSetFactory factory,
    BiFunction<S, BitSet, Edge<S>> transitions) {
    return new StreamingAutomaton<>(acceptance, factory, Set.of(initialState), transitions);
  }

  public static <S> Automaton<S, NoneAcceptance> empty(ValuationSetFactory factory) {
    return new EmptyAutomaton<>(factory);
  }

  public static <S, A extends OmegaAcceptance> Automaton<S, A> singleton(S state,
    ValuationSetFactory factory, A acceptance) {
    return new SingletonAutomaton<>(state, factory, Map.of(), acceptance);
  }

  public static <S, A extends OmegaAcceptance> Automaton<S, A> singleton(S state,
    ValuationSetFactory factory, A acceptance, Set<Integer> acceptanceSet) {
    return new SingletonAutomaton<>(state, factory, Map.of(acceptanceSet,
      factory.createUniverseValuationSet()), acceptance);
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
    public Set<S> getInitialStates() {
      return Set.of();
    }

    @Override
    public Collection<LabelledEdge<S>> getLabelledEdges(S state) {
      return Set.of();
    }

    @Override
    public Set<S> getStates() {
      return Set.of();
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
      Map<Set<Integer>, ValuationSet> acceptances, A acceptance) {
      this.singletonState = singletonState;
      this.factory = factory;
      this.acceptance = acceptance;
      this.selfLoopValuations = factory.createEmptyValuationSet();
      ImmutableList.Builder<LabelledEdge<S>> builder = ImmutableList.builder();

      acceptances.forEach((edgeAcceptance, valuations) -> {
        Edge<S> edge = Edges.create(singletonState, edgeAcceptance.stream()
          .mapToInt(x -> x).iterator());
        builder.add(LabelledEdge.of(edge, valuations));
        selfLoopValuations.addAll(valuations);
      });

      this.selfLoopEdges = builder.build();
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
    public Set<S> getInitialStates() {
      return Set.of(singletonState);
    }

    @Override
    public Collection<LabelledEdge<S>> getLabelledEdges(S state) {
      return selfLoopEdges;
    }

    @Override
    public Set<S> getStates() {
      return Set.of(singletonState);
    }
  }
}
