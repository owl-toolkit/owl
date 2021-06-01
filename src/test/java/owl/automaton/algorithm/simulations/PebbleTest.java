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

import static owl.automaton.algorithm.simulations.CommonAutomata.predecessorAutomaton;

import java.util.BitSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import owl.bdd.BddSet;

public class PebbleTest {
  @Test
  void predecessorsTest() {
    var expectedForA = Set.of(
      Pebble.of(2, false), Pebble.of(3, false), Pebble.of(4, true)
    );
    var expectedForB = Set.of(
      Pebble.of(2, false)
    );

    var aut = predecessorAutomaton();
    BitSet a = aut.factory().of(0).iterator(aut.atomicPropositions().size()).next();
    BitSet b = aut.factory().of(1).iterator(aut.atomicPropositions().size()).next();
    BddSet ab = aut.factory().of(a,
      aut.atomicPropositions().size()).union(aut.factory().of(b, aut.atomicPropositions().size()));

    assert Pebble.of(4, false).predecessors(aut, a).equals(expectedForA);
    assert Pebble.of(4, false).predecessors(aut, b).isEmpty();
    assert Pebble.of(1, false).predecessors(aut, a).isEmpty();

    var combined = Pebble.of(4, false).predecessors(aut, ab);
    assert combined.containsAll(expectedForA);
    assert combined.containsAll(expectedForB);
  }
}
