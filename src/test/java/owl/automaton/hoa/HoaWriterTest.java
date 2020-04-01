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

package owl.automaton.hoa;

import java.io.Writer;
import java.util.BitSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import owl.automaton.AbstractImmutableAutomaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.edge.Edge;
import owl.factories.ValuationSetFactory;
import owl.run.Environment;
import owl.run.modules.OutputWriters;

public class HoaWriterTest {

  private static final ValuationSetFactory FACTORY
    = Environment.standard().factorySupplier().getValuationSetFactory(List.of("a"));

  @Test
  void testStateWithoutOutgoingEdgesBug() {
    var automaton = new AbstractImmutableAutomaton.NonDeterministicEdgesAutomaton<>(
      FACTORY, Set.of(1, 2), BuchiAcceptance.INSTANCE) {

      @Override
      public Set<Edge<Integer>> edges(Integer state, BitSet valuation) {
        return state == 1 ? Set.of(Edge.of(2, 0)) : Set.of();
      }
    };

    var hoaWriter = new OutputWriters.ToHoa(false, true);

    // This call should complete without exception.
    hoaWriter.write(Writer.nullWriter(), automaton);
  }
}
