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

import static owl.automaton.algorithm.simulations.SimulationUtil.possibleKSets;

import java.util.BitSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import owl.automaton.Automaton;
import owl.automaton.HashMapAutomaton;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.edge.Edge;
import owl.bdd.FactorySupplier;
import owl.collections.Pair;

public class SimulationGameTest {

  private Automaton<Integer, BuchiAcceptance> buildAutomatonOne() {

    MutableAutomaton<Integer, BuchiAcceptance> aut
      = HashMapAutomaton.create(List.of("a"), BuchiAcceptance.INSTANCE);
    BitSet a = aut.factory().of(0).iterator(1).next();

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

  private Pair<Automaton<Integer, BuchiAcceptance>, Automaton<Integer, BuchiAcceptance>>
    buildAutomataThree() {

    var factory = FactorySupplier.defaultSupplier()
      .getBddSetFactory();

    MutableAutomaton<Integer, BuchiAcceptance> left
      = HashMapAutomaton.create(List.of("a", "b", "c", "d"), factory, BuchiAcceptance.INSTANCE);
    MutableAutomaton<Integer, BuchiAcceptance> right
      = HashMapAutomaton.create(List.of("a", "b", "c", "d"), factory, BuchiAcceptance.INSTANCE);

    BitSet a = factory.of(0).iterator(4).next();
    BitSet b = factory.of(1).iterator(4).next();
    BitSet c = factory.of(2).iterator(4).next();
    BitSet d = factory.of(3).iterator(4).next();

    left.addState(1);
    left.addInitialState(1);
    left.addState(2);
    left.addState(3);

    left.addEdge(1, a, Edge.of(2));
    left.addEdge(2, b, Edge.of(3));
    left.addEdge(2, c, Edge.of(3));
    left.addEdge(2, d, Edge.of(3));
    left.addEdge(3, a, Edge.of(3, 0));

    right.addState(1);
    right.addInitialState(1);
    right.addState(2);
    right.addState(3);
    right.addState(4);
    right.addState(5);

    right.addEdge(1, a, Edge.of(2));
    right.addEdge(1, a, Edge.of(3));
    right.addEdge(1, a, Edge.of(5));
    right.addEdge(2, b, Edge.of(4));
    right.addEdge(3, c, Edge.of(4));
    right.addEdge(5, d, Edge.of(4));
    right.addEdge(4, a, Edge.of(4, 0));

    left.trim();
    right.trim();

    assert left.states().size() == 3;
    assert right.states().size() == 5;

    return Pair.of(left, right);
  }

  private Pair<Automaton<Integer, BuchiAcceptance>, Automaton<Integer, BuchiAcceptance>>
    buildAutomataFvsDe() {

    var factory = FactorySupplier.defaultSupplier()
      .getBddSetFactory();
    BitSet a = factory.of(0).iterator(1).next();

    MutableAutomaton<Integer, BuchiAcceptance> l
      = HashMapAutomaton.create(List.of("a"), factory, BuchiAcceptance.INSTANCE);
    MutableAutomaton<Integer, BuchiAcceptance> r
      = HashMapAutomaton.create(List.of("a"), factory, BuchiAcceptance.INSTANCE);

    l.addState(1);
    l.addInitialState(1);
    l.addState(2);
    l.addState(3);

    r.addState(1);
    r.addInitialState(1);
    r.addState(2);
    r.addState(3);

    l.addEdge(1, a, Edge.of(2));
    l.addEdge(2, a, Edge.of(3, 0));
    l.addEdge(3, a, Edge.of(3));

    r.addEdge(1, a, Edge.of(2));
    r.addEdge(2, a, Edge.of(3));
    r.addEdge(3, a, Edge.of(3));

    l.trim();
    r.trim();

    assert l.states().size() == 3;
    assert r.states().size() == 3;

    return Pair.of(l, r);
  }

  private Pair<Automaton<Integer, BuchiAcceptance>, Automaton<Integer, BuchiAcceptance>>
  buildAutomataDiVDe() {

    var factory = FactorySupplier.defaultSupplier()
      .getBddSetFactory();
    BitSet a = factory.of(0).iterator(1).next();

    MutableAutomaton<Integer, BuchiAcceptance> l = HashMapAutomaton.create(
      List.of("a"), factory, BuchiAcceptance.INSTANCE);
    MutableAutomaton<Integer, BuchiAcceptance> r = HashMapAutomaton.create(
      List.of("a"), factory, BuchiAcceptance.INSTANCE);

    l.addState(1);
    l.addInitialState(1);
    l.addState(2);
    l.addState(3);

    l.addEdge(1, a, Edge.of(2, 0));
    l.addEdge(2, a, Edge.of(3));
    l.addEdge(3, a, Edge.of(3));

    r.addState(1);
    r.addInitialState(1);
    r.addState(2);
    r.addState(3);

    r.addEdge(1, a, Edge.of(2));
    r.addEdge(2, a, Edge.of(3, 0));
    r.addEdge(3, a, Edge.of(3));

    l.trim();
    r.trim();

    assert l.states().size() == 3;
    assert r.states().size() == 3;

    return Pair.of(l, r);
  }

  private Pair<Automaton<Integer, BuchiAcceptance>, Automaton<Integer, BuchiAcceptance>>
    buildAutomataTwo() {

    var factory = FactorySupplier.defaultSupplier()
      .getBddSetFactory();

    MutableAutomaton<Integer, BuchiAcceptance> left
      = HashMapAutomaton.create(List.of("a", "b", "c"), factory, BuchiAcceptance.INSTANCE);
    MutableAutomaton<Integer, BuchiAcceptance> right
      = HashMapAutomaton.create(List.of("a", "b", "c"), factory, BuchiAcceptance.INSTANCE);

    BitSet a = factory.of(0).iterator(3).next();
    BitSet b = factory.of(1).iterator(3).next();
    BitSet c = factory.of(2).iterator(3).next();

    left.addState(1);
    left.addInitialState(1);
    left.addState(2);
    left.addState(3);

    left.addEdge(1, a, Edge.of(2));
    left.addEdge(2, b, Edge.of(3));
    left.addEdge(2, c, Edge.of(3));
    left.addEdge(3, a, Edge.of(3, 0));

    right.addState(1);
    right.addInitialState(1);
    right.addState(2);
    right.addState(3);
    right.addState(4);

    right.addEdge(1, a, Edge.of(2));
    right.addEdge(1, a, Edge.of(3));
    right.addEdge(2, b, Edge.of(4));
    right.addEdge(3, c, Edge.of(4));
    right.addEdge(4, a, Edge.of(4, 0));

    left.trim();
    right.trim();

    assert left.states().size() == 3;
    assert right.states().size() == 4;

    return Pair.of(left, right);
  }

  @Test
  void multiplePebbleStrongerTest() {
    var automata = buildAutomataTwo();
    var automataThree = buildAutomataThree();
    var simulator = new BuchiSimulation();

    assert !simulator.directSimulates(
      automata.fst(), automata.snd(), 1, 1, 1
    );

    assert simulator.directSimulates(
      automata.fst(), automata.snd(), 1, 1, 2
    );

    assert !simulator.directSimulates(
      automataThree.fst(), automataThree.snd(), 1, 1, 1
    );

    assert !simulator.directSimulates(
      automataThree.fst(), automataThree.snd(), 1, 1, 2
    );

    assert simulator.directSimulates(
      automataThree.fst(), automataThree.snd(), 1, 1, 3
    );
  }

  @Test
  void simpleSelfTest() {
    var expected = List.of(
      Pair.of(1, 1), Pair.of(1, 3), Pair.of(1, 5), Pair.of(1, 2), Pair.of(1, 4),
      Pair.of(2, 2), Pair.of(2, 3), Pair.of(2, 4), Pair.of(2, 5),
      Pair.of(3, 3), Pair.of(3, 2), Pair.of(3, 4), Pair.of(3, 5),
      Pair.of(4, 4), Pair.of(4, 5),
      Pair.of(5, 4), Pair.of(5, 5)
    );
    var simulator = new BuchiSimulation();
    var automaton = buildAutomatonOne();
    assert automaton.states().size() == 5;

    assert simulator.directSimulates(automaton, automaton, 2, 3, 1);
    var relation = simulator.directSimulation(automaton, automaton, 1);

    assert relation.containsAll(expected);
    assert expected.containsAll(relation);
  }

  @Test
  void kCombTest() {
    var in = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    var oldList = possibleKSets(Set.copyOf(in), 2);
    var newList = CombinationGenerator.comb(in, 2);

    var newSet = newList.stream().map(Set::copyOf).collect(Collectors.toSet());
    assert oldList.size() == newList.size();

    oldList.forEach(s -> {
      assert newSet.contains(s);
    });

    newList.forEach(s -> {
      assert (oldList.contains(Set.copyOf(s)));
    });
  }

  @Disabled
  @Test
  void delayedVsDirectTest() {
    var auts = buildAutomataDiVDe();
    var simulator = new BuchiSimulation();

    var diRel = simulator.directSimulation(auts.fst(), auts.snd(), 1);
    var deRel = simulator.delayedSimulation(auts.fst(), auts.snd(), 1);

    Assertions.assertFalse(diRel.contains(Pair.of(1, 1)));
    Assertions.assertTrue(deRel.contains(Pair.of(1, 1)));
  }

  @Disabled
  @Test
  void fairVsDelayedTest() {
    var auts = buildAutomataFvsDe();
    var simulator = new BuchiSimulation();

    var deRel = simulator.delayedSimulation(auts.fst(), auts.snd(), 1);
    var fRel = simulator.fairSimulation(auts.fst(), auts.snd(), 1);

    assert !deRel.contains(Pair.of(1, 1));
    assert fRel.contains(Pair.of(1, 1));
  }

  @Test
  void newDirectSelfTest() {
    var expected = List.of(
      Pair.of(1, 1), Pair.of(1, 3), Pair.of(1, 5), Pair.of(1, 2), Pair.of(1, 4),
      Pair.of(2, 2), Pair.of(2, 3), Pair.of(2, 4), Pair.of(2, 5),
      Pair.of(3, 3), Pair.of(3, 2), Pair.of(3, 4), Pair.of(3, 5),
      Pair.of(4, 4), Pair.of(4, 5),
      Pair.of(5, 4), Pair.of(5, 5)
    );
    var automaton = buildAutomatonOne();
    var simulator = new BuchiSimulation();

    var rel = simulator.directSimulation(automaton, automaton, 3);
    assert expected.containsAll(rel);
    assert rel.containsAll(expected);
  }
}
