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
import owl.automaton.algorithm.simulations.SimulationStates.MultipebbleSimulationState;
import owl.automaton.edge.Edge;
import owl.collections.Pair;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;
import owl.run.Environment;
import owl.util.BitSetUtil;

public class ForwardFairSimulation<S>
  implements SimulationType<S, SimulationStates.MultipebbleSimulationState<S>> {
  Automaton<S, BuchiAcceptance> leftAutomaton;
  Automaton<S, BuchiAcceptance> rightAutomaton;
  ValuationSetFactory factory;
  S leftState;
  S rightState;
  MultipebbleSimulationState<S> initialState;
  MultipebbleSimulationState<S> sinkState;
  int pebbleCount;
  Set<Pair<S, S>> knownPairs;
  Set<MultipebbleSimulationState<S>> stateSet;

  public ForwardFairSimulation(
    Automaton<S, BuchiAcceptance> leftAutomaton,
    Automaton<S, BuchiAcceptance> rightAutomaton,
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

    this.factory = Environment
      .annotated()
      .factorySupplier()
      .getValuationSetFactory(List.of("a"));

    this.initialState = SimulationStates.MultipebbleSimulationState.of(
      Pebble.of(left, false),
      MultiPebble.of(right, false, pebbleCount)
    );

    this.sinkState = MultipebbleSimulationState.of(
      Pebble.of(left, true),
      MultiPebble.of(List.of(), pebbleCount)
    );

    stateSet = SimulationStates.MultipebbleSimulationState.universe(
      Pebble.universe(leftAutomaton),
      MultiPebble.universe(rightAutomaton, pebbleCount),
      leftAutomaton.factory().universe()
    );
  }

  public static <S> ForwardFairSimulation<S> of(
    Automaton<S, BuchiAcceptance> leftAutomaton,
    Automaton<S, BuchiAcceptance> rightAutomaton,
    S leftState,
    S rightState,
    int pebbleCount,
    Set<Pair<S, S>> known
  ) {
    return new ForwardFairSimulation<>(
      leftAutomaton, rightAutomaton, leftState, rightState, pebbleCount, known
    );
  }

  @Override
  public Map<Edge<MultipebbleSimulationState<S>>, ValuationSet>
  edgeMap(SimulationStates.MultipebbleSimulationState<S> state) {
    Map<Edge<MultipebbleSimulationState<S>>, ValuationSet> out = new HashMap<>();

    if (state.equals(sinkState)) {
      out.put(Edge.of(sinkState, 1), factory.universe());
      return out;
    }

    if (state.owner().isOdd()) {
      if (1 == state.even().count()
        && knownPairs.contains(Pair.of(state.odd().state(), state.even().onlyState()))) {
        out.put(Edge.of(state, 2), factory.universe());
        return out;
      }

      leftAutomaton.edgeMap(state.odd().state()).forEach((edge, valSet) -> {
        valSet.forEach(val -> {
          boolean isAccepting = leftAutomaton.acceptance().isAcceptingEdge(edge);
          // if Duplicator has an accepting Multipebble, we assign a good parity, otherwise the
          // parity is determined by whether or not Spoiler sees an accepting edge
          int edgeParity = state.even().flag() ? 2 : (isAccepting ? 1 : 0);
          var target = SimulationStates.MultipebbleSimulationState.of(
            Pebble.of(edge.successor(), isAccepting),
            state.even().flag() ? state.even().setFlag(false) : state.even(),
            val
          );
          out.put(Edge.of(target, edgeParity), factory.universe());
        });
      });
    } else {
      var possible = state
        .even()
        .successors(rightAutomaton, BitSetUtil.fromInt(state.valuation()));
      if (possible.isEmpty()) {
        out.put(Edge.of(sinkState, 1), factory.universe());
        return out;
      }
      possible.forEach(p -> {
        var target = SimulationStates.MultipebbleSimulationState.of(
          state.odd(), p
        );
        out.put(Edge.of(target, 0), factory.universe());
      });
    }

    return out;
  }

  @Override
  public Set<MultipebbleSimulationState<S>> states() {
    return stateSet;
  }

  @Override
  public ParityAcceptance acceptance() {
    return new ParityAcceptance(3, ParityAcceptance.Parity.MAX_EVEN);
  }

  @Override
  public Set<SimulationStates.MultipebbleSimulationState<S>> initialStates() {
    return Set.of(initialState);
  }

  @Override
  public ValuationSetFactory factory() {
    return factory;
  }
}
