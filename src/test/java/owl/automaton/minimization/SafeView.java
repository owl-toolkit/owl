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

package owl.automaton.minimization;

import java.util.Set;
import javax.annotation.Nullable;
import owl.automaton.AbstractMemoizingAutomaton;
import owl.automaton.Automaton;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.edge.Edge;
import owl.bdd.MtBdd;

class SafeView<S> extends AbstractMemoizingAutomaton.EdgeTreeImplementation<S, AllAcceptance> {

  @Nullable
  private Automaton<S, CoBuchiAcceptance> ncw;

  SafeView(Automaton<S, CoBuchiAcceptance> ncw) {
    super(ncw.atomicPropositions(), ncw.factory(), ncw.initialStates(), AllAcceptance.INSTANCE);
    this.ncw = ncw;
  }

  @Override
  protected MtBdd<Edge<S>> edgeTreeImpl(S state) {
    assert ncw != null;
    return ncw.edgeTree(state).map(edges -> {
      switch (edges.size()) {
        case 0:
          return Set.of();

        case 1:
          return edges.iterator().next().colours().isEmpty() ? edges : Set.of();

        default:
          assert edges.stream().allMatch(edge -> edge.colours().contains(0));
          return Set.of();
      }
    });
  }

  @Override
  protected void explorationCompleted() {
    assert ncw != null;
    ncw = null;
  }
}
