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
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import owl.automaton.Automaton;
import owl.automaton.AutomatonState;
import owl.collections.TarjanStack;
import owl.collections.ValuationSet;

public class SCCAnalyser<S extends AutomatonState<S>> {

  protected final Set<S> allowedStates;
  protected final Automaton<S, ?> automaton;
  protected final Map<S, ValuationSet> forbiddenEdges;
  protected final Deque<S> stack = new TarjanStack<>();

  private final Object2IntMap<S> lowlink = new Object2IntOpenHashMap<>();
  private final Object2IntMap<S> id = new Object2IntOpenHashMap<>();

  protected int n = 0;

  protected SCCAnalyser(Automaton<S, ?> a, Set<S> allowedStates, Map<S, ValuationSet> forbiddenEdges) {
    this.automaton = a;
    this.allowedStates = allowedStates;
    this.forbiddenEdges = forbiddenEdges;
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
   * @param a:
   *     Automaton, for which the class is analysed
   *
   * @return list of set of states, where each set corresponds to a (maximal) SCC.. The list is
   * ordered according to the topological ordering in the "condensation graph", aka the graph where
   * the SCCs are vertices, ordered such that for each transition a->b in the condensation graph, a
   * is in the list before b
   */
  public static <S extends AutomatonState<S>> List<Set<S>> computeSCCs(Automaton<S, ?> a) {
    SCCAnalyser<S> s = new SCCAnalyser<>(a, a.getStates(), Collections.emptyMap());
    List<Set<S>> sccs = new ArrayList<>();

    for (S state : a.getInitialStates()) {
      s.stack.push(state);
      sccs.addAll(s.computeSCCs());
    }

    return sccs;
  }

  protected List<Set<S>> computeSCCs() {
    n++;
    S v = stack.peek();
    lowlink.put(v, n);
    id.put(v, n);
    List<Set<S>> result = new ArrayList<>();

    automaton.getSuccessors(v).forEach((edge, valuation) -> {
      ValuationSet forbidden = forbiddenEdges.get(v);

      if (forbidden != null && forbidden.containsAll(valuation)) {
        return;
      }

      S w = edge.getSuccessor();

      if (allowedStates.contains(w) && !id.containsKey(w)) {
        stack.push(w);
        result.addAll(computeSCCs());
        lowlink.put(v, Math.min(lowlink.getInt(v), lowlink.getInt(w)));
      } else if (allowedStates.contains(w) && id.getInt(w) < id.getInt(v) && stack
        .contains(w)) {
        lowlink.put(v, Math.min(lowlink.getInt(v), id.getInt(w)));
      }
    });

    if (lowlink.getInt(v) == id.getInt(v)) {
      Set<S> set = new HashSet<>();

      while (!stack.isEmpty() && id.getInt(stack.peek()) >= id.getInt(v)) {
        S w = stack.pop();
        set.add(w);
      }

      result.add(set);
    }

    return result;
  }
}
