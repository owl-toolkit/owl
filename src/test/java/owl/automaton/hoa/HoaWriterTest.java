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

package owl.automaton.hoa;

import java.util.List;
import java.util.Set;
import jhoafparser.consumer.HOAConsumerException;
import jhoafparser.consumer.HOAConsumerNull;
import jhoafparser.consumer.HOAIntermediateCheckValidity;
import org.junit.jupiter.api.Test;
import owl.automaton.AbstractMemoizingAutomaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.edge.Edge;
import owl.bdd.MtBdd;

public class HoaWriterTest {

  @Test
  void testStateWithoutOutgoingEdgesBug() throws HOAConsumerException {
    var automaton = new AbstractMemoizingAutomaton.EdgeTreeImplementation<>(
      List.of("a"), Set.of(1, 2), BuchiAcceptance.INSTANCE) {

      @Override
      public MtBdd<Edge<Integer>> edgeTreeImpl(Integer state) {
        return state == 1 ? MtBdd.of(Set.of(Edge.of(2, 0))) : MtBdd.of();
      }
    };

    // This call should complete without exception.
    HoaWriter.write(
      automaton, new HOAIntermediateCheckValidity(new HOAConsumerNull()), true);
  }
}
