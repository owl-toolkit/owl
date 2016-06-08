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

import com.google.common.collect.ImmutableSet;
import omega_automaton.Automaton;
import omega_automaton.AutomatonState;
import omega_automaton.StoredBuchiAutomaton;
import omega_automaton.acceptance.BuchiAcceptance;
import omega_automaton.collections.valuationset.ValuationSet;
import omega_automaton.collections.valuationset.ValuationSetFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * Created by sickert on 01/06/16.
 */
public class YCAcc extends Automaton<YCAcc.State, BuchiAcceptance> {

    private StoredBuchiAutomaton nba;

    protected YCAcc(StoredBuchiAutomaton nba) {
        super(nba.getFactory());
        this.nba = nba;
        acceptance = new BuchiAcceptance();
    }

    public State createState(StoredBuchiAutomaton.State target) {
        return new State(Collections.singleton(target), Collections.singleton(target));
    }

    public class State implements AutomatonState<State> {
        private final Set<StoredBuchiAutomaton.State> left;
        private final Set<StoredBuchiAutomaton.State> right;

        State(Set<StoredBuchiAutomaton.State> l, Set<StoredBuchiAutomaton.State> r) {
            left = l;
            right = r;
        }

        @Nullable
        @Override
        public State getSuccessor(BitSet valuation) {
            // Standard Subset Construction
            Set<StoredBuchiAutomaton.State> rightSuccessor = new HashSet<>();

            for (StoredBuchiAutomaton.State rightState : right) {
                rightSuccessor.addAll(nba.getSuccessors(rightState, valuation));
            }

            Set<StoredBuchiAutomaton.State> leftSuccessor = new HashSet<>();

            // Add all states reached from an accepting state
            right.stream().filter(nba::isAccepting).forEach(s -> leftSuccessor.addAll(nba.getSuccessors(s, valuation)));

            if (!left.equals(right)) {
                left.forEach(s -> leftSuccessor.addAll(nba.getSuccessors(s, valuation)));
            }

            // Don't construct the trap state.
            if (leftSuccessor.isEmpty() && rightSuccessor.isEmpty()) {
                return null;
            }

            return new State(ImmutableSet.copyOf(leftSuccessor), ImmutableSet.copyOf(rightSuccessor));
        }

        @Nullable
        public Map<BitSet, ValuationSet> getAcceptanceIndices() {
            BitSet bs = new BitSet(1);
            bs.set(0, left.stream().anyMatch(nba::isAccepting) && left.equals(right));
            return Collections.singletonMap(bs, valuationSetFactory.createUniverseValuationSet());
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
            return Objects.equals(left, state.left) &&
                    Objects.equals(right, state.right);
        }

        @Override
        public int hashCode() {
            return Objects.hash(left, right);
        }

        @Override
        public String toString() {
            return "State{" + "left=" + left + ", right=" + right + '}';
        }
    }
}
