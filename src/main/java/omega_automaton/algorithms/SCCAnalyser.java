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

package omega_automaton.algorithms;

import omega_automaton.Automaton;
import omega_automaton.AutomatonState;
import omega_automaton.collections.TarjanStack;
import omega_automaton.collections.TranSet;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Christopher Ziegler
 */
public class SCCAnalyser<S extends AutomatonState<S>> {
    private final Map<S, Integer> lowlink = new HashMap<>();
    private final Map<S, Integer> number = new HashMap<>();
    private final Deque<S> stack = new TarjanStack<>();
    private final Automaton<S, ?> automaton;
    private final TranSet<S> forbiddenEdges;
    private final Set<S> allowedStates;
    private int n = 0;

    private SCCAnalyser(Automaton<S, ?> a) {
        this(a, a.getStates(), new TranSet<>(a.getFactory()));
    }

    private SCCAnalyser(Automaton<S, ?> a, Set<S> allowedStates, TranSet<S> forbiddenEdges) {
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
     * @param a: Automaton, for which the class is analysed
     * @return list of set of states, where each set corresponds to a (maximal)
     * SCC.. The list is ordered according to the topological ordering
     * in the "condensation graph", aka the graph where the SCCs are
     * vertices, ordered such that for each transition a->b in the
     * condensation graph, a is in the list before b
     */
    public static <S extends AutomatonState<S>> List<Set<S>> SCCsStates(Automaton<S, ?> a) {
        SCCAnalyser<S> s = new SCCAnalyser<>(a);
        s.stack.push(a.getInitialState());
        return s.SCCsStatesRecursively();
    }

    /**
     * This method refines the SCC in order to have the sub-SCCs if
     * forbiddenEdges are not allowed to use
     *
     * @param SCC:            the SCC that will be processed
     * @param forbiddenEdges: the edges that are forbidden
     * @param a:              Automaton, for which the SCC-Analysis has to be made
     * @return the sub-SCCs of the SCC as list in topologic ordering
     */
    public static <S extends AutomatonState<S>> List<TranSet<S>> subSCCsTran(Automaton<S, ?> a, TranSet<S> SCC, TranSet<S> forbiddenEdges) {
        SCCAnalyser<S> s = new SCCAnalyser<>(a, SCC.asMap().keySet(), forbiddenEdges);
        return s.subSCCsTranPrivate();
    }

    private List<TranSet<S>> subSCCsTranPrivate() {
        List<Set<S>> resultStates = new ArrayList<>();
        Set<S> notYetProcessed = new HashSet<>(allowedStates);

        while (!notYetProcessed.isEmpty()) {
            Iterator<S> iter = notYetProcessed.iterator();
            S state = iter.next();
            iter.remove();
            stack.push(state);
            resultStates.addAll(SCCsStatesRecursively());
            resultStates.forEach(notYetProcessed::removeAll);
        }

        return resultStates.stream().map(s -> sccToTran(automaton, s, forbiddenEdges)).collect(Collectors.toList());
    }

    private List<Set<S>> SCCsStatesRecursively() {
        n++;
        S v = stack.peek();
        lowlink.put(v, n);
        number.put(v, n);
        List<Set<S>> result = new ArrayList<>();

        automaton.getSuccessors(v).forEach((edge, valuation) -> {
            // edge not forbidden
            if (!forbiddenEdges.containsAll(v, valuation)) {
                S w = edge.successor;

                if (allowedStates.contains(w) && !number.containsKey(w)) {
                    stack.push(w);
                    result.addAll(SCCsStatesRecursively());
                    lowlink.put(v, Math.min(lowlink.get(v), lowlink.get(w)));
                } else if (allowedStates.contains(w) && number.get(w) < number.get(v) && stack.contains(w)) {
                    lowlink.put(v, Math.min(lowlink.get(v), number.get(w)));
                }
            }
        });

        if (lowlink.get(v).equals(number.get(v))) {
            Set<S> set = new HashSet<>();

            while (!stack.isEmpty() && number.get(stack.peek()) >= number.get(v)) {
                S w = stack.pop();
                set.add(w);
            }

            result.add(set);
        }

        return result;
    }

    public static <S extends AutomatonState<S>> TranSet<S> sccToTran(Automaton<S, ?> aut, Set<S> scc, TranSet<S> forbiddenEdges) {
        TranSet<S> result = new TranSet<>(aut.getFactory());

        scc.forEach(s -> aut.getSuccessors(s).forEach((edge, valuation) -> {
            if (scc.contains(edge.successor)) {
                result.addAll(s, valuation);
            }
        }));

        result.removeAll(forbiddenEdges);
        return result;
    }
}
