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

import com.google.auto.value.AutoValue;
import java.util.BitSet;
import java.util.List;
import owl.automaton.Automaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.collections.BitSet2;
import owl.game.Game;

public class SimulationStates {
  private SimulationStates() {}

  @AutoValue
  public abstract static class LookaheadSimulationState<S>
    extends SimulationType.SimulationState {
    public abstract S odd();

    public abstract S even();

    public abstract List<Transition<S>> moves();

    static <S> LookaheadSimulationState<S> of(S odd, S even) {
      return new AutoValue_SimulationStates_LookaheadSimulationState<>(
        Game.Owner.PLAYER_1, odd, even, List.of()
      );
    }

    static <S> LookaheadSimulationState<S> of(S odd, S even, List<Transition<S>> buf) {
      return new AutoValue_SimulationStates_LookaheadSimulationState<>(
        Game.Owner.PLAYER_2, odd, even, buf
      );
    }

    @Override
    public String toString() {
      return (owner().isOdd() ? "O: " : "E: ")
        + odd()
        + '|'
        + even()
        + (moves().isEmpty() ? "" : moves().toString());
    }

    public boolean isValid(Automaton<S, BuchiAcceptance> aut) {
      boolean out = true;
      S current = odd();
      // tests for each move whether it conforms to the given automaton's transition relation
      for (var move : moves()) {
        out &= move.isValid(current, aut);
        current = move.target();
      }
      return out;
    }

    public boolean flag() {
      return moves()
        .stream()
        .anyMatch(Transition::flag);
    }
  }

  /**
   * Holds all information necessary to implement forward multipebble simulations.
   * It contains a pebble controlled by Spoiler, a multipebble (i.e. a collection of pebbles)
   * controlled by Duplicator as well as a valuation chosen by Spoiler.
   *
   * @param <S> Type of state for the underlying automaton.
   */
  @AutoValue
  public abstract static class MultipebbleSimulationState<S>
    extends SimulationType.SimulationState {
    public abstract Pebble<S> odd();

    public abstract MultiPebble<S> even();

    // using an integer instead of a BitSet here is a hacky solution designed to bypass some
    // limitations that AutoValue has with storing BitSets.
    public abstract int valuation();

    static <S> MultipebbleSimulationState<S> of(Pebble<S> odd, MultiPebble<S> even) {
      return new AutoValue_SimulationStates_MultipebbleSimulationState<>(
        Game.Owner.PLAYER_1, odd, even, -1
      );
    }

    static <S> MultipebbleSimulationState<S> of(Pebble<S> odd, MultiPebble<S> even,
                                                BitSet val) {
      return new AutoValue_SimulationStates_MultipebbleSimulationState<>(
        Game.Owner.PLAYER_2, odd, even, BitSet2.toInt(val)
      );
    }

    @Override
    public String toString() {
      return (owner().isOdd() ? "O: " : "E: ")
        + odd()
        + '|'
        + even()
        + ' '
        + valuation();
    }
  }
}
