/*
 * Copyright (C) 2016  (See AUTHORS)
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

package owl.automaton.minimizations;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import owl.algorithms.SccAnalyser;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edges;

public final class GenericMinimizations {
  private GenericMinimizations() {}

  public static <S, A extends OmegaAcceptance> Minimization<S, A> removeTransientAcceptance() {
    return automaton -> {
      List<Set<S>> sccs = SccAnalyser.computeSccs(automaton);
      Object2IntMap<S> stateToSccMap = new Object2IntOpenHashMap<>(automaton.stateCount());
      stateToSccMap.defaultReturnValue(-1);
      ListIterator<Set<S>> sccIterator = sccs.listIterator();
      while (sccIterator.hasNext()) {
        int sccIndex = sccIterator.nextIndex();
        sccIterator.next().forEach(state -> stateToSccMap.put(state, sccIndex));
      }

      automaton.remapEdges((state, edge) -> {
        int sccIndex = stateToSccMap.getInt(state);
        assert sccIndex != -1;
        S successor = edge.getSuccessor();
        int successorSccIndex = stateToSccMap.getInt(successor);
        assert successorSccIndex != -1;
        if (sccIndex != successorSccIndex) {
          return Edges.create(successor);
        }
        return edge;
      });
    };
  }
}
