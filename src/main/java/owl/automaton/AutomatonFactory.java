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

import com.google.common.base.Preconditions;
import de.tum.in.naturals.bitset.BitSets;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nullable;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.LabelledEdge;
import owl.factories.ValuationSetFactory;

public final class AutomatonFactory {
  private AutomatonFactory() {}

  /**
   * Creates a deterministic on-the-fly constructed automaton.
   *
   * @param <S> The type of the state.
   * @param <A> The type of the acceptance conditions.
   * @param factory The alphabet.
   * @param initialState The initial state.
   * @param acceptance The acceptance condition.
   * @param transitions The transition function.
   */
  public static <S, A extends OmegaAcceptance> Automaton<S, A> create(ValuationSetFactory factory,
    S initialState, A acceptance, BiFunction<S, BitSet, Edge<S>> transitions) {
    return create(factory, Set.of(initialState), acceptance, transitions);
  }

  /**
   * Creates a semi-deterministic on-the-fly constructed automaton.
   *
   * @param <S> The type of the state.
   * @param <A> The type of the acceptance conditions.
   * @param factory The alphabet.
   * @param initialStates The initial state.
   * @param acceptance The acceptance condition.
   * @param transitions The transition function.
   */
  public static <S, A extends OmegaAcceptance> Automaton<S, A> create(ValuationSetFactory factory,
    Collection<S> initialStates, A acceptance, BiFunction<S, BitSet, Edge<S>> transitions) {
    return new ImplicitSemiDeterministicEdgesAutomaton<>(factory, initialStates, acceptance,
      transitions);
  }

  /**
   * Creates a non-deterministic on-the-fly constructed automaton with supporting only bulk creation
   * of edges.
   *
   * @param <S> The type of the state.
   * @param <A> The type of the acceptance conditions.
   * @param factory The alphabet.
   * @param initialState The initial state.
   * @param acceptance The acceptance condition.
   * @param labelledEdgesFunction
   *     A bulk transition function, needs to be consistent with {@code transitions}.
   */
  public static <S, A extends OmegaAcceptance> Automaton<S, A> create(ValuationSetFactory factory,
    S initialState, A acceptance,
    Function<S, ? extends Collection<LabelledEdge<S>>> labelledEdgesFunction) {
    return create(factory, Set.of(initialState), acceptance, labelledEdgesFunction);
  }

  /**
   * Creates a non-deterministic on-the-fly constructed automaton with support for bulk creation of
   * edges.
   *
   * @param <S> The type of the state.
   * @param <A> The type of the acceptance conditions.
   * @param factory The alphabet.
   * @param initialState The initial state.
   * @param acceptance The acceptance condition.
   * @param edgesFunction The transition function.
   * @param labelledEdgesFunction
*     A bulk transition function, needs to be consistent with {@code transitions}.
   */
  public static <S, A extends OmegaAcceptance> Automaton<S, A> create(ValuationSetFactory factory,
    S initialState, A acceptance,
    BiFunction<S, BitSet, ? extends Collection<Edge<S>>> edgesFunction,
    Function<S, ? extends Collection<LabelledEdge<S>>> labelledEdgesFunction) {
    return create(factory, Set.of(initialState), acceptance, edgesFunction, labelledEdgesFunction);
  }

  /**
   * Creates a non-deterministic on-the-fly constructed automaton with supporting only bulk creation
   * of edges.
   *
   * @param <S> The type of the state.
   * @param <A> The type of the acceptance conditions.
   * @param factory The alphabet.
   * @param initialStates The initial states.
   * @param acceptance The acceptance condition.
   * @param labelledEdgesFunction
   *     A bulk transition function, needs to be consistent with {@code transitions}.
   */
  public static <S, A extends OmegaAcceptance> Automaton<S, A> create(ValuationSetFactory factory,
    Collection<S> initialStates, A acceptance,
    Function<S, ? extends Collection<LabelledEdge<S>>> labelledEdgesFunction) {
    return new ImplicitNonDeterministicLabelledEdgesAutomaton<>(factory, initialStates, acceptance,
      null, labelledEdgesFunction);
  }

  /**
   * Creates a non-deterministic on-the-fly constructed automaton with support for bulk creation of
   * edges.
   *
   * @param <S> The type of the state.
   * @param <A> The type of the acceptance conditions.
   * @param factory The alphabet.
   * @param initialStates The initial states.
   * @param acceptance The acceptance condition.
   * @param edgesFunction The transition function.
   * @param labelledEdgesFunction
   *     A bulk transition function, needs to be consistent with {@code transitions}.
   */
  public static <S, A extends OmegaAcceptance> Automaton<S, A> create(ValuationSetFactory factory,
    Collection<S> initialStates, A acceptance,
    BiFunction<S, BitSet, ? extends Collection<Edge<S>>> edgesFunction,
    Function<S, ? extends Collection<LabelledEdge<S>>> labelledEdgesFunction) {
    return new ImplicitNonDeterministicLabelledEdgesAutomaton<>(factory, initialStates, acceptance,
      Objects.requireNonNull(edgesFunction), labelledEdgesFunction);
  }

  public static <S> Automaton<S, NoneAcceptance> empty(ValuationSetFactory factory) {
    return new EmptyAutomaton<>(factory);
  }

  public static <S, A extends OmegaAcceptance> Automaton<S, A> singleton(
    ValuationSetFactory factory, S state, A acceptance) {
    return new SingletonAutomaton<>(state, factory, null, acceptance);
  }

  public static <S, A extends OmegaAcceptance> Automaton<S, A> singleton(
    ValuationSetFactory factory, S state, A acceptance, Set<Integer> acceptanceSet) {
    return new SingletonAutomaton<>(state, factory, BitSets.of(acceptanceSet), acceptance);
  }

  public static <S, A extends OmegaAcceptance> Automaton<S, A> singleton(
    ValuationSetFactory factory, S state, A acceptance, BitSet acceptanceSet) {
    return new SingletonAutomaton<>(state, factory, acceptanceSet, acceptance);
  }

  private static final class EmptyAutomaton<S>
    extends ImplicitCachedStatesAutomaton<S, NoneAcceptance>
    implements LabelledEdgesAutomatonMixin<S, NoneAcceptance> {

    private EmptyAutomaton(ValuationSetFactory factory) {
      super(factory, Set.of(), NoneAcceptance.INSTANCE);
    }

    @Override
    public List<LabelledEdge<S>> labelledEdges(S state) {
      throw new IllegalArgumentException("There are no states in this automaton.");
    }
  }

  private static final class SingletonAutomaton<S, A extends OmegaAcceptance>
    extends ImplicitCachedStatesAutomaton<S, A>
    implements LabelledEdgesAutomatonMixin<S, A> {

    private final List<LabelledEdge<S>> selfLoopEdges;

    private SingletonAutomaton(S singletonState, ValuationSetFactory factory,
      @Nullable BitSet acceptanceSets, A acceptance) {
      super(factory, Set.of(singletonState), acceptance);
      this.selfLoopEdges = acceptanceSets == null
        ? List.of()
        : List.of(LabelledEdge.of(singletonState, acceptanceSets, factory.universe()));
    }

    @Override
    public List<LabelledEdge<S>> labelledEdges(S state) {
      Preconditions.checkArgument(initialStates.contains(state),
        "This state is not in the automaton");
      return selfLoopEdges;
    }
  }
}
