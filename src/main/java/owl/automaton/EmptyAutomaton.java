/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
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

import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.edge.Edge;
import owl.bdd.BddSetFactory;
import owl.bdd.FactorySupplier;
import owl.bdd.MtBdd;

public final class EmptyAutomaton<S, A extends EmersonLeiAcceptance>
  extends AbstractMemoizingAutomaton.EdgeTreeImplementation<S, A> {

  private EmptyAutomaton(
    List<String> atomicPropositions,
    @Nullable BddSetFactory factory,
    A acceptance) {

    super(atomicPropositions,
      factory == null ? FactorySupplier.defaultSupplier().getBddSetFactory() : factory,
      Set.of(),
      acceptance);
  }

  public static <S, A extends EmersonLeiAcceptance> Automaton<S, A> of(
    List<String> atomicPropositions, A acceptance) {

    return new EmptyAutomaton<>(atomicPropositions, null, acceptance);
  }

  public static <S, A extends EmersonLeiAcceptance> Automaton<S, A> of(
    List<String> atomicPropositions, BddSetFactory factory, A acceptance) {

    return new EmptyAutomaton<>(atomicPropositions, factory, acceptance);
  }

  @Override
  protected MtBdd<Edge<S>> edgeTreeImpl(S state) {
    throw new IllegalArgumentException("There are no states in this automaton.");
  }
}
