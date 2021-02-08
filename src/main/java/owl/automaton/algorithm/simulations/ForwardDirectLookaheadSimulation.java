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

package owl.automaton.algorithm.simulations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import owl.automaton.Automaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.algorithm.simulations.SimulationStates.LookaheadSimulationState;
import owl.automaton.edge.Edge;
import owl.bdd.FactorySupplier;
import owl.bdd.ValuationSetFactory;
import owl.collections.Pair;
import owl.collections.ValuationSet;

public class ForwardDirectLookaheadSimulation<S>
  implements SimulationType<S, LookaheadSimulationState<S>> {
  final Automaton<S, BuchiAcceptance> leftAutomaton;
  final Automaton<S, BuchiAcceptance> rightAutomaton;
  final ValuationSetFactory factory;
  final S leftState;
  final S rightState;
  final LookaheadSimulationState<S> initialState;
  final LookaheadSimulationState<S> sinkState;
  final Set<Pair<S, S>> knownPairs;
  final int maxLookahead;

  public ForwardDirectLookaheadSimulation(
    Automaton<S, BuchiAcceptance> leftAutomaton,
    Automaton<S, BuchiAcceptance> rightAutomaton,
    S left,
    S right,
    int maxLookahead,
    Set<Pair<S, S>> known
  ) {
    this.leftAutomaton = leftAutomaton;
    this.rightAutomaton = rightAutomaton;
    this.leftState = left;
    this.rightState = right;
    this.maxLookahead = maxLookahead;
    this.knownPairs = known;


    this.factory = FactorySupplier.defaultSupplier()
      .getValuationSetFactory(List.of("a"));

    this.initialState = LookaheadSimulationState.of(left, right);
    this.sinkState = LookaheadSimulationState.of(left, right, List.of());
  }

  @Override
  public Map<Edge<SimulationStates.LookaheadSimulationState<S>>, ValuationSet> edgeMap(
    SimulationStates.LookaheadSimulationState<S> state
  ) {
    var out = new HashMap<Edge<LookaheadSimulationState<S>>, ValuationSet>();

    if (state.equals(sinkState)) {
      out.put(Edge.of(sinkState, 1), factory.universe());
      return out;
    }

    if (state.owner().isOdd()) {
      var possible = Transition.universe(state.odd(), leftAutomaton, maxLookahead);
      if (possible.isEmpty()) {
        out.put(Edge.of(state, 0), factory.universe());
        return out;
      }
      possible.forEach(moves -> {
        var target = LookaheadSimulationState.of(state.odd(), state.even(), moves);
        out.put(Edge.of(target, 0), factory.universe());
      });
    } else {
      var possible = Transition.directMatching(state.even(), rightAutomaton, state.moves());
      if (possible.isEmpty()) {
        out.put(Edge.of(sinkState, 1), factory.universe());
        return out;
      }
      possible.forEach(move -> {
        var target = LookaheadSimulationState.of(
          Transition.at(state.moves(), move.size()),
          Transition.end(move)
        );
        out.put(Edge.of(target, 0), factory.universe());
      });
    }

    return out;
  }

  @Override
  public ParityAcceptance acceptance() {
    return new ParityAcceptance(2, ParityAcceptance.Parity.MAX_EVEN);
  }

  @Override
  public Set<SimulationStates.LookaheadSimulationState<S>> initialStates() {
    return Set.of(initialState);
  }

  @Override
  public ValuationSetFactory factory() {
    return factory;
  }

}
