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
import owl.bdd.BddSet;
import owl.bdd.BddSetFactory;
import owl.bdd.FactorySupplier;
import owl.collections.BitSet2;
import owl.collections.Pair;

public class ForwardDelayedSimulation<S>
  implements SimulationType<S, MultipebbleSimulationState<S>> {

  final Automaton<S, BuchiAcceptance> leftAutomaton;
  final Automaton<S, BuchiAcceptance> rightAutomaton;
  final BddSetFactory factory;
  final S leftState;
  final S rightState;
  final MultipebbleSimulationState<S> initialState;
  final MultipebbleSimulationState<S> sinkState;
  final int pebbleCount;
  final Set<Pair<S, S>> knownPairs;

  public ForwardDelayedSimulation(
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

    this.factory = FactorySupplier.defaultSupplier()
      .getBddSetFactory(List.of("a"));

    // the initial state is a Spoiler state with one pebble for each player on the given states
    this.initialState = MultipebbleSimulationState.of(
      Pebble.of(left, false),
      MultiPebble.of(right, false, pebbleCount)
    );

    // the sink state is needed so the game is complete, we just use the given leftState for
    // Spoiler and the empty Multipebble for Duplicator.
    this.sinkState = MultipebbleSimulationState.of(
      Pebble.of(left, true),
      MultiPebble.of(List.of(), pebbleCount)
    );
  }

  @Override
  public Map<Edge<MultipebbleSimulationState<S>>, BddSet>
  edgeMap(MultipebbleSimulationState<S> state
  ) {
    Map<Edge<MultipebbleSimulationState<S>>, BddSet> out = new HashMap<>();

    if (state.equals(sinkState)) {
      out.put(Edge.of(sinkState, 1), factory.universe());
      return out;
    }

    if (state.owner().isOdd()) {
      // the parity of an edge is determined by the flag
      var edgeParity = state.odd().flag() ? 1 : 2;

      // if even has only one singular state
      if (1 == state.even().count()
        // and the states that both players have their pebble on
        && knownPairs.contains(Pair.of(state.odd().state(), state.even().onlyState()))
        // and either we do not need to see a final state or just saw one
        && (!state.odd().flag() || state.even().flag())) {
        // just put a loop to this state with even parity to make it accepting
        out.put(Edge.of(state, 2), factory.universe());
        return out;
      }

      leftAutomaton.edgeMap(state.odd().state()).forEach((edge, valSet) -> {
        valSet.toSet().forEach(val -> {
          var isAccepting = leftAutomaton.acceptance().isAcceptingEdge(edge);
          var target = MultipebbleSimulationState.of(
            Pebble.of(edge.successor(), state.odd().flag() || isAccepting),
            isAccepting ? state.even().setFlag(false) : state.even(),
            val
          );
          out.put(Edge.of(target, edgeParity), factory.universe());
        });
      });
    } else {
      var possible = state
        .even()
        .successors(rightAutomaton, BitSet2.fromInt(state.valuation()));

      // if no successor is possible for Duplicator, we go to the sink
      if (possible.isEmpty()) {
        out.put(Edge.of(sinkState, 1), factory.universe());
        return out;
      }

      possible.forEach(p -> {
        MultipebbleSimulationState<S> target;
        // if all pebbles have seen a final state, we reset Spoilers flag, otherwise we just
        // keep it as is
        target = MultipebbleSimulationState.of(
          p.flag() ? state.odd().withFlag(false) : state.odd(), p
        );
        out.put(Edge.of(target, 0), factory.universe());
      });
    }

    return out;
  }

  @Override
  public ParityAcceptance acceptance() {
    return new ParityAcceptance(3, ParityAcceptance.Parity.MAX_EVEN);
  }

  @Override
  public Set<MultipebbleSimulationState<S>> initialStates() {
    return Set.of(initialState);
  }

  @Override
  public BddSetFactory factory() {
    return factory;
  }
}
