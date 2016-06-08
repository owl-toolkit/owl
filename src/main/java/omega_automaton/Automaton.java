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

package omega_automaton;

import com.google.common.collect.BiMap;
import jhoafparser.consumer.HOAConsumer;
import omega_automaton.acceptance.OmegaAcceptance;
import omega_automaton.output.HOAConsumerExtended;
import omega_automaton.collections.valuationset.ValuationSet;
import omega_automaton.collections.valuationset.ValuationSetFactory;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;

// TODO: clarify relation between, nondeterministic/deterministic transition relation acceptance.
public abstract class Automaton<S extends AutomatonState<S>, Acc extends OmegaAcceptance> {

    @Nullable
    public S initialState;
    protected final Map<S, Map<S, ValuationSet>> transitions;
    protected final Map<S, Map<BitSet, ValuationSet>> acceptanceIndices;
    public Acc acceptance;

    public final ValuationSetFactory valuationSetFactory;

    protected Automaton(Automaton<S, Acc> a) {
        initialState = a.initialState;
        transitions = a.transitions;
        acceptanceIndices = a.acceptanceIndices;
        valuationSetFactory = a.valuationSetFactory;
    }

    protected Automaton(ValuationSetFactory factory) {
        transitions = new HashMap<>();
        acceptanceIndices = new HashMap<>();
        valuationSetFactory = factory;
    }

    public void generate() {
        generate(getInitialState());
    }

    public void generate(S initialState) {
        // Return if already generated
        if (transitions.containsKey(initialState)) {
            return;
        }

        Queue<S> workList = new ArrayDeque<>();
        workList.add(initialState);

        while (!workList.isEmpty()) {
            S current = workList.remove();
            Collection<S> next = getSuccessors(current).keySet();

            for (S successor : next) {
                if (!transitions.containsKey(successor)) {
                    workList.add(successor);
                }
            }
        }
    }

    public boolean hasSuccessors(S state) {
        return !getSuccessors(state).isEmpty();
    }

    public boolean isSink(S state) {
        ValuationSet valuationSet = getSuccessors(state).get(state);
        return valuationSet != null && valuationSet.isUniverse();
    }

    public boolean isTransient(S state) {
        ValuationSet valuationSet = getSuccessors(state).get(state);
        return valuationSet == null || valuationSet.isEmpty();
    }

    public boolean isDeterministic() {
        return getStates().stream().allMatch(this::isDeterministic);
    }

    public boolean isDeterministic(S state) {
        ValuationSet valuationSet = valuationSetFactory.createEmptyValuationSet();

        for (Map.Entry<S, ValuationSet> entry : getSuccessors(state).entrySet()) {
            if (valuationSet.intersects(entry.getValue())) {
                return false;
            } else {
                valuationSet.addAll(entry.getValue());
            }
        }

        return true;
    }

    @Nullable
    public S getSuccessor(S state, BitSet valuation) {
        for (Map.Entry<S, ValuationSet> transition : getSuccessors(state).entrySet()) {
            if (transition.getValue().contains(valuation)) {
                return transition.getKey();
            }
        }

        return null;
    }

    public Set<S> getSuccessors(S state, BitSet valuation) {
        Set<S> successors = new HashSet<>();

        for (Map.Entry<S, ValuationSet> transition : getSuccessors(state).entrySet()) {
            if (transition.getValue().contains(valuation)) {
                successors.add(transition.getKey());
            }
        }

        return successors;
    }

    public Map<S, ValuationSet> getSuccessors(S state) {
        Map<S, ValuationSet> row = transitions.get(state);

        if (row == null) {
            row = state.getSuccessors();
            transitions.put(state, row);
        }

        return row;
    }

    public boolean hasAcceptanceIndex(S state, int i, BitSet valuation) {
        BitSet indices = getAcceptanceIndices(state, valuation);
        return indices != null && indices.get(i);
    }

    public BitSet getAcceptanceIndices(S state, BitSet valuation) {
        Map<BitSet, ValuationSet> row = getAcceptanceIndices(state);

        for (Map.Entry<BitSet, ValuationSet> entry : row.entrySet()) {
            if (entry.getValue().contains(valuation)) {
                return entry.getKey();
            }
        }

        return null;
    }

    public Map<BitSet, ValuationSet> getAcceptanceIndices(S state) {
        Map<BitSet, ValuationSet> row = acceptanceIndices.get(state);

        if (row == null) {
            row = state.getAcceptanceIndices();
            acceptanceIndices.put(state, row);
        }

        return row;
    }

    public int size() {
        return transitions.size();
    }

    public S getInitialState() {
        if (initialState == null) {
            initialState = generateInitialState();
        }

        return initialState;
    }

    public Set<S> getStates() {
        return transitions.keySet();
    }

    public void removeUnreachableStates() {
        Set<S> states = new HashSet<>();
        states.add(getInitialState());
        removeUnreachableStates(states);
    }

    public void removeUnreachableStates(Set<S> reach) {
        getReachableStates(reach);
        removeStatesIf(s -> !reach.contains(s));
    }

    /**
     * This method removes unused states and their in- and outgoing transitions.
     * If the set dependsOn the initial state, it becomes an automaton with the
     * only state false. Use this method only if you are really sure you want to
     * remove the states! The method is designed for the assumptions, that only
     * nonaccepting SCCs are deleted, and the idea is also that everything,
     * which is deleted will be replaced with a trap state (in makeComplete).
     *
     * @param states: Set of states that is to be removed
     */
    public void removeStates(Collection<S> states) {
        if (states.contains(initialState)) {
            initialState = null;
            transitions.clear();
        } else {
            removeStatesIf(states::contains);
        }
    }

    public void removeStatesIf(Predicate<S> predicate) {
        transitions.keySet().removeIf(predicate);
        transitions.forEach((k, v) -> v.keySet().removeIf(predicate));

        if (predicate.test(initialState)) {
            initialState = null;
        }
    }

    public ValuationSetFactory getFactory() {
        return valuationSetFactory;
    }

    protected S generateInitialState() {
        throw new UnsupportedOperationException();
    }

    /**
     * This method has no side effects
     *
     * @param scc: set of states
     * @return true if the only transitions from scc go to scc again and false
     * otherwise
     */
    public boolean isBSSC(Set<S> scc) {
        return scc.stream().allMatch(s -> scc.containsAll(getSuccessors(s).keySet()));
    }

    /**
     * The method replaces antecessor by replacement. Both must be in the
     * states-set (and both must not be null) when calling the method.
     * Antecessor gets deleted during the method, and the transitions to
     * antecessor will be recurved towards replacement.
     * <p>
     * The method throws an IllegalArgumentException, when one of the parameters
     * is not in the states-set
     */
    protected void replaceBy(S antecessor, S replacement) {
        if (!(transitions.containsKey(antecessor) && transitions.containsKey(replacement))) {
            throw new IllegalArgumentException();
        }

        transitions.remove(antecessor).clear();

        for (Map<S, ValuationSet> edges : transitions.values()) {
            ValuationSet vs = edges.get(antecessor);

            if (vs == null) {
                continue;
            }

            ValuationSet vs2 = edges.get(replacement);

            if (vs2 == null) {
                edges.put(replacement, vs);
            } else {
                vs2.addAll(vs);
            }
        }

        if (antecessor.equals(initialState)) {
            initialState = replacement;
        }
    }

    private void getReachableStates(Set<S> states) {
        Deque<S> workList = new ArrayDeque<>(states);

        while (!workList.isEmpty()) {
            S state = workList.remove();

            getSuccessors(state).forEach((suc, v) -> {
                if (states.add(suc)) {
                    workList.add(suc);
                }
            });
        }
    }

    public final void toHOA(HOAConsumer ho, BiMap<String, Integer> aliases) {
        HOAConsumerExtended hoa = new HOAConsumerExtended(ho, valuationSetFactory, aliases, acceptance, initialState, size());
        toHOABody(hoa);
        hoa.done();
    }

    public final void toHOABody(HOAConsumerExtended hoa) {
        for (S s : getStates()) {
            hoa.addState(s);
            getSuccessors(s).forEach((k, v) -> {
                Map<BitSet, ValuationSet> acceptanceIndices = getAcceptanceIndices(s);

                if (acceptanceIndices != null) {
                    acceptanceIndices.forEach((b, v2) -> hoa.addEdge(v.intersect(v2), k, b));
                } else {
                    hoa.addEdge(v, k);
                }
            });

            toHOABodyEdge(s, hoa);

            hoa.stateDone();
        }
    }

    protected void toHOABodyEdge(S state, HOAConsumerExtended hoa) {
        // NOP.
    }
}