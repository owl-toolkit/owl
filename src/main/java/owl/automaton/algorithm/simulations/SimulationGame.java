/*
 * Copyright (C) 2020, 2022  (Anton Pirogov, Salomon Sickert)
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
import java.util.List;
import owl.automaton.AbstractMemoizingAutomaton;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.algorithm.simulations.SimulationType.SimulationState;
import owl.automaton.edge.Edge;
import owl.automaton.hoa.HoaWriter;
import owl.bdd.MtBdd;
import owl.game.Game;

/**
 * Wrapper class that takes a simulationType and constructs the actual game itself based on the
 * state and transition function defined within the concrete simulationType.
 *
 * @param <S> Type of state in the underlying automata.
 * @param <T> Type of simulation state that is used.
 */
public class SimulationGame<S, T extends SimulationState>
    extends AbstractMemoizingAutomaton.EdgeTreeImplementation<T, ParityAcceptance>
    implements Game<T, ParityAcceptance> {

  final SimulationType<S, T> simulationType;

  public SimulationGame(SimulationType<S, T> type) {
    super(List.of("a"), type.initialStates(), type.acceptance());
    simulationType = type;
  }

  @Override
  public MtBdd<Edge<T>> edgeTreeImpl(T state) {
    return MtBdd.copyOf(simulationType.edges(state));
  }

  @Override
  public Owner owner(T state) {
    return state.owner();
  }

  @Override
  public List<String> variables(Owner owner) {
    throw new UnsupportedOperationException();
  }

  @Override
  public BitSet choice(T state, Owner owner) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String toString() {
    return HoaWriter.toString(this);
  }
}
