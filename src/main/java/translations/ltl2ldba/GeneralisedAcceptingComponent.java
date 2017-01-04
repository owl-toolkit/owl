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

package translations.ltl2ldba;

import ltl.ImmutableObject;
import ltl.equivalence.EquivalenceClass;
import ltl.equivalence.EquivalenceClassFactory;
import ltl.visitors.predicates.XFragmentPredicate;
import omega_automaton.AutomatonState;
import omega_automaton.acceptance.BuchiAcceptance;
import omega_automaton.acceptance.GeneralisedBuchiAcceptance;
import omega_automaton.collections.valuationset.ValuationSetFactory;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import translations.Optimisation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.Objects;

public class GeneralisedAcceptingComponent extends AbstractAcceptingComponent<GeneralisedAcceptingComponent.State, GeneralisedBuchiAcceptance, RecurringObligations> {

    private final BitSet ACCEPT = new BitSet();

    GeneralisedAcceptingComponent(EquivalenceClassFactory factory, ValuationSetFactory valuationSetFactory, EnumSet<Optimisation> optimisations) {
        super(new BuchiAcceptance(), optimisations, valuationSetFactory, factory);
        ACCEPT.set(0, 1);
    }

    @Override
    public State createState(EquivalenceClass remainder, RecurringObligations obligations) {
        final int length = obligations.obligations.length + obligations.liveness.length;

        // If it is necessary, increase the number of acceptance conditions.
        if (length > acceptance.getSize()) {
            acceptance = new GeneralisedBuchiAcceptance(length);
            ACCEPT.set(0, length);
        }

        EquivalenceClass safety = obligations.safety;
        EquivalenceClass[] currentBuilder = new EquivalenceClass[length];

        if (remainder.testSupport(XFragmentPredicate.INSTANCE)) {
            safety = remainder.andWith(safety);
            remainder = equivalenceClassFactory.getTrue();
        }

        if (length == 0) {
            if (remainder.isTrue()) {
                return new State(obligations, safety, EMPTY, EMPTY);
            } else {
                return new State(obligations, safety, new EquivalenceClass[]{remainder}, EMPTY);
            }
        }

        if (obligations.obligations.length > 0) {
            currentBuilder[0] = getInitialClass(remainder.andWith(obligations.obligations[0]));
        } else {
            currentBuilder[0] = getInitialClass(remainder.andWith(obligations.liveness[0]));
        }

        for (int i = 1; i < obligations.obligations.length; i++) {
            currentBuilder[i] = getInitialClass(obligations.obligations[i]);
        }

        for (int i = Math.max(1, obligations.obligations.length); i < length; i++) {
            currentBuilder[i] = getInitialClass(obligations.liveness[i - obligations.obligations.length]);
        }

        EquivalenceClass[] next = new EquivalenceClass[obligations.obligations.length];
        Arrays.fill(next, equivalenceClassFactory.getTrue());

        return new State(obligations, safety, currentBuilder, next);
    }

    public final class State extends ImmutableObject implements AutomatonState<GeneralisedAcceptingComponent.State> {

        private final RecurringObligations obligations;
        private final EquivalenceClass safety;
        private final EquivalenceClass[] current;
        private final EquivalenceClass[] next;

        private State(RecurringObligations obligations, EquivalenceClass safety, EquivalenceClass[] current, EquivalenceClass[] next) {
            this.obligations = obligations;
            this.safety = safety;
            this.current = current;
            this.next = next;
        }

        private Edge<State> getSuccessorPureSafety(EquivalenceClass nextSafety, BitSet valuation) {
            // Take care of the remainder.
            if (current.length > 0) {
                EquivalenceClass remainder = GeneralisedAcceptingComponent.this.getSuccessor(current[0], valuation, nextSafety);

                if (remainder.isFalse()) {
                    return null;
                }

                if (!remainder.isTrue()) {
                    return Edges.create(new State(obligations, nextSafety, new EquivalenceClass[]{remainder}, EMPTY));
                }
            }

            return Edges.create(new State(obligations, nextSafety, EMPTY, EMPTY), ACCEPT);
        }

        @Nullable
        public Edge<State> getSuccessor(@Nonnull BitSet valuation) {
            // Check the safety field first.
            EquivalenceClass nextSafety = GeneralisedAcceptingComponent.this.getSuccessor(safety, valuation).andWith(obligations.safety);

            if (nextSafety.isFalse()) {
                return null;
            }

            if (obligations.isPureSafety()) {
                return getSuccessorPureSafety(nextSafety, valuation);
            }

            final int length = current.length;
            EquivalenceClass[] currentSuccessors = new EquivalenceClass[length];
            EquivalenceClass[] nextSuccessors = new EquivalenceClass[next.length];

            // Create acceptance set and set all unused indices to 1.
            BitSet bs = new BitSet();
            bs.set(length, acceptance.getSize());

            for (int i = 0; i < next.length; i++) {
                EquivalenceClass currentSuccessor = GeneralisedAcceptingComponent.this.getSuccessor(current[i], valuation, nextSafety);

                if (currentSuccessor.isFalse()) {
                    return null;
                }

                EquivalenceClass assumptions = currentSuccessor.and(nextSafety);
                EquivalenceClass nextSuccessor = GeneralisedAcceptingComponent.this.getSuccessor(next[i], valuation, assumptions);

                if (nextSuccessor.isFalse()) {
                    EquivalenceClass.free(currentSuccessors);
                    EquivalenceClass.free(nextSuccessors);
                    assumptions.free();
                    return null;
                }

                nextSuccessor = nextSuccessor.andWith(getInitialClass(obligations.obligations[i], assumptions));

                // Successor is done and we can switch components.
                if (currentSuccessor.isTrue()) {
                    bs.set(i);
                    currentSuccessors[i] = nextSuccessor;
                    nextSuccessors[i] = equivalenceClassFactory.getTrue();
                } else {
                    currentSuccessors[i] = currentSuccessor;
                    nextSuccessors[i] = nextSuccessor;
                }

                assumptions.free();
            }

            for (int i = next.length; i < length; i++) {
                EquivalenceClass currentSuccessor = GeneralisedAcceptingComponent.this.getSuccessor(current[i], valuation, nextSafety);

                if (currentSuccessor.isFalse()) {
                    return null;
                }

                if (currentSuccessor.isTrue()) {
                    bs.set(i);
                    currentSuccessors[i] = getInitialClass(obligations.liveness[i - next.length]);
                } else {
                    currentSuccessors[i] = currentSuccessor;
                }
            }

            return Edges.create(new State(obligations, nextSafety, currentSuccessors, nextSuccessors), bs);
        }

        private BitSet sensitiveAlphabet;

        @Nonnull
        public BitSet getSensitiveAlphabet() {
            if (sensitiveAlphabet == null) {
                sensitiveAlphabet = GeneralisedAcceptingComponent.this.getSensitiveAlphabet(safety);

                for (EquivalenceClass clazz : current) {
                    sensitiveAlphabet.or(GeneralisedAcceptingComponent.this.getSensitiveAlphabet(clazz));
                }

                for (EquivalenceClass clazz : next) {
                    sensitiveAlphabet.or(GeneralisedAcceptingComponent.this.getSensitiveAlphabet(clazz));
                }
            }

            return (BitSet) sensitiveAlphabet.clone();
        }

        @Override
        protected int hashCodeOnce() {
            return Objects.hash(safety, obligations, Arrays.hashCode(current), Arrays.hashCode(next));
        }

        @Override
        protected boolean equals2(ImmutableObject o) {
            State that = (State) o;
            return Objects.equals(safety, that.safety) && Objects.equals(obligations, that.obligations) && Arrays.equals(current, that.current) && Arrays.equals(next, that.next);
        }

        @Override
        public void free() {
            // current.forEach(EquivalenceClass::free);
            // next.forEach(EquivalenceClass::free);
        }

        @Override
        public String toString() {
            return "[obligations=" + obligations +
                    ", safety=" + safety +
                    ", current=" + Arrays.toString(current) +
                    ", next=" + Arrays.toString(next) +
                    ']';
        }
    }
}
