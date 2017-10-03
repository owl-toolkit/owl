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
import owl.automaton.MutableAutomaton;
import owl.automaton.algorithms.SccDecomposition;
import owl.automaton.edge.Edges;
import owl.collections.Lists2;

public final class GenericMinimizations {
  private GenericMinimizations() {
  }

  public static <S> void removeTransientAcceptance(MutableAutomaton<S, ?> automaton) {
    Object2IntMap<S> stateToSccMap = new Object2IntOpenHashMap<>(automaton.stateCount());
    stateToSccMap.defaultReturnValue(-1);

    Lists2.forEachIndexed(SccDecomposition.computeSccs(automaton),
      (index, scc) -> scc.forEach(state -> stateToSccMap.put(state, index)));

    automaton.remapEdges((state, edge) -> {
      int sccIndex = stateToSccMap.getInt(state);
      S successor = edge.getSuccessor();
      int successorSccIndex = stateToSccMap.getInt(successor);

      assert sccIndex != -1;
      assert successorSccIndex != -1;

      if (sccIndex == successorSccIndex) {
        return edge;
      }

      return Edges.create(successor);
    });
  }
}
