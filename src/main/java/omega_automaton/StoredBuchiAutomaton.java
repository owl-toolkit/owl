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
import com.google.common.collect.HashBiMap;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.AtomLabel;
import jhoafparser.ast.BooleanExpression;
import jhoafparser.consumer.HOAConsumer;
import jhoafparser.consumer.HOAConsumerException;
import omega_automaton.acceptance.BuchiAcceptance;
import omega_automaton.collections.Collections3;
import omega_automaton.collections.valuationset.BDDValuationSetFactory;
import omega_automaton.collections.valuationset.ValuationSet;
import omega_automaton.collections.valuationset.ValuationSetFactory;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;

public class StoredBuchiAutomaton extends Automaton<StoredBuchiAutomaton.State, BuchiAcceptance> {

    StoredBuchiAutomaton(ValuationSetFactory factory) {
        super(new BuchiAcceptance(), factory);
    }

    private State addState() {
        State state = new State();

        // Add to transition table
        transitions.put(state, new HashMap<>());

        return state;
    }

    private void addTransition(State source, boolean accepting, ValuationSet label, State successor) {
        Map<Edge<State>, ValuationSet> transition = transitions.get(source);

        Edge<State> edge;
      if (accepting) {
        edge = Edges.create(successor, 0);
      } else {
        edge = Edges.create(successor);
      }

      ValuationSet oldLabel = transition.get(edge);

        if (oldLabel == null) {
            transition.put(edge, label);
        } else {
            oldLabel.addAll(label);
        }
    }

    public boolean isAccepting(State state) {
        return Collections3.getElement(transitions.get(state).keySet()).inSet(0);
    }

    public static class State implements AutomatonState<State> {

        public String label;

        @Nullable
        @Override
        public Edge<State> getSuccessor(@Nonnull BitSet valuation) {
            throw new UnsupportedOperationException("Stored Automaton State cannot perform on-demand computations.");
        }

        @Nonnull
        @Override
        public BitSet getSensitiveAlphabet() {
            throw new UnsupportedOperationException("Stored Automaton State cannot perform on-demand computations.");
        }


        @Override
        public String toString() {
            return label == null ? "null" : label;
        }
    }

    public static class Builder implements HOAConsumer {

        private final Deque<StoredBuchiAutomaton> automata = new ArrayDeque<>();
        private StoredBuchiAutomaton automaton;
        private ValuationSetFactory valuationSetFactory;
        private Integer initialState;
        private State[] integerToState;
        private int implicitEdgeCounter;
        private BitSet acceptingStates;

        @Override
        public boolean parserResolvesAliases() {
            return false;
        }

        @Override
        public void notifyHeaderStart(String s) {
            valuationSetFactory = null;
            integerToState = null;
            initialState = null;
            automaton = null;
            acceptingStates = null;
        }

        @Override
        public void setNumberOfStates(int i) throws HOAConsumerException {
            integerToState = new State[i];
        }

        @Override
        public void addStartStates(List<Integer> list) throws HOAConsumerException {
            if (list.size() != 1 || initialState != null) {
                throw new HOAConsumerException("Only a single initial state is supported.");
            }

            initialState = list.get(0);
        }

        @Override
        public void addAlias(String s, BooleanExpression<AtomLabel> booleanExpression) throws HOAConsumerException {
            throw new HOAConsumerException("Unsupported Operation.");
        }

        @Override
        public void setAPs(List<String> list) throws HOAConsumerException {
            BiMap<String, Integer> aliases = HashBiMap.create(list.size());
            list.forEach(ap -> aliases.put(ap, aliases.size()));
            valuationSetFactory = new BDDValuationSetFactory(list.size());
        }

        @Override
        public void setAcceptanceCondition(int i, BooleanExpression<AtomAcceptance> booleanExpression) throws HOAConsumerException {
            if (i == 1 && booleanExpression.isAtom()) {
                return;
            }

            throw new HOAConsumerException("Unsupported Acceptance Conditions: " + i + ' ' + booleanExpression);
        }

        @Override
        public void provideAcceptanceName(String s, List<Object> list) throws HOAConsumerException {
            if (!"Buchi".equals(s)) {
                throw new HOAConsumerException("Unsupported Acceptance: " + s);
            }
        }

        @Override
        public void setName(String s) throws HOAConsumerException {
            // No operation
        }

        @Override
        public void setTool(String s, String s1) throws HOAConsumerException {
            // No operation
        }

        @Override
        public void addProperties(List<String> list) throws HOAConsumerException {
            // No operation
        }

        @Override
        public void addMiscHeader(String s, List<Object> list) throws HOAConsumerException {
            // No operation
        }

        @Override
        public void notifyBodyStart() throws HOAConsumerException {
            if (valuationSetFactory == null) {
                valuationSetFactory = new BDDValuationSetFactory(0);
            }

            automaton = new StoredBuchiAutomaton(valuationSetFactory);
            ensureSpaceInMap(initialState);
            integerToState[initialState] = automaton.initialState = automaton.addState();
            acceptingStates = new BitSet();
        }

        @Override
        public void addState(int i, String s, BooleanExpression<AtomLabel> booleanExpression, List<Integer> list) throws HOAConsumerException {
            ensureSpaceInMap(i);

            String label = (s == null) ? Integer.toString(i) : s;
            State state = integerToState[i];

            // Create state, if missing.
            if (state == null) {
                state = automaton.addState();
                integerToState[i] = state;
            }

            // Update label and acceptance marking for state.
            state.label = label;
            acceptingStates.set(i, isAcceptingState(list));
        }

        private static boolean isAcceptingState(List<Integer> list) throws HOAConsumerException {
            if (list == null) {
                return false;
            }

            if (!Collections3.isSingleton(list)) {
                throw new HOAConsumerException("Only state-based Büchi Acceptance is supported.");
            }

            Integer element = Collections3.getElement(list);

            if (element != 0) {
                throw new HOAConsumerException("Only state-based Büchi Acceptance is supported. Malformed index: " + element);
            }

            return true;
        }

        @Override
        public void addEdgeImplicit(int i, List<Integer> list, List<Integer> list1) throws HOAConsumerException {
            addEdgeWithLabel(i, BooleanExpression.fromImplicit(implicitEdgeCounter, valuationSetFactory.getSize()), list, list1);
            implicitEdgeCounter++;
        }

        @Override
        public void addEdgeWithLabel(int i, BooleanExpression<AtomLabel> booleanExpression, List<Integer> successors, List<Integer> accList) throws HOAConsumerException {
            State source = integerToState[i];

            if (accList != null && !accList.isEmpty()) {
                throw new HOAConsumerException("Edge acceptance is not supported.");
            }

            if (successors == null || successors.isEmpty()) {
                return;
            }

            if (successors.size() > 1) {
                throw new HOAConsumerException("Universal or create transitions are not supported.");
            }

            int index = Collections3.getElement(successors);
            State successor = integerToState[index];

            if (successor == null) {
                integerToState[index] = successor = automaton.addState();
            }

            automaton.addTransition(source, acceptingStates.get(i), toValuationSet(booleanExpression), successor);
        }

        @Override
        public void notifyEndOfState(int i) throws HOAConsumerException {
            implicitEdgeCounter = 0;
        }

        @Override
        public void notifyEnd() throws HOAConsumerException {
            automata.add(automaton);
            notifyHeaderStart(null);
        }

        @Override
        public void notifyAbort() {
            notifyHeaderStart(null);
        }

        @Override
        public void notifyWarning(String s) throws HOAConsumerException {
            // No operation
        }

        public Iterable<StoredBuchiAutomaton> getAutomata() {
            return automata;
        }

        private void ensureSpaceInMap(int id) {
            if (integerToState == null) {
                integerToState = new State[id + 1];
            }

            if (id >= integerToState.length) {
                integerToState = Arrays.copyOf(integerToState, id + 1);
            }
        }

        private ValuationSet toValuationSet(BooleanExpression<AtomLabel> label) {
            if (label.isFALSE()) {
                return valuationSetFactory.createEmptyValuationSet();
            }

            if (label.isTRUE()) {
                return valuationSetFactory.createUniverseValuationSet();
            }

            if (label.isAtom()) {
                BitSet bs = new BitSet();
                bs.set(label.getAtom().getAPIndex());
                return valuationSetFactory.createValuationSet(bs, bs);
            }

            if (label.isNOT()) {
                return toValuationSet(label.getLeft()).complement();
            }

            if (label.isAND()) {
                ValuationSet valuationSet = toValuationSet(label.getLeft());
                valuationSet.retainAll(toValuationSet(label.getRight()));
                return valuationSet;
            }

            if (label.isOR()) {
                ValuationSet valuationSet = toValuationSet(label.getLeft());
                valuationSet.addAll(toValuationSet(label.getRight()));
                return valuationSet;
            }

            throw new IllegalArgumentException("Unsupported Case: " + label);
        }
    }
}
