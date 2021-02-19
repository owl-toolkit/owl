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
import java.util.List;
import owl.automaton.Automaton;
import owl.automaton.HashMapAutomaton;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.edge.Edge;
import owl.bdd.FactorySupplier;

public final class CommonAutomata {
  // silence PMD
  private CommonAutomata() {}

  public static Automaton<Integer, BuchiAcceptance> buildAutomatonOne() {

    var factory = FactorySupplier.defaultSupplier()
      .getValuationSetFactory(List.of("a"));
    var acceptance = BuchiAcceptance.INSTANCE;

    MutableAutomaton<Integer, BuchiAcceptance> aut = HashMapAutomaton.of(acceptance, factory);
    BitSet a = new BitSet();

    aut.addState(1);
    aut.addInitialState(1);
    aut.addState(2);
    aut.addState(3);
    aut.addState(4);
    aut.addState(5);

    aut.addEdge(1, a, Edge.of(2));
    aut.addEdge(1, a, Edge.of(3));

    aut.addEdge(2, a, Edge.of(4));

    aut.addEdge(3, a, Edge.of(5));

    aut.addEdge(4, a, Edge.of(4, 0));

    aut.addEdge(5, a, Edge.of(5, 0));

    aut.trim();
    return aut;
  }

  public static Automaton<Integer, BuchiAcceptance> anotherRefinementAutomaton() {

    var factory = FactorySupplier.defaultSupplier()
      .getValuationSetFactory(List.of("a", "b"));
    var acceptance = BuchiAcceptance.INSTANCE;

    MutableAutomaton<Integer, BuchiAcceptance> aut = HashMapAutomaton.of(acceptance, factory);
    BitSet a = factory.of(0).toSet().iterator().next();
    BitSet b = factory.of(1).toSet().iterator().next();

    aut.addState(0);
    aut.addInitialState(0);
    aut.addState(1);
    aut.addState(2);
    aut.addState(3);

    aut.addEdge(0, a, Edge.of(1));
    aut.addEdge(0, a, Edge.of(2));
    aut.addEdge(0, b, Edge.of(2));
    aut.addEdge(1, a, Edge.of(1, 0));
    aut.addEdge(1, b, Edge.of(3));
    aut.addEdge(3, b, Edge.of(1));
    aut.addEdge(2, a, Edge.of(2, 0));
    aut.trim();

    return aut;
  }

  public static Automaton<Integer, BuchiAcceptance> simpleColorRefinementAutomaton() {

    var factory = FactorySupplier.defaultSupplier()
      .getValuationSetFactory(List.of("a", "b"));
    var acceptance = BuchiAcceptance.INSTANCE;

    MutableAutomaton<Integer, BuchiAcceptance> aut = HashMapAutomaton.of(acceptance, factory);
    BitSet a = factory.of(0).toSet().iterator().next();
    BitSet b = factory.of(1).toSet().iterator().next();

    aut.addState(1);
    aut.addInitialState(1);
    aut.addState(2);
    aut.addState(3);
    aut.addState(4);
    aut.addState(5);

    aut.addEdge(1, a, Edge.of(2));
    aut.addEdge(1, a, Edge.of(3));

    aut.addEdge(2, a, Edge.of(4));

    aut.addEdge(3, a, Edge.of(5));

    aut.addEdge(4, a, Edge.of(4, 0));
    aut.addEdge(4, b, Edge.of(4, 0));

    aut.addEdge(5, a, Edge.of(5, 0));

    aut.trim();
    return aut;
  }

  public static Automaton<Integer, BuchiAcceptance> predecessorAutomaton() {

    var factory = FactorySupplier.defaultSupplier()
      .getValuationSetFactory(List.of("a", "b"));
    var acceptance = BuchiAcceptance.INSTANCE;

    MutableAutomaton<Integer, BuchiAcceptance> aut = HashMapAutomaton.of(acceptance, factory);
    BitSet a = factory.of(0).toSet().iterator().next();

    aut.addState(1);
    aut.addState(2);
    aut.addState(3);
    aut.addState(4);
    aut.addInitialState(1);

    aut.addEdge(1, a, Edge.of(2));
    aut.addEdge(1, a, Edge.of(3));
    aut.addEdge(2, a, Edge.of(4));
    aut.addEdge(3, a, Edge.of(4));
    aut.addEdge(4, a, Edge.of(4, 0));
    aut.trim();
    assert 4 == aut.states().size();
    return aut;
  }

}
