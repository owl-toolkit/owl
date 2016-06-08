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

package translations.ldba2parity;

import omega_automaton.Automaton;
import omega_automaton.AutomatonState;
import omega_automaton.acceptance.GeneralisedBuchiAcceptance;
import translations.ldba.AbstractInitialComponent;
import translations.ldba.LimitDeterministicAutomaton;
import ltl.Collections3;
import omega_automaton.acceptance.ParityAcceptance;
import omega_automaton.collections.valuationset.ValuationSet;
import omega_automaton.collections.valuationset.ValuationSetFactory;

import javax.annotation.Nonnull;
import java.util.*;

public class ParityAutomaton extends Automaton<ParityAutomaton.State, ParityAcceptance> {

    private final int colors;
    private final Automaton acceptingComponent;
    private final AbstractInitialComponent initialComponent;

    protected ParityAutomaton(LimitDeterministicAutomaton<?, ?, ? extends GeneralisedBuchiAcceptance, ?, ?> ldba) {
        super(ldba.getAcceptingComponent().valuationSetFactory);

        acceptingComponent = ldba.getAcceptingComponent();
        initialComponent = ldba.getInitialComponent();

        if (ldba.getAcceptingComponent().acceptance.getSize() > 1) {
            throw  new UnsupportedOperationException("Only Generalised Buchi with 1 acceptance condition accepted");
        }

        if (initialComponent != null) {
            colors = ldba.getAcceptingComponent().size();
            AutomatonState initial = initialComponent.getInitialState();
            initialState = new State(initial, new ArrayList(initialComponent.epsilonJumps.get(initial)));
        } else {
            colors = 2;
            initialState = new State(null, Collections.singletonList(acceptingComponent.getInitialState()));
        }

        acceptance = new ParityAcceptance(colors);
    }

    public final class State implements AutomatonState<State> {

        private final AutomatonState<?> initialComponentState;
        private final List<AutomatonState<?>> acceptingComponentRanking;

        public State(AutomatonState<?> state) {
            this(state, new ArrayList(initialComponent.epsilonJumps.get(state)));
        }

        public State(AutomatonState<?> state, List<AutomatonState<?>> ranking) {
            initialComponentState = state;
            acceptingComponentRanking = ranking;
        }

        public State getSuccessor(BitSet valuation) {
            if (initialComponentState == null) {
                AutomatonState state = Collections3.getElement(acceptingComponentRanking);
                AutomatonState successor = acceptingComponent.getSuccessor(state, valuation);
                if (successor == null) {
                    return null;
                }

                return new State(null, Collections.singletonList(successor));
            }

            AutomatonState<?> successor = initialComponent.getSuccessor(initialComponentState, valuation);

            if (successor == null) {
                return null;
            }

            Set seenStates = new HashSet<>();
            List ranking = new ArrayList<>(acceptingComponentRanking.size());
            ListIterator listIterator = acceptingComponentRanking.listIterator();

            while (listIterator.hasNext()) {
                AutomatonState state = (AutomatonState) listIterator.next();
                state = acceptingComponent.getSuccessor(state, valuation);

                if (state == null) {
                    continue;
                }

                if (seenStates.add(state)) {
                    ranking.add(state);
                }
            }

            for (Object target : initialComponent.epsilonJumps.get(successor)) {
                if (seenStates.add(target)) {
                    ranking.add(target);
                }
            }

            return new State(successor, ranking);
        }

        @Nonnull
        @Override
        public Map<BitSet, ValuationSet> getAcceptanceIndices() {
            Map<BitSet, ValuationSet> mapping = new HashMap<>();

            for (BitSet valuation : Collections3.powerSet(getSensitiveAlphabet())) {
                BitSet acc = new BitSet();
                acc.set(getColor(valuation));

                ValuationSet entry = mapping.get(acc);

                if (entry == null) {
                    mapping.put(acc, valuationSetFactory.createValuationSet(valuation));
                } else {
                    entry.add(valuation);
                }
            }

            return mapping;
        }

        public int getColor(BitSet valuation) {
            if (initialComponentState != null && initialComponentState.getSuccessor(valuation) == null) {
                return 2 * colors;
            }

            Set seenStates = new HashSet<>();
            ListIterator listIterator = acceptingComponentRanking.listIterator();

            while (listIterator.hasNext()) {
                int index = listIterator.nextIndex();
                AutomatonState state = acceptingComponent.getSuccessor((AutomatonState) listIterator.next(), valuation);

                if (state == null) {
                    return (2 * index);
                }

                if (acceptingComponent.hasAcceptanceIndex(state, 0, valuation)) {
                    return (2 * index) + 1;
                }

                if (!seenStates.add(state)) {
                    return (2 * index);
                }
            }

            return (initialComponent != null) ? 2 * colors : 2;
        }

        @Nonnull
        @Override
        public BitSet getSensitiveAlphabet() {
            BitSet a = new BitSet(valuationSetFactory.getSize());
            a.flip(0, valuationSetFactory.getSize());
            return a;
        }

        @Override
        public ValuationSetFactory getFactory() {
            return valuationSetFactory;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            State state = (State) o;
            return Objects.equals(initialComponentState, state.initialComponentState) &&
                    Objects.equals(acceptingComponentRanking, state.acceptingComponentRanking);
        }

        @Override
        public int hashCode() {
            return Objects.hash(initialComponentState, acceptingComponentRanking);
        }

        @Override
        public String toString() {
            return "{Init=" + initialComponentState + ", AccRanking=" + acceptingComponentRanking + '}';
        }
    }
}

