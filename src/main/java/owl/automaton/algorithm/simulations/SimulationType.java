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

import java.util.Map;
import java.util.Set;
import owl.automaton.Automaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;
import owl.game.Game;

public interface SimulationType<S, T extends SimulationType.SimulationState<S>> {
  Map<Edge<T>, ValuationSet> edgeMap(T state);

  Set<T> states();

  ParityAcceptance acceptance();

  Set<T> initialStates();

  ValuationSetFactory factory();

  abstract class SimulationState<S> {

    public abstract Game.Owner owner();

  }

  default void automataCompatible(
    Automaton<S, BuchiAcceptance> a,
    Automaton<S, BuchiAcceptance> b
  ) {
    a.factory().universe().forEach(set -> {
      assert b.factory().universe().contains(set);
    });

    b.factory().universe().forEach(set -> {
      assert a.factory().universe().contains(set);
    });
  }

}
