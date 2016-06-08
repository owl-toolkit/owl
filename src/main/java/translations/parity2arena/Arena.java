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

package translations.parity2arena;

import ltl.Collections3;
import omega_automaton.Automaton;
import omega_automaton.AutomatonState;
import omega_automaton.acceptance.ParityAcceptance;
import omega_automaton.collections.valuationset.ValuationSet;
import omega_automaton.collections.valuationset.ValuationSetFactory;
import translations.ldba2parity.ParityAutomaton;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class Arena extends Automaton<Arena.State, ParityAcceptance> {

    public enum Player {System, Environment}

    final ParityAutomaton automaton;
    final BitSet environmentAlphabet;
    final BitSet systemAlphabet;
    final Player firstPlayer;

    Arena(ParityAutomaton parityAutomaton, BitSet environmentAlphabet, Player firstPlayer) {
        super(parityAutomaton.valuationSetFactory);
        this.automaton = parityAutomaton;
        this.firstPlayer = firstPlayer;
        this.environmentAlphabet = environmentAlphabet;
        this.systemAlphabet = (BitSet) environmentAlphabet.clone();
        this.systemAlphabet.flip(0, valuationSetFactory.getSize());
        this.acceptance = parityAutomaton.acceptance;
    }

    @Override
    public State generateInitialState() {
        return new State(automaton.getInitialState(), null);
    }

    BitSet getFirstPlayerChoice(BitSet valuation) {
        BitSet choice = (firstPlayer == Player.Environment) ? environmentAlphabet : systemAlphabet;
        choice = (BitSet) choice.clone();
        choice.and(valuation);
        return choice;
    }

    BitSet getSecondPlayerChoice(BitSet valuation) {
        BitSet choice = (firstPlayer == Player.System) ? environmentAlphabet : systemAlphabet;
        choice = (BitSet) choice.clone();
        choice.and(valuation);
        return choice;
    }

    public class State implements AutomatonState<State> {

        ParityAutomaton.State state;
        BitSet choice;

        public State(ParityAutomaton.State state, BitSet choice) {
            this.state = state;
            this.choice = choice;
        }

        @Override
        public ValuationSetFactory getFactory() {
            return valuationSetFactory;
        }

        @Nullable
        @Override
        public State getSuccessor(BitSet valuation) {
            // If there is no state, we are stuck.
            if (choice == null && state == null) {
                return this;
            }

            // This state is controlled by the first player
            if (choice == null) {
                BitSet firstPlayerChoice = getFirstPlayerChoice(valuation);
                // Drop bits that do not influence the behaviour of the state.
                firstPlayerChoice.and(state.getSensitiveAlphabet());
                return new State(state, firstPlayerChoice);
            }

            // This state is controlled by the second player
            BitSet combinedChoice = getSecondPlayerChoice(valuation);
            combinedChoice.or(choice);
            ParityAutomaton.State successor = automaton.getSuccessor(state, combinedChoice);

            // If the firstPlayer is the environment, we can drop the transitions, since the system we would always lose.
            if (successor == null && firstPlayer == Player.Environment) {
                return null;
            }

            return new State(successor, null);
        }

        @Nonnull
        @Override
        public BitSet getSensitiveAlphabet() {
            return (choice == null ^ firstPlayer == Player.System) ? environmentAlphabet : systemAlphabet;
        }

        @Nullable
        public Map<BitSet,ValuationSet> getAcceptanceIndices() {
            if (choice == null) {
                return null;
            }

            Map<BitSet, ValuationSet> stateAcceptance = new HashMap<>();
            BitSet alphabet = firstPlayer == Player.Environment ? systemAlphabet : environmentAlphabet;

            for (BitSet valuation : Collections3.powerSet(alphabet)) {
                valuation.or(choice);
                BitSet indices = automaton.getAcceptanceIndices(state, valuation);

                ValuationSet vs = stateAcceptance.get(indices);

                if (vs == null) {
                    stateAcceptance.put(indices, valuationSetFactory.createValuationSet(valuation, alphabet));
                } else {
                    vs.addAll(valuationSetFactory.createValuationSet(valuation, alphabet));
                }
            }

            return stateAcceptance;
        }

        @Override
        public String toString() {
            if (firstPlayer == Player.Environment) {
                if (choice == null) {
                    return "[S=" + state + ']';
                } else {
                    return "(S=" + state + ", C=" + choice + ')';
                }
            } else {
                if (choice == null) {
                    return "(S=" + state + ')';
                } else {
                    return "[S=" + state + ", C=" + choice + ']';
                }
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            State state1 = (State) o;
            return Objects.equals(state, state1.state) &&
                    Objects.equals(choice, state1.choice);
        }

        @Override
        public int hashCode() {
            return Objects.hash(state, choice);
        }
    }
}
