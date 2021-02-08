/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
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

import java.util.Map;
import java.util.Set;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.bdd.ValuationSetFactory;
import owl.collections.ValuationSet;

public final class EmptyAutomaton<S, A extends OmegaAcceptance>
  extends AbstractImmutableAutomaton<S, A>
  implements EdgeMapAutomatonMixin<S, A> {

  private EmptyAutomaton(ValuationSetFactory factory, A acceptance) {
    super(factory, Set.of(), acceptance);
  }

  public static <S, A extends OmegaAcceptance> Automaton<S, A> of(
    ValuationSetFactory factory, A acceptance) {
    return new EmptyAutomaton<>(factory, acceptance);
  }

  @Override
  public Map<Edge<S>, ValuationSet> edgeMap(S state) {
    throw new IllegalArgumentException("There are no states in this automaton.");
  }
}
