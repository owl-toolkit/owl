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

import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
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

/**
 * Simulation type for forward-direct multipebble simulation games.
 *
 * @param <S> The type of state of the underlying automaton.
 */
public class ForwardDirectSimulation<S>
  implements SimulationType<S, SimulationStates.MultipebbleSimulationState<S>> {
  final Automaton<S, BuchiAcceptance> leftAutomaton;
  final Automaton<S, BuchiAcceptance> rightAutomaton;
  final BddSetFactory factory;
  final S leftState;
  final S rightState;
  final MultipebbleSimulationState<S> initialState;
  final SimulationStates.MultipebbleSimulationState<S> sinkState;
  final int pebbleCount;
  final Set<Pair<S, S>> knownPairs;

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
    this.knownPairs = Set.copyOf(known);


    this.factory = FactorySupplier.defaultSupplier()
      .getBddSetFactory(List.of("a"));

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
  public Map<Edge<SimulationStates.MultipebbleSimulationState<S>>, BddSet> edgeMap(
    SimulationStates.MultipebbleSimulationState<S> state
  ) {
    Map<Edge<SimulationStates.MultipebbleSimulationState<S>>, BddSet> out = new HashMap<>();

    if (state.equals(sinkState)) {
      return Map.of(Edge.of(sinkState, 1), factory.universe());
    }

    if (state.owner().isOdd()) {
      if (1 == state.even().count()
        && knownPairs.contains(Pair.of(state.odd().state(), state.even().onlyState()))) {

        return Map.of(Edge.of(state, 0), factory.universe());
      }

      if (!state.even().flag() && state.odd().flag()) {
        return Map.of(Edge.of(sinkState, 1), factory.universe());
      }

      leftAutomaton.edgeMap(state.odd().state()).forEach((edge, valSet) -> {
        valSet.toSet().forEach((Consumer<? super BitSet>) valuation -> {
          var target = MultipebbleSimulationState.of(
            Pebble.of(edge.successor(), leftAutomaton.acceptance().isAcceptingEdge(edge)),
            state.even().setFlag(false),
            valuation
          );
          out.put(Edge.of(target, 0), factory.universe());
        });
      });

    } else {
      if (state.valuation() < 0) {
        throw new AssertionError("Valuation should not be negative here.");
      }

      var possibilities = state
        .even()
        .successors(rightAutomaton, BitSet2.fromInt(state.valuation()));

      if (possibilities.isEmpty()) {
        return Map.of(Edge.of(sinkState, 1), factory.universe());
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
  public ParityAcceptance acceptance() {
    return new ParityAcceptance(2, ParityAcceptance.Parity.MAX_EVEN);
  }

  @Override
  public Set<SimulationStates.MultipebbleSimulationState<S>> initialStates() {
    return Set.of(initialState);
  }

  @Override
  public BddSetFactory factory() {
    return factory;
  }
}
