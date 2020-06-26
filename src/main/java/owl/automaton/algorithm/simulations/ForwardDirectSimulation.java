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
import java.util.HashSet;
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

/**
 * Simulation type for forward-direct multipebble simulation games.
 *
 * @param <S> The type of state of the underlying automaton.
 */
public class ForwardDirectSimulation<S>
  implements SimulationType<S, SimulationStates.MultipebbleSimulationState<S>> {
  Automaton<S, BuchiAcceptance> leftAutomaton;
  Automaton<S, BuchiAcceptance> rightAutomaton;
  ValuationSetFactory factory;
  S leftState;
  S rightState;
  MultipebbleSimulationState<S> initialState;
  SimulationStates.MultipebbleSimulationState<S> sinkState;
  int pebbleCount;
  Set<Pair<S, S>> knownPairs;

  /**
   * Constructs a simulation game for two given automata and two states.
   *
   * @param leftAutomaton First input automaton.
   * @param rightAutomaton Second input automaton.
   * @param left First input state.
   * @param right Second input state.
   * @param pebbleCount The number of pebbles Duplicator can control.
   * @param known The set of state-state pairs already known to be similar.
   */
  public ForwardDirectSimulation(
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
      MultiPebble.of(List.of(Pebble.of(right, false)), pebbleCount)
    );

    this.sinkState = SimulationStates.MultipebbleSimulationState.of(
      Pebble.of(left, true),
      MultiPebble.of(List.of(), pebbleCount)
    );
  }

  @Override
  public Map<Edge<SimulationStates.MultipebbleSimulationState<S>>, ValuationSet> edgeMap(
    SimulationStates.MultipebbleSimulationState<S> state
  ) {
    Map<Edge<SimulationStates.MultipebbleSimulationState<S>>, ValuationSet> out = new HashMap<>();

    if (state.equals(sinkState)) {
      out.put(Edge.of(sinkState, 1), factory.universe());
      return out;
    }

    if (state.owner().isOdd()) {
      if (1 == state.even().count()
        && knownPairs.contains(Pair.of(state.odd().state(), state.even().onlyState()))) {
        out.put(Edge.of(state, 0), factory.universe());
        return out;
      }

      if (!state.even().flag() && state.odd().flag()) {
        out.put(Edge.of(sinkState, 1), factory.universe());
        return out;
      }

      leftAutomaton.edgeMap(state.odd().state()).forEach((edge, valSet) -> {
        valSet.forEach(valuation -> {
          var target = SimulationStates.MultipebbleSimulationState.of(
            Pebble.of(edge.successor(), leftAutomaton.acceptance().isAcceptingEdge(edge)),
            state.even().setFlag(false),
            valuation
          );
          out.put(Edge.of(target, 0), factory.universe());
        });
      });

    } else {
      var possibilities = state
        .even()
        .successors(rightAutomaton, BitSetUtil.fromInt(state.valuation()));
      if (possibilities.size() == 0) {
        out.put(Edge.of(sinkState, 1), factory.universe());
        return out;
      }

      possibilities.forEach(p -> {
        Set<S> ensureSucc = new HashSet<>();
        state.even().pebbles().forEach(ps -> {
          ensureSucc.addAll(rightAutomaton.successors(ps.state()));
        });
        p.pebbles().forEach(peb -> {
          if (!ensureSucc.contains(peb.state())) {
            throw new AssertionError("successor calculation broken");
          }
        });

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
  public Set<MultipebbleSimulationState<S>> states() {
    return SimulationStates.MultipebbleSimulationState.universe(
      Pebble.universe(leftAutomaton),
      MultiPebble.universe(rightAutomaton, pebbleCount),
      leftAutomaton.factory().universe()
    );
  }

  @Override
  public ParityAcceptance acceptance() {
    return new ParityAcceptance(2, ParityAcceptance.Parity.MAX_EVEN);
  }

  @Override
  public Set<SimulationStates.MultipebbleSimulationState<S>> initialStates() {
    return Set.of(initialState);
  }

  @Override
  public ValuationSetFactory factory() {
    return factory;
  }

  public static <S> ForwardDirectSimulation<S> of(
    Automaton<S, BuchiAcceptance> leftAutomaton,
    Automaton<S, BuchiAcceptance> rightAutomaton,
    S leftState,
    S rightState,
    int pebbleCount,
    Set<Pair<S, S>> known
  ) {
    return new ForwardDirectSimulation<>(
      leftAutomaton, rightAutomaton, leftState, rightState, pebbleCount, known
    );
  }
}