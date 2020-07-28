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

import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import owl.automaton.Automaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.algorithm.simulations.SimulationStates.MultipebbleSimulationState;
import owl.automaton.edge.Edge;
import owl.collections.BitSet2;
import owl.collections.Pair;

public class ForwardFairSimulation<S>
  implements SimulationType<S, SimulationStates.MultipebbleSimulationState<S>> {

  final Automaton<S, ? extends BuchiAcceptance> leftAutomaton;
  final Automaton<S, ? extends BuchiAcceptance> rightAutomaton;
  final S leftState;
  final S rightState;
  final MultipebbleSimulationState<S> initialState;
  final MultipebbleSimulationState<S> sinkState;
  final int pebbleCount;
  final Set<Pair<S, S>> knownPairs;

  public ForwardFairSimulation(
    Automaton<S, ? extends BuchiAcceptance> leftAutomaton,
    Automaton<S, ? extends BuchiAcceptance> rightAutomaton,
    S left,
    S right,
    int pebbleCount,
    Set<Pair<S, S>> known
  ) {
    this.leftAutomaton = leftAutomaton;
    this.rightAutomaton = rightAutomaton;
    this.leftState = left;
    this.rightState = right;
    this.pebbleCount = pebbleCount;
    this.knownPairs = known;

    this.initialState = SimulationStates.MultipebbleSimulationState.of(
      Pebble.of(left, false),
      MultiPebble.of(right, false, pebbleCount)
    );

    this.sinkState = MultipebbleSimulationState.of(
      Pebble.of(left, true),
      MultiPebble.of(List.of(), pebbleCount)
    );
  }

  @Override
  public Set<Edge<MultipebbleSimulationState<S>>>
    edges(SimulationStates.MultipebbleSimulationState<S> state) {

    Set<Edge<MultipebbleSimulationState<S>>> out = new HashSet<>();

    if (state.equals(sinkState)) {
      return Set.of(Edge.of(sinkState, 1));
    }

    if (state.owner().isOdd()) {
      if (1 == state.even().count()
        && knownPairs.contains(Pair.of(state.odd().state(), state.even().onlyState()))) {

        return Set.of(Edge.of(state, 2));
      }

      leftAutomaton.edgeMap(state.odd().state()).forEach((edge, valSet) -> {
        // if Duplicator has an accepting Multipebble, we assign a good parity, otherwise the
        // parity is determined by whether or not Spoiler sees an accepting edge
        valSet.iterator(leftAutomaton.atomicPropositions().size()).forEachRemaining(
          (Consumer<? super BitSet>) val -> {
          boolean isAccepting = leftAutomaton.acceptance().isAcceptingEdge(edge);
          // if Duplicator has an accepting Multipebble, we assign a good parity, otherwise the
          // parity is determined by whether or not Spoiler sees an accepting edge
          int edgeParity = state.even().flag() ? 2 : (isAccepting ? 1 : 0);
          var target = MultipebbleSimulationState.of(
              Pebble.of(edge.successor(), isAccepting),
              state.even().flag() ? state.even().setFlag(false) : state.even(),
              val
          );
          out.add(Edge.of(target, edgeParity));
        });
      });
    } else {
      var possible = state
        .even()
        .successors(rightAutomaton, BitSet2.fromInt(state.valuation()));
      if (possible.isEmpty()) {
        return Set.of(Edge.of(sinkState, 1));
      }
      possible.forEach(p -> {
        var target = SimulationStates.MultipebbleSimulationState.of(
          state.odd(), p
        );
        out.add(Edge.of(target, 0));
      });
    }

    return out;
  }

  @Override
  public ParityAcceptance acceptance() {
    return new ParityAcceptance(3, ParityAcceptance.Parity.MAX_EVEN);
  }

  @Override
  public Set<SimulationStates.MultipebbleSimulationState<S>> initialStates() {
    return Set.of(initialState);
  }
}
