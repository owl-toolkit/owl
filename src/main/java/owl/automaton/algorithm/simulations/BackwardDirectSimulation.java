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
import owl.collections.BitSet2;
import owl.collections.Pair;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;
import owl.run.Environment;

public class BackwardDirectSimulation<S>
  implements SimulationType<S, MultipebbleSimulationState<S>> {

  final Automaton<S, BuchiAcceptance> leftAutomaton;
  final Automaton<S, BuchiAcceptance> rightAutomaton;
  final ValuationSetFactory factory;
  final S leftState;
  final S rightState;
  final MultipebbleSimulationState<S> initialState;
  final MultipebbleSimulationState<S> sinkState;
  final int pebbleCount;
  final Set<Pair<S, S>> knownPairs;

  public BackwardDirectSimulation(
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

    this.initialState = MultipebbleSimulationState.of(
      Pebble.of(left, false),
      MultiPebble.of(right, false, pebbleCount)
    );

    this.sinkState = MultipebbleSimulationState.of(
      Pebble.of(left, true),
      MultiPebble.of(List.of(), pebbleCount)
    );
  }

  @Override
  public Map<Edge<MultipebbleSimulationState<S>>, ValuationSet> edgeMap(
    MultipebbleSimulationState<S> state
  ) {
    Map<Edge<MultipebbleSimulationState<S>>, ValuationSet> out = new HashMap<>();

    if (state.equals(sinkState)) {
      out.put(Edge.of(sinkState, 1), factory.universe());
      return out;
    }

    if (state.owner().isOdd()) {
      if (state.even().isSingleton()
        && knownPairs.contains(Pair.of(state.odd().state(), state.even().onlyState()))) {
        out.put(Edge.of(state, 0), factory.universe());
        return out;
      }

      // check if Duplicator has a pebble on an initial state
      boolean containsInitial = state.even().pebbles()
        .stream().anyMatch(p -> rightAutomaton.initialStates().contains(p.state()));

      if ((!containsInitial && leftAutomaton.initialStates().contains(state.odd().state()))
        || (!state.even().flag() && state.odd().flag())) {
        // Spoiler reached an initial state while Duplicator did not, go to sink
        out.put(Edge.of(sinkState, 1), factory.universe());
        return out;
      }

      // we obtain the entrySet to be able to exit prematurely if no predecessors are available
      var predecessors = leftAutomaton.predecessors(state.odd().state());
      if (predecessors.isEmpty()) {
        out.put(Edge.of(state, 0), factory.universe());
        return out;
      }

      predecessors.forEach(pred -> leftAutomaton.edgeMap(pred).forEach((e, vS) -> {
        if (e.successor().equals(state.odd().state())) {
          vS.forEach(val -> state.odd().predecessors(leftAutomaton, val).forEach(p -> {
            var target = MultipebbleSimulationState.of(
              p, state.even().setFlag(false), val
            );
            out.put(Edge.of(target, 0), factory.universe());
          }));
        }
      }));
    } else {
      var possibilities = state.even()
        .predecessors(leftAutomaton, BitSet2.fromInt(state.valuation()));
      if (possibilities.isEmpty()) {
        out.put(Edge.of(sinkState, 1), factory.universe());
        return out;
      }

      possibilities.forEach(p -> {
        if (!state.odd().flag() || p.flag()) {
          var target = MultipebbleSimulationState.of(
            state.odd(), p
          );
          out.put(Edge.of(target, 0), factory.universe());
        } else {
          out.put(Edge.of(sinkState, 1), factory.universe());
        }
      });
    }

    return out;
  }

  @Override
  public ParityAcceptance acceptance() {
    return new ParityAcceptance(2, ParityAcceptance.Parity.MAX_EVEN);
  }

  @Override
  public Set<MultipebbleSimulationState<S>> initialStates() {
    return Set.of(initialState);
  }

  @Override
  public ValuationSetFactory factory() {
    return factory;
  }
}
