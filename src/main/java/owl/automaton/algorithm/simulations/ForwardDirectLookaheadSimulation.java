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

package owl.automaton.algorithm.simulations;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import owl.automaton.Automaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.algorithm.simulations.SimulationStates.LookaheadSimulationState;
import owl.automaton.edge.Edge;
import owl.collections.Pair;

public class ForwardDirectLookaheadSimulation<S>
  implements SimulationType<S, LookaheadSimulationState<S>> {
  final Automaton<S, ? extends BuchiAcceptance> leftAutomaton;
  final Automaton<S, ? extends BuchiAcceptance> rightAutomaton;
  final S leftState;
  final S rightState;
  final LookaheadSimulationState<S> initialState;
  final LookaheadSimulationState<S> sinkState;
  final Set<Pair<S, S>> knownPairs;
  final int maxLookahead;

  public ForwardDirectLookaheadSimulation(
    Automaton<S, ? extends BuchiAcceptance> leftAutomaton,
    Automaton<S, ? extends BuchiAcceptance> rightAutomaton,
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

    this.initialState = LookaheadSimulationState.of(left, right);
    this.sinkState = LookaheadSimulationState.of(left, right, List.of());
  }

  @Override
  public Set<Edge<SimulationStates.LookaheadSimulationState<S>>> edges(
    SimulationStates.LookaheadSimulationState<S> state) {

    if (state.equals(sinkState)) {
      return Set.of(Edge.of(sinkState, 1));
    }

    var out = new HashSet<Edge<LookaheadSimulationState<S>>>();

    if (state.owner().isOdd()) {
      var possible = Transition.universe(state.odd(), leftAutomaton, maxLookahead);
      if (possible.isEmpty()) {
        return Set.of(Edge.of(state, 0));
      }
      possible.forEach(moves -> {
        var target = LookaheadSimulationState.of(state.odd(), state.even(), moves);
        out.add(Edge.of(target, 0));
      });
    } else {
      var possible = Transition.directMatching(state.even(), rightAutomaton, state.moves());
      if (possible.isEmpty()) {
        return Set.of(Edge.of(sinkState, 1));
      }
      possible.forEach(move -> {
        var target = LookaheadSimulationState.of(
          Transition.at(state.moves(), move.size()),
          Transition.end(move)
        );
        out.add(Edge.of(target, 0));
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
}
