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
import java.util.List;
import owl.automaton.Automaton;
import owl.automaton.HashMapAutomaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.edge.Edge;

public final class CommonAutomata {

  private CommonAutomata() {}

  public static Automaton<Integer, BuchiAcceptance> buildAutomatonOne() {

    var automaton = HashMapAutomaton.<Integer, BuchiAcceptance>
      create(List.of("a"), BuchiAcceptance.INSTANCE);

    automaton.addInitialState(1);
    automaton.addState(2);
    automaton.addState(3);
    automaton.addState(4);
    automaton.addState(5);

    automaton.addEdge(1, new BitSet(), Edge.of(2));
    automaton.addEdge(1, new BitSet(), Edge.of(3));
    automaton.addEdge(2, new BitSet(), Edge.of(4));
    automaton.addEdge(3, new BitSet(), Edge.of(5));
    automaton.addEdge(4, new BitSet(), Edge.of(4, 0));
    automaton.addEdge(5, new BitSet(), Edge.of(5, 0));

    automaton.trim();
    return automaton;
  }

  public static Automaton<Integer, BuchiAcceptance> anotherRefinementAutomaton() {

    var automaton = HashMapAutomaton.<Integer, BuchiAcceptance>
      create(List.of("a", "b"), BuchiAcceptance.INSTANCE);

    BitSet a = new BitSet();
    a.set(0);
    BitSet b = new BitSet();
    b.set(1);

    automaton.addInitialState(0);
    automaton.addState(1);
    automaton.addState(2);
    automaton.addState(3);

    automaton.addEdge(0, a, Edge.of(1));
    automaton.addEdge(0, a, Edge.of(2));
    automaton.addEdge(0, b, Edge.of(2));
    automaton.addEdge(1, a, Edge.of(1, 0));
    automaton.addEdge(1, b, Edge.of(3));
    automaton.addEdge(3, b, Edge.of(1));
    automaton.addEdge(2, a, Edge.of(2, 0));

    automaton.trim();
    return automaton;
  }

  public static Automaton<Integer, BuchiAcceptance> simpleColorRefinementAutomaton() {

    var automaton = HashMapAutomaton.<Integer, BuchiAcceptance>
      create(List.of("a", "b"), BuchiAcceptance.INSTANCE);

    BitSet a = new BitSet();
    a.set(0);
    BitSet b = new BitSet();
    b.set(1);

    automaton.addInitialState(1);
    automaton.addState(2);
    automaton.addState(3);
    automaton.addState(4);
    automaton.addState(5);

    automaton.addEdge(1, a, Edge.of(2));
    automaton.addEdge(1, a, Edge.of(3));
    automaton.addEdge(2, a, Edge.of(4));
    automaton.addEdge(3, a, Edge.of(5));
    automaton.addEdge(4, a, Edge.of(4, 0));
    automaton.addEdge(4, b, Edge.of(4, 0));
    automaton.addEdge(5, a, Edge.of(5, 0));

    automaton.trim();
    return automaton;
  }

  public static Automaton<Integer, BuchiAcceptance> predecessorAutomaton() {

    var automaton = HashMapAutomaton.<Integer, BuchiAcceptance>
      create(List.of("a", "b"), BuchiAcceptance.INSTANCE);

    BitSet a = new BitSet();
    a.set(0);

    automaton.addInitialState(1);
    automaton.addState(2);
    automaton.addState(3);
    automaton.addState(4);

    automaton.addEdge(1, a, Edge.of(2));
    automaton.addEdge(1, a, Edge.of(3));
    automaton.addEdge(2, a, Edge.of(4));
    automaton.addEdge(3, a, Edge.of(4));
    automaton.addEdge(4, a, Edge.of(4, 0));

    automaton.trim();
    assert 4 == automaton.states().size();
    return automaton;
  }
}
