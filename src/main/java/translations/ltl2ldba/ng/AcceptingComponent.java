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
import java.util.BitSet;
import java.util.EnumSet;
import java.util.Objects;

public class AcceptingComponent extends AbstractAcceptingComponent<AcceptingComponent.State, BuchiAcceptance, RecurringObligations2> {

    AcceptingComponent(EquivalenceClassFactory factory, ValuationSetFactory valuationSetFactory, EnumSet<Optimisation> optimisations) {
        super(new BuchiAcceptance(), optimisations, valuationSetFactory, factory);
    }

    @Override
    protected State createState(EquivalenceClass remainder, RecurringObligations2 obligations) {
        EquivalenceClass safety = remainder.andWith(obligations.safety);
        EquivalenceClass liveness;

        if (obligations.liveness.length > 0) {
            liveness = factory.getInitial(obligations.liveness[0]);
        } else {
            liveness = equivalenceClassFactory.getTrue();
        }

        return new State(0, factory.getInitial(safety, liveness), liveness, obligations);
    }

    public final class State extends ImmutableObject implements AutomatonState<State> {

        private final RecurringObligations2 obligations;

        @Nonnegative // Index of the current checked liveness (F) obligation.
        private final int index;
        private final EquivalenceClass liveness;
        private final EquivalenceClass safety;

        private State(@Nonnegative int index, EquivalenceClass safety, EquivalenceClass liveness, RecurringObligations2 obligations) {
            assert 0 == index || (0 < index && index < obligations.liveness.length);

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
                sensitiveAlphabet = factory.getSensitiveAlphabet(liveness);
                sensitiveAlphabet.or(factory.getSensitiveAlphabet(safety));

                sensitiveAlphabet.or(factory.getSensitiveAlphabet(obligations.safety));

                for (EquivalenceClass clazz : obligations.liveness) {
                    sensitiveAlphabet.or(factory.getSensitiveAlphabet(factory.getInitial(clazz)));
                }
            }

            return (BitSet) sensitiveAlphabet.clone();
        }

        @Nonnegative
        private int scan(@Nonnegative int i, BitSet valuation) {
            final int livenessLength = obligations.liveness.length;

            while (i < livenessLength) {
                EquivalenceClass successor = factory.getSuccessor(factory.getInitial(obligations.liveness[i]), valuation);

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
            EquivalenceClass livenessSuccessor = factory.getSuccessor(liveness, valuation);
            EquivalenceClass safetySuccessor = factory.getSuccessor(safety, valuation, livenessSuccessor)
                    .andWith(factory.getInitial(obligations.safety));

            if (safetySuccessor.isFalse()) {
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
                j = scan(index + 1, valuation);

                if (j >= livenessLength) {
                    acceptingEdge = true;
                    j = scan(0, valuation);

                    if (j >= livenessLength) {
                        j = 0;
                    }
                }
            } else {
                j = index;
            }

            if (obtainNewGoal && j < obligations.liveness.length) {
                livenessSuccessor = factory.getInitial(obligations.liveness[j]);
            }

            assert !livenessSuccessor.isFalse() : "Liveness property cannot be false.";

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
