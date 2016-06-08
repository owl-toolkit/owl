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

package translations.nba2ldba;

import omega_automaton.StoredBuchiAutomaton;
import translations.ldba.AbstractInitialComponent;
import omega_automaton.AutomatonState;
import omega_automaton.collections.valuationset.ValuationSetFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class YCInit extends AbstractInitialComponent<YCInit.State, YCAcc.State> {

    private final StoredBuchiAutomaton nba;
    private final YCAcc acc;

    public YCInit(StoredBuchiAutomaton nba, YCAcc acceptingComponent) {
        super(nba.getFactory());
        this.nba = nba;
        acc = acceptingComponent;
        initialState = new State(Collections.singleton(nba.getInitialState()));
    }

    @Override
    public void generateJumps(State state) {
        Set<YCAcc.State> succ = epsilonJumps.get(state);

        for (StoredBuchiAutomaton.State s : state.states) {
            if (nba.isAccepting(s)) {
                YCAcc.State succ2 = acc.createState(s);
                succ.add(succ2);
                acc.generate(succ2);
            }
        }
    }

    public class State implements AutomatonState<State> {
        final Set<StoredBuchiAutomaton.State> states;

        State(Set<StoredBuchiAutomaton.State> s) {
            states = s;
        }

        @Nullable
        @Override
        public State getSuccessor(BitSet valuation) {
            Set<StoredBuchiAutomaton.State> successors = new HashSet<>();
            states.forEach(s -> successors.addAll(nba.getSuccessors(s, valuation)));

            if (successors.isEmpty()) {
                return null;
            }

            return new State(successors);
        }

        @Nonnull
        @Override
        public BitSet getSensitiveAlphabet() {
            BitSet bs = new BitSet();
            bs.set(0, valuationSetFactory.getSize());
            return bs;
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
            return Objects.equals(states, state.states);
        }

        @Override
        public int hashCode() {
            return Objects.hash(states);
        }

        @Override
        public String toString() {
            return "State{" + "states=" + states + '}';
        }
    }
}
