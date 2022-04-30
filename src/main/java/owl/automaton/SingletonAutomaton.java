/*
 * Copyright (C) 2020, 2022  (Salomon Sickert)
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
import java.util.BitSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.edge.Edge;
import owl.bdd.BddSetFactory;
import owl.bdd.FactorySupplier;
import owl.bdd.MtBdd;
import owl.collections.ImmutableBitSet;

public final class SingletonAutomaton<S, A extends EmersonLeiAcceptance>
    extends AbstractMemoizingAutomaton.EdgeTreeImplementation<S, A> {

  private final MtBdd<Edge<S>> selfLoopEdges;

  private SingletonAutomaton(
      List<String> atomicPropositions,
      S singletonState,
      BddSetFactory factory,
      @Nullable ImmutableBitSet acceptanceSets,
      A acceptance) {

    super(atomicPropositions, factory, Set.of(singletonState), acceptance);
    this.selfLoopEdges = acceptanceSets == null
        ? MtBdd.of()
        : MtBdd.of(Edge.of(singletonState, acceptanceSets));
  }

  public static <S, A extends EmersonLeiAcceptance> Automaton<S, A> of(
      List<String> atomicPropositions, S state, A acceptance) {

    return new SingletonAutomaton<>(
        atomicPropositions,
        state,
        FactorySupplier.defaultSupplier().getBddSetFactory(),
        null,
        acceptance);
  }

  public static <S, A extends EmersonLeiAcceptance> Automaton<S, A> of(
      List<String> atomicPropositions, BddSetFactory factory, S state, A acceptance) {

    return new SingletonAutomaton<>(
        atomicPropositions,
        state,
        factory,
        null,
        acceptance);
  }

  public static <S, A extends EmersonLeiAcceptance> Automaton<S, A> of(
      List<String> atomicPropositions,
      S state,
      A acceptance,
      Set<Integer> acceptanceSet) {

    return new SingletonAutomaton<>(
        atomicPropositions,
        state,
        FactorySupplier.defaultSupplier().getBddSetFactory(),
        ImmutableBitSet.copyOf(acceptanceSet),
        acceptance);
  }

  public static <S, A extends EmersonLeiAcceptance> Automaton<S, A> of(
      List<String> atomicPropositions,
      BddSetFactory factory,
      S state,
      A acceptance,
      Set<Integer> acceptanceSet) {

    return new SingletonAutomaton<>(
        atomicPropositions,
        state,
        factory,
        ImmutableBitSet.copyOf(acceptanceSet),
        acceptance);
  }

  public static <S, A extends EmersonLeiAcceptance> Automaton<S, A> of(
      List<String> atomicPropositions,
      S state,
      A acceptance,
      BitSet acceptanceSet) {

    return new SingletonAutomaton<>(
        atomicPropositions,
        state,
        FactorySupplier.defaultSupplier().getBddSetFactory(),
        ImmutableBitSet.copyOf(acceptanceSet),
        acceptance);
  }

  public static <S, A extends EmersonLeiAcceptance> Automaton<S, A> of(
      List<String> atomicPropositions,
      BddSetFactory factory,
      S state,
      A acceptance,
      BitSet acceptanceSet) {

    return new SingletonAutomaton<>(
        atomicPropositions,
        state,
        factory,
        ImmutableBitSet.copyOf(acceptanceSet),
        acceptance);
  }

  @Override
  protected MtBdd<Edge<S>> edgeTreeImpl(S state) {
    Preconditions.checkArgument(initialStates.contains(state),
        "This state is not in the automaton");
    return selfLoopEdges;
  }
}
