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

package owl.translations.nba2ldba;

import static java.util.stream.Collectors.toSet;

import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.MutableAutomaton;
import owl.automaton.MutableAutomatonFactory;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.algorithms.SccDecomposition;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.automaton.ldba.MutableAutomatonBuilder;

final class AcceptingComponentBuilder<S>
  implements MutableAutomatonBuilder<S, BreakpointState<S>, BuchiAcceptance> {

  private final List<BreakpointState<S>> initialStates;
  private final int max;
  private final Automaton<S, GeneralizedBuchiAcceptance> nba;
  private final List<Set<S>> sccs;
  @Nullable
  private final Edge<BreakpointState<S>> sinkEdge;

  AcceptingComponentBuilder(Automaton<S, GeneralizedBuchiAcceptance> nba, boolean complete) {
    this.nba = nba;
    initialStates = new ArrayList<>();
    max = Math.max(nba.acceptance().acceptanceSets(), 1);
    sccs = SccDecomposition.computeSccs(nba, false);
    sinkEdge = complete ? Edge.of(BreakpointState.sink()) : null;
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
      return sinkEdge;
    }

    Set<S> scc = optionalScc.get();
    Set<Edge<S>> outEdgesM = ldbaState.mx()
      .stream()
      .flatMap(x -> nba.edges(x, valuation).stream())
      .filter(x -> scc.contains(x.successor()))
      .collect(toSet());

    if (outEdgesM.isEmpty()) {
      return sinkEdge;
    }

    Set<Edge<S>> outEdgesN = ldbaState.nx()
      .stream()
      .flatMap(x -> nba.edges(x, valuation).stream())
      .filter(x -> scc.contains(x.successor()))
      .collect(toSet());

    Set<Edge<S>> intersection = outEdgesM.stream()
      .filter(x -> x.inSet(ldbaState.ix() % max)).collect(toSet());

    outEdgesN.addAll(intersection);

    Set<S> n1;
    int i1;

    if (outEdgesM.equals(outEdgesN)) {
      i1 = (ldbaState.ix() + 1) % max;
      n1 = Edges.successors(Sets.filter(outEdgesM, x -> x.inSet(i1)));
    } else {
      i1 = ldbaState.ix();
      n1 = Edges.successors(outEdgesN);
    }

    BreakpointState<S> successor = BreakpointState.of(i1, Edges.successors(outEdgesM), n1);
    return i1 == 0 && outEdgesM.equals(outEdgesN) ? Edge.of(successor, 0) : Edge.of(successor);
  }
}
