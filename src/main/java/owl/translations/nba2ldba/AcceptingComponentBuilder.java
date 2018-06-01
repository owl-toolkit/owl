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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.MutableAutomaton;
import owl.automaton.MutableAutomatonFactory;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.algorithms.SccDecomposition;
import owl.automaton.edge.Edge;
import owl.automaton.ldba.MutableAutomatonBuilder;

final class AcceptingComponentBuilder<S>
  implements MutableAutomatonBuilder<S, BreakpointState<S>, BuchiAcceptance> {

  private final List<Set<Edge<S>>> finEdges;
  private final List<BreakpointState<S>> initialStates;
  private final int max;
  private final Automaton<S, GeneralizedBuchiAcceptance> nba;
  private final List<Set<S>> sccs;

  AcceptingComponentBuilder(Automaton<S, GeneralizedBuchiAcceptance> nba) {
    this.nba = nba;
    initialStates = new ArrayList<>();
    max = Math.max(nba.acceptance().acceptanceSets(), 1);

    this.finEdges = new ArrayList<>(max);
    for (int i = 0; i < max; i++) {
      finEdges.add(new HashSet<>());
    }

    nba.states().forEach(state -> nba.forEachEdge(state, edge ->
      edge.acceptanceSetIterator().forEachRemaining((int x) -> finEdges.get(x).add(edge))));

    assert finEdges.get(0) != null;
    sccs = SccDecomposition.computeSccs(nba, false);
  }

  @Override
  public BreakpointState<S> add(S stateKey) {
    BreakpointState<S> state = BreakpointState.of(0, Set.of(stateKey), Set.of());
    initialStates.add(state);
    return state;
  }

  @Override
  public MutableAutomaton<BreakpointState<S>, BuchiAcceptance> build() {
    return MutableAutomatonFactory.create(BuchiAcceptance.INSTANCE,
      nba.factory(), initialStates, this::explore, (x) -> null);
  }

  @Nullable
  private Edge<BreakpointState<S>> explore(BreakpointState<S> ldbaState, BitSet valuation) {
    Optional<Set<S>> optionalScc = sccs.stream().filter(x -> x.containsAll(ldbaState.mx()))
      .findAny();

    if (!optionalScc.isPresent()) {
      return null;
    }

    Set<S> scc = optionalScc.get();
    Set<Edge<S>> outEdgesM = ldbaState.mx().stream().flatMap(x -> nba.edges(x, valuation)
      .stream()).filter(x -> scc.contains(x.successor()))
      .collect(Collectors.toSet());

    if (outEdgesM.isEmpty()) {
      return null;
    }

    Set<Edge<S>> outEdgesN = ldbaState.nx().stream().flatMap(x -> nba.edges(x, valuation)
      .stream()).filter(x -> scc.contains(x.successor()))
      .collect(Collectors.toSet());

    Set<Edge<S>> intersection = outEdgesM.stream()
      .filter(x -> finEdges.get(ldbaState.ix() % max).contains(x)).collect(Collectors.toSet());

    outEdgesN.addAll(intersection);

    Set<S> n1;
    int i1;

    if (outEdgesM.equals(outEdgesN)) {
      i1 = (ldbaState.ix() + 1) % max;
      Set<Edge<S>> intersection2 = outEdgesM.stream()
        .filter(x -> finEdges.get(i1).contains(x)).collect(Collectors.toSet());
      n1 = intersection2.stream().map(Edge::successor).collect(Collectors.toSet());
    } else {
      n1 = outEdgesN.stream().map(Edge::successor).collect(Collectors.toSet());
      i1 = ldbaState.ix();
    }

    BreakpointState<S> successor = BreakpointState.of(i1, outEdgesM.stream().map(
      Edge::successor).collect(Collectors.toSet()), n1);

    return i1 == 0 && outEdgesM.equals(outEdgesN) ? Edge.of(successor, 0) : Edge.of(successor);
  }
}
