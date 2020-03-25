/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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

package owl.automaton.acceptance.optimization;

import de.tum.in.naturals.Indices;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import owl.automaton.MutableAutomaton;
import owl.automaton.algorithm.SccDecomposition;

public final class OmegaAcceptanceOptimizations {
  private OmegaAcceptanceOptimizations() {
  }

  public static <S> void removeTransientAcceptance(MutableAutomaton<S, ?> automaton) {
    Object2IntMap<S> stateToSccMap = new Object2IntOpenHashMap<>(automaton.size());
    stateToSccMap.defaultReturnValue(-1);

    Indices.forEachIndexed(SccDecomposition.of(automaton).sccs(),
      (index, scc) -> scc.forEach(state -> stateToSccMap.put(state, index)));

    automaton.updateEdges((state, edge) -> {
      int sccIndex = stateToSccMap.getInt(state);
      int successorSccIndex = stateToSccMap.getInt(edge.successor());

      assert sccIndex != -1;
      assert successorSccIndex != -1;

      if (sccIndex == successorSccIndex) {
        return edge;
      }

      return edge.withoutAcceptance();
    });

    automaton.trim();
  }
}
