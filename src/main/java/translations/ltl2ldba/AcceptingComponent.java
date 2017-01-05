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
import omega_automaton.collections.valuationset.ValuationSetFactory;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import translations.Optimisation;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class AcceptingComponent extends AbstractAcceptingComponent<AcceptingComponent.State, BuchiAcceptance, RecurringObligations> {

    AcceptingComponent(EquivalenceClassFactory factory, ValuationSetFactory valuationSetFactory, EnumSet<Optimisation> optimisations) {
        super(new BuchiAcceptance(), optimisations, valuationSetFactory, factory);
    }

    @Nonnull
    @Override
    public State generateRejectingTrap() {
        return new State(0, equivalenceClassFactory.getFalse(), equivalenceClassFactory.getFalse(), EMPTY, new RecurringObligations(equivalenceClassFactory.getTrue(), Collections.emptyList(), Collections.emptyList()));
    }

    private EquivalenceClass and(EquivalenceClass[] classes) {
        EquivalenceClass conjunction = equivalenceClassFactory.getTrue();

        for (EquivalenceClass clazz : classes) {
            conjunction = conjunction.andWith(clazz);
        }

        return conjunction;
    }

    @Override
    public State createState(EquivalenceClass remainder, RecurringObligations obligations) {
        final int length = obligations.obligations.length + obligations.liveness.length;

        // TODO: field for extra data.

        EquivalenceClass safety = obligations.safety;
        EquivalenceClass current = remainder;

        if (remainder.testSupport(XFragmentPredicate.INSTANCE)) {
            safety = current.andWith(safety);
            current = equivalenceClassFactory.getTrue();
        }

        EquivalenceClass environment = safety.and(and(obligations.liveness));

        if (length == 0) {
            return new State(0, safety, stateFactory.getInitial(current, environment), EMPTY, obligations);
        }

        EquivalenceClass[] nextBuilder = new EquivalenceClass[obligations.obligations.length];

        if (current.isTrue()) {
            if (obligations.obligations.length > 0) {
                nextBuilder[0] = current;
                current = stateFactory.getInitial(obligations.obligations[0], environment);
            } else {
                current = stateFactory.getInitial(obligations.liveness[0]);
            }
        }

        for (int i = current.isTrue() ? 1 : 0; i < nextBuilder.length; i++) {
            nextBuilder[i] = stateFactory.getInitial(obligations.obligations[i], current);
        }

        // Drop unused representative.
        current.freeRepresentative();

        return new State(obligations.obligations.length > 0 ? 0 : -obligations.liveness.length, safety, current, nextBuilder, obligations);
    }

    public final class State extends ImmutableObject implements AutomatonState<State> {

        private final RecurringObligations obligations;

        // Index of the current checked obligation. A negative index means a liveness obligation is checked.
        private final int index;
        private final EquivalenceClass current;
        private final EquivalenceClass[] next;
        private final EquivalenceClass safety;

        private State(int index, EquivalenceClass safety, EquivalenceClass current, EquivalenceClass[] next, RecurringObligations obligations) {
            assert (obligations.isPureSafety() && index == 0) || (-obligations.liveness.length <= index && index < obligations.obligations.length);

            this.index = index;
            this.current = current;
            this.obligations = obligations;
            this.safety = safety;
            this.next = next;
        }

        private BitSet sensitiveAlphabet;

        @Nonnull
        @Override
        public BitSet getSensitiveAlphabet() {
            if (sensitiveAlphabet == null) {
                sensitiveAlphabet = stateFactory.getSensitiveAlphabet(current);
                sensitiveAlphabet.or(stateFactory.getSensitiveAlphabet(safety));

                for (EquivalenceClass clazz : next) {
                    sensitiveAlphabet.or(stateFactory.getSensitiveAlphabet(clazz));
                }

                for (EquivalenceClass clazz : obligations.liveness) {
                    sensitiveAlphabet.or(stateFactory.getSensitiveAlphabet(stateFactory.getInitial(clazz)));
                }
            }

            return (BitSet) sensitiveAlphabet.clone();
        }

        @Nonnegative
        private int scanObligations(@Nonnegative int i, EquivalenceClass[] obligations) {
            final int obligationsLength = obligations.length;

            while (i < obligationsLength && obligations[i].isTrue()) {
                i++;
            }

            return i;
        }

        private int scanLiveness(int i, BitSet valuation, EquivalenceClass environment) {
            final int livenessLength = obligations.liveness.length;

            while (i < 0) {
                EquivalenceClass successor = stateFactory.getSuccessor(
                        stateFactory.getInitial(obligations.liveness[livenessLength + i]),
                        valuation,
                        environment);

                if (successor.isTrue()) {
                    i++;
                } else {
                    break;
                }
            }

            return i;
        }

        private int scan(int i, EquivalenceClass[] obligations, BitSet valuation, EquivalenceClass environment) {
            if (i < 0) {
                i = scanLiveness(i, valuation, environment);
            }

            if (0 <= i) {
                i = scanObligations(i, obligations);
            }

            return i;
        }

        @Nullable
        public Edge<State> getSuccessor(@Nonnull BitSet valuation) {
            EquivalenceClass safetySuccessor = stateFactory.getSuccessor(safety, valuation).andWith(obligations.safety);

            if (safetySuccessor.isFalse()) {
                return null;
            }

            EquivalenceClass currentSuccessor = stateFactory.getSuccessor(current, valuation, safetySuccessor);

            if (currentSuccessor.isFalse()) {
                EquivalenceClass.free(safetySuccessor);
                return null;
            }

            EquivalenceClass assumptions = currentSuccessor.and(safetySuccessor);

            if (assumptions.isFalse()) {
                EquivalenceClass.free(safetySuccessor, currentSuccessor);
                return null;
            }

            EquivalenceClass[] nextSuccessors = stateFactory.getSuccessors(next, valuation, assumptions);

            if (nextSuccessors == null) {
                EquivalenceClass.free(safetySuccessor, currentSuccessor, assumptions);
                return null;
            }

            final int obligationsLength = obligations.obligations.length;
            final int livenessLength = obligations.liveness.length;

            boolean acceptingEdge = false;

            boolean obtainNewGoal = false;
            int j;

            // Scan for new index if currentSuccessor currentSuccessor is true.
            // In this way we can skip several fullfilled break-points at a time and are not bound to slowly check one by one.
            if (currentSuccessor.isTrue()) {
                obtainNewGoal = true;
                j = scan(index + 1, nextSuccessors, valuation, assumptions);

                if (j >= obligationsLength) {
                    acceptingEdge = true;
                    j = scan(-livenessLength, nextSuccessors, valuation, assumptions);

                    if (j >= obligationsLength) {
                        j = -livenessLength;
                    }
                }
            } else {
                j = index;
            }

            for (int i = 0; i < nextSuccessors.length; i++) {
                EquivalenceClass nextSuccessor = nextSuccessors[i];

                if (obtainNewGoal && i == j) {
                    currentSuccessor = nextSuccessor.and(stateFactory.getInitial(obligations.obligations[i], assumptions));
                    assumptions = assumptions.and(currentSuccessor);
                    nextSuccessors[i] = equivalenceClassFactory.getTrue();
                } else {
                    nextSuccessors[i] = nextSuccessor.and(stateFactory.getInitial(obligations.obligations[i], assumptions));
                }
            }

            if (obtainNewGoal && j < 0) {
                currentSuccessor = stateFactory.getInitial(obligations.liveness[livenessLength + j]);
            }

            if (currentSuccessor.isFalse()) {
                EquivalenceClass.free(safetySuccessor, currentSuccessor, assumptions);
                EquivalenceClass.free(nextSuccessors);
                return null;
            }

            for (EquivalenceClass clazz : nextSuccessors) {
                if (clazz.isFalse()) {
                    EquivalenceClass.free(safetySuccessor, currentSuccessor, assumptions);
                    EquivalenceClass.free(nextSuccessors);
                    return null;
                }
            }

            assumptions.free();

            State successor = new State(j, safetySuccessor, currentSuccessor, nextSuccessors, obligations);
            return acceptingEdge ? Edges.create(successor, 0) : Edges.create(successor);
        }

        @Override
        protected int hashCodeOnce() {
            return Objects.hash(current, obligations, safety, index, Arrays.hashCode(next));
        }

        @Override
        protected boolean equals2(ImmutableObject o) {
            State that = (State) o;
            return index == that.index && Objects.equals(safety, that.safety) && Objects.equals(current, that.current) && Arrays.equals(next, that.next) && Objects.equals(obligations, that.obligations);
        }

        @Override
        public void free() {
            // current.free();
            // next.forEach(EquivalenceClass::free);
        }

        public RecurringObligations getObligations() {
            return obligations;
        }

        public EquivalenceClass getCurrent() {
            return index < 0 ? equivalenceClassFactory.getTrue() : current;
        }

        private EquivalenceClass label = null;

        public EquivalenceClass getLabel() {
            if (label == null) {
                label = safety.and(current);

                for (EquivalenceClass clazz : next) {
                    label = label.andWith(clazz);
                }

                for (EquivalenceClass clazz : obligations.obligations) {
                    label = label.andWith(clazz);
                }

                for (EquivalenceClass clazz : obligations.liveness) {
                    label = label.andWith(clazz);
                }
            }

            return label;
        }

        @Override
        public String toString() {
            return "[obligations=" + obligations +
                    (!safety.isTrue() ? ", safety=" + safety : "") +
                    (index != 0 ? ", index=" + index : "") +
                    (!current.isTrue() ? ", current=" + current : "") +
                    (next.length > 0 ? ", next=" + Arrays.toString(next) : "") +
                    ']';
        }
    }
}
