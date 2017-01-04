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

package translations.ltl2ldba.ng;

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
import translations.ltl2ldba.AbstractAcceptingComponent;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class QuantAcceptingComponent extends AbstractAcceptingComponent<QuantAcceptingComponent.State, BuchiAcceptance, RecurringObligations2> {

    QuantAcceptingComponent(EquivalenceClassFactory factory, ValuationSetFactory valuationSetFactory, EnumSet<Optimisation> optimisations) {
        super(new BuchiAcceptance(), optimisations, valuationSetFactory, factory);
    }

    @Nonnull
    @Override
    public State generateRejectingTrap() {
        return new State(0, equivalenceClassFactory.getFalse(), equivalenceClassFactory.getFalse(), new RecurringObligations2(equivalenceClassFactory.getTrue()));
    }

    @Override
    protected State createState(EquivalenceClass remainder, RecurringObligations2 obligations) {
        EquivalenceClass safety = obligations.safety;
        EquivalenceClass liveness = remainder;

        // TODO: Scoped vs. un-scoped check?
        if (remainder.testSupport(XFragmentPredicate.INSTANCE)) {
            safety = liveness.andWith(safety);
            liveness = equivalenceClassFactory.getTrue();
        }

        if (liveness.isTrue() && obligations.liveness.length > 0) {
            liveness = getInitialClass(obligations.liveness[0], safety);
        }

        return new State(0, safety, liveness, obligations);
    }

    public final class State extends ImmutableObject implements AutomatonState<State> {

        private final RecurringObligations2 obligations;

        @Nonnegative // Index of the current checked liveness (F) obligation.
        private final int index;
        private final EquivalenceClass liveness;
        private final EquivalenceClass safety;

        private State(@Nonnegative int index, EquivalenceClass safety, EquivalenceClass liveness, RecurringObligations2 obligations) {
            assert 0 <= index && index < obligations.liveness.length;

            this.index = index;
            this.liveness = liveness;
            this.obligations = obligations;
            this.safety = safety;
        }

        private BitSet sensitiveAlphabet;

        @Nonnull
        @Override
        public BitSet getSensitiveAlphabet() {
            if (sensitiveAlphabet == null) {
                sensitiveAlphabet = QuantAcceptingComponent.this.getSensitiveAlphabet(liveness);
                sensitiveAlphabet.or(QuantAcceptingComponent.this.getSensitiveAlphabet(safety));

                for (EquivalenceClass clazz : obligations.liveness) {
                    sensitiveAlphabet.or(QuantAcceptingComponent.this.getSensitiveAlphabet(getInitialClass(clazz)));
                }
            }

            return (BitSet) sensitiveAlphabet.clone();
        }

        @Nonnegative
        private int scan(@Nonnegative int i, BitSet valuation, EquivalenceClass environment) {
            final int livenessLength = obligations.liveness.length;

            while (i < livenessLength) {
                EquivalenceClass successor = QuantAcceptingComponent.this.getSuccessor(
                        QuantAcceptingComponent.this.getInitialClass(obligations.liveness[i]),
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

        @Nullable
        public Edge<State> getSuccessor(@Nonnull BitSet valuation) {
            EquivalenceClass safetySuccessor = QuantAcceptingComponent.this.getSuccessor(safety, valuation).andWith(obligations.safety);

            if (safetySuccessor.isFalse()) {
                return null;
            }

            EquivalenceClass livenessSuccessor = QuantAcceptingComponent.this.getSuccessor(liveness, valuation, safetySuccessor);

            if (livenessSuccessor.isFalse()) {
                EquivalenceClass.free(safetySuccessor);
                return null;
            }

            EquivalenceClass assumptions = livenessSuccessor.and(safetySuccessor);

            if (assumptions.isFalse()) {
                EquivalenceClass.free(safetySuccessor, livenessSuccessor);
                return null;
            }

            final int livenessLength = obligations.liveness.length;

            boolean acceptingEdge = false;
            boolean obtainNewGoal = false;
            int j;

            // Scan for new index if currentSuccessor currentSuccessor is true.
            // In this way we can skip several fullfilled break-points at a time and are not bound to slowly check one by one.
            if (livenessSuccessor.isTrue()) {
                obtainNewGoal = true;
                j = scan(index + 1, valuation, assumptions);

                if (j >= livenessLength) {
                    acceptingEdge = true;
                    j = scan(0, valuation, assumptions);

                    if (j >= livenessLength) {
                        j = 0;
                    }
                }
            } else {
                j = index;
            }

            if (obtainNewGoal && j < obligations.liveness.length) {
                livenessSuccessor = getInitialClass(obligations.liveness[j]);
            }

            if (livenessSuccessor.isFalse()) {
                EquivalenceClass.free(safetySuccessor, livenessSuccessor, assumptions);
                return null;
            }

            assumptions.free();

            State successor = new State(j, safetySuccessor, livenessSuccessor, obligations);
            return acceptingEdge ? Edges.create(successor, 0) : Edges.create(successor);
        }

        @Override
        protected int hashCodeOnce() {
            return Objects.hash(liveness, obligations, safety, index);
        }

        @Override
        protected boolean equals2(ImmutableObject o) {
            State that = (State) o;
            return index == that.index && Objects.equals(safety, that.safety) && Objects.equals(liveness, that.liveness) && Objects.equals(obligations, that.obligations);
        }

        @Override
        public String toString() {
            return "[obligations=" + obligations +
                    (!safety.isTrue() ? ", safety=" + safety : "") +
                    (index != 0 ? ", index=" + index : "") +
                    (!liveness.isTrue() ? ", current-liveness=" + liveness : "") +
                    ']';
        }
    }
}
