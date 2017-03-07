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

package owl.algorithms;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import owl.automaton.Automaton;
import owl.collections.TarjanStack;

public final class SccAnalyser<S> {
  private final Object2IntMap<S> id = new Object2IntOpenHashMap<>();
  private final Object2IntMap<S> lowlink = new Object2IntOpenHashMap<>();
  private final Deque<S> stack = new TarjanStack<>();
  private final Function<S, Set<S>> successorFunction;
  private int num = 0;

  private SccAnalyser(Function<S, Set<S>> successorFunction) {
    this.successorFunction = successorFunction;
  }

  /**
   * This method computes the SCCs of the state-/transition-graph of the
   * automaton. It is based on Tarjan's strongly connected component
   * algorithm. It runs in linear time, assuming the Map-operation get and put
   * and containsKey (and the onStack set-operations) take constant time,
   * which is acc. to java Documentation the case if the hash-function is good
   * enough, also the checks for forbiddenEdges and allowedState need to be
   * constant for the function to run in linear time.
   *
   * @param automaton:
   *     Automaton, for which the class is analysed
   *
   * @return list of set of states, where each set corresponds to a (maximal) SCC.. The list is
   * ordered according to the topological ordering in the "condensation graph", aka the graph where
   * the SCCs are vertices, ordered such that for each transition a->b in the condensation graph, a
   * is in the list before b
   */
  public static <S> List<Set<S>> computeSccs(Automaton<S, ?> automaton) {
    SccAnalyser<S> analyser = new SccAnalyser<>(automaton::getSuccessors);
    List<Set<S>> sccList = new ArrayList<>();

    for (S state : automaton.getInitialStates()) {
      analyser.stack.push(state);
      sccList.addAll(analyser.computeSccs());
    }

    return sccList;
  }

  private List<Set<S>> computeSccs() {
    num++;
    S node = stack.peek();
    lowlink.put(node, num);
    id.put(node, num);
    List<Set<S>> result = new ArrayList<>();

    successorFunction.apply(node).forEach((successor) -> {
      if (!id.containsKey(successor)) {
        stack.push(successor);
        result.addAll(computeSccs());
        lowlink.put(node, Math.min(lowlink.getInt(node), lowlink.getInt(successor)));
      } else if (id.getInt(successor) < id.getInt(node)
        && stack.contains(successor)) {
        lowlink.put(node, Math.min(lowlink.getInt(node), id.getInt(successor)));
      }
    });

    if (lowlink.getInt(node) == id.getInt(node)) {
      Set<S> set = new HashSet<>();

      while (!stack.isEmpty() && id.getInt(stack.peek()) >= id.getInt(node)) {
        S otherNode = stack.pop();
        set.add(otherNode);
      }

      result.add(set);
    }

    return result;
  }
}
