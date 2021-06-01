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
import java.util.List;
import org.junit.jupiter.api.Test;

public class MultiPebbleTest {
  @Test
  void predecessorsTest() {
    var aut = predecessorAutomaton();
    BitSet a = aut.factory().of(0).iterator(aut.atomicPropositions().size()).next();

    var peb1 = Pebble.of(4, false);
    var peb2 = Pebble.of(3, false);
    var mp = MultiPebble.of(
      List.of(peb1, peb2),
      2
    );

    var predecessors = mp.predecessors(aut, a);
    for (var p : peb1.predecessors(aut, a)) {
      assert predecessors.contains(MultiPebble.of(List.of(p, p), 2));
    }
    for (var p : peb2.predecessors(aut, a)) {
      assert predecessors.contains(MultiPebble.of(List.of(p, p), 2));
    }
  }
}
