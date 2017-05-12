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

package owl.translations.nba2ldba;

import com.google.common.collect.ImmutableSet;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.PrimitiveIterator.OfInt;
import java.util.Set;
import java.util.stream.Collectors;

import owl.automaton.Automaton;
import owl.automaton.AutomatonFactory;
import owl.automaton.AutomatonUtil;
import owl.automaton.ExploreBuilder;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;

public final class AcceptingComponentBuilder<S>
  implements ExploreBuilder<S, BreakpointState<S>, BuchiAcceptance> {

  private final List<BreakpointState<S>> initialStates;
  private final Automaton<S, BuchiAcceptance> nba;
  private final List<Set<Edge<S>>> finEdges;
  private final int max;

  private AcceptingComponentBuilder(Automaton<S, BuchiAcceptance> nba) {
    this.nba = nba;
    initialStates = new ArrayList<>();
    max = nba.getAcceptance().getAcceptanceSets();
    
    this.finEdges = new ArrayList<>(max);
    for (int i = 0; i < max; i++) {
      finEdges.add(new HashSet<>());
    }
    for (S state : nba.getStates()) {
      for (Edge<S> edge : nba.getEdges(state)) {
        OfInt it = edge.acceptanceSetIterator();
        it.forEachRemaining((int x) -> {
          finEdges.get(x).add(edge); });
      }
    }
    assert finEdges.get(0) != null;
  }

  public static <S> AcceptingComponentBuilder<S> create(Automaton<S, BuchiAcceptance> nba) {
    return new AcceptingComponentBuilder<>(nba);
  }

  @Override
  public BreakpointState<S> add(S stateKey) {
    int i1 = max > 1 ? 1 : 0;
    Set<S> m1 = ImmutableSet.of(stateKey);
    Set<S> n1 = ImmutableSet.of();
    BreakpointState<S> state = new BreakpointState<>(i1, m1, n1);
    initialStates.add(state);
    return state;
  }

  @Override
  public MutableAutomaton<BreakpointState<S>, BuchiAcceptance> build() {
    MutableAutomaton<BreakpointState<S>, BuchiAcceptance> automaton =
      AutomatonFactory.create(new BuchiAcceptance(), nba.getFactory());

    AutomatonUtil.exploreDeterministic(automaton, initialStates, this::explore);
    automaton.setInitialStates(initialStates);

    return automaton;
  }

  private Edge<BreakpointState<S>> explore(BreakpointState<S> ldbaState, 
      BitSet valuation) {
    Set<S> m1 = nba.getSuccessors(ldbaState.mx, valuation);
    if (m1.isEmpty()) {
      return null;
    }
    
    Set<Edge<S>> outEdgesM = nba.getEdges(ldbaState.mx, valuation);
    Set<Edge<S>> outEdgesN = nba.getEdges(ldbaState.nx, valuation);
    Set<Edge<S>> intersection =
        outEdgesM.stream().filter(x -> finEdges.get((ldbaState.ix + 1) % max).contains(x))
            .collect(Collectors.toSet());
    outEdgesN.addAll(intersection);
    Set<S> n1;
    int i1 = -1;
    if (outEdgesM.equals(outEdgesN)) {
      i1 = (ldbaState.ix + 1) % max;
      n1 = intersection.stream().map(x -> x.getSuccessor()).collect(Collectors.toSet());
    } else {
      n1 = outEdgesN.stream().map(x -> x.getSuccessor()).collect(Collectors.toSet());
      i1 = ldbaState.ix;
    }
    BreakpointState<S> newState = new BreakpointState<>(i1, m1, n1);
    Edge<BreakpointState<S>> edge;
    if (i1 == 0 && outEdgesM.equals(outEdgesN)) {
      edge = Edges.create(newState, 0);
    } else {
      edge = Edges.create(newState);
    }
    return edge;
  }

}
