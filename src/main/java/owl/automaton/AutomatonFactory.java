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

import de.tum.in.naturals.bitset.BitSets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.LabelledEdge;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;

public final class AutomatonFactory {
  private AutomatonFactory() {}

  /**
   * Creates a deterministic on-the-fly constructed automaton.
   *
   * @param <S> The type of the state.
   * @param <A> The type of the acceptance conditions.
   * @param initialState The initial state.
   * @param factory The alphabet.
   * @param transitions The transition function.
   * @param acceptance The acceptance condition.
   * @return
   */
  public static <S, A extends OmegaAcceptance> Automaton<S, A> create(S initialState,
    ValuationSetFactory factory, BiFunction<S, BitSet, Edge<S>> transitions, A acceptance) {
    return new OnTheFlyAutomaton.Simple<>(initialState, factory, transitions, acceptance);
  }

  /**
   * Creates a non-deterministic on-the-fly constructed automaton with support for bulk creation of
   * edges.
   *
   * @param <S> The type of the state.
   * @param <A> The type of the acceptance conditions.
   * @param initialState The initial state.
   * @param factory The alphabet.
   * @param transitions The transition function.
   * @param bulkTransitions
   *    A bulk transition function, needs to be consistent with transitions.
   * @param acceptance The acceptance condition.
   * @return
   */
  public static <S, A extends OmegaAcceptance> Automaton<S, A> create(S initialState,
    ValuationSetFactory factory, BiFunction<S, BitSet, Set<Edge<S>>> transitions,
    Function<S, Collection<LabelledEdge<S>>> bulkTransitions, A acceptance) {
    return new OnTheFlyAutomaton.Bulk<>(initialState, factory, transitions, bulkTransitions,
      acceptance);
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
      factory.universe()), acceptance);
  }

  private static final class EmptyAutomaton<S>
    implements Automaton<S, NoneAcceptance>, BulkOperationAutomaton {
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
    implements Automaton<S, A>, BulkOperationAutomaton {
    private final A acceptance;
    private final ValuationSetFactory factory;
    private final Collection<LabelledEdge<S>> selfLoopEdges;
    private final S singletonState;

    SingletonAutomaton(S singletonState, ValuationSetFactory factory,
      Map<Set<Integer>, ValuationSet> acceptances, A acceptance) {
      this.singletonState = singletonState;
      this.factory = factory;
      this.acceptance = acceptance;

      List<LabelledEdge<S>> builder = new ArrayList<>();
      acceptances.forEach((edgeAcceptance, valuations) -> {
        Edge<S> edge = Edge.of(singletonState, BitSets.of(edgeAcceptance));
        builder.add(LabelledEdge.of(edge, valuations));
      });
      this.selfLoopEdges = List.copyOf(builder);
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
