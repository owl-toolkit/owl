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

public class ForwardDelayedSimulation<S>
  implements SimulationType<S, MultipebbleSimulationState<S>> {
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

    this.factory = Environment
      .annotated()
      .factorySupplier()
      .getValuationSetFactory(List.of("a"));

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

    // create the state set consisting of all possible pebble locations and flags for Spoiler,
    // all possible multipebbles up to and including size pebbleCount and all valuations that
    // can be chosen by Spoiler.
    this.stateSet = MultipebbleSimulationState.universe(
      Pebble.universe(leftAutomaton),
      MultiPebble.universe(rightAutomaton, pebbleCount),
      leftAutomaton.factory().universe()
    );
  }

  public static <S> ForwardDelayedSimulation<S> of(
    Automaton<S, BuchiAcceptance> leftAutomaton,
    Automaton<S, BuchiAcceptance> rightAutomaton,
    S leftState,
    S rightState,
    int pebbleCount,
    Set<Pair<S, S>> known
  ) {
    return new ForwardDelayedSimulation<>(
      leftAutomaton, rightAutomaton, leftState, rightState, pebbleCount, known
    );
  }

  @Override
  public Map<Edge<MultipebbleSimulationState<S>>, ValuationSet>
  edgeMap(MultipebbleSimulationState<S> state
  ) {
    Map<Edge<MultipebbleSimulationState<S>>, ValuationSet> out = new HashMap<>();

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
        valSet.forEach(val -> {
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
        .successors(rightAutomaton, BitSetUtil.fromInt(state.valuation()));

      // if no successor is possible for Duplicator, we go to the sink
      if (possible.size() == 0) {
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
  public Set<MultipebbleSimulationState<S>> states() {
    return stateSet;
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
  public ValuationSetFactory factory() {
    return factory;
  }
}
