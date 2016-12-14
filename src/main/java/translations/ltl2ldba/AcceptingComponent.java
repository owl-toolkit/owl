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

import com.google.common.base.Preconditions;
import ltl.ImmutableObject;
import ltl.equivalence.EquivalenceClass;
import ltl.equivalence.EquivalenceClassFactory;
import ltl.visitors.predicates.XFragmentPredicate;
import omega_automaton.AutomatonState;
import omega_automaton.Edge;
import omega_automaton.acceptance.BuchiAcceptance;
import omega_automaton.collections.valuationset.ValuationSetFactory;
import translations.Optimisation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class AcceptingComponent extends AbstractAcceptingComponent<AcceptingComponent.State, BuchiAcceptance> {

    private static final BitSet ACCEPT = new BitSet();

    static {
        ACCEPT.set(0);
    }

    AcceptingComponent(EquivalenceClassFactory factory, ValuationSetFactory valuationSetFactory, EnumSet<Optimisation> optimisations) {
        super(new BuchiAcceptance(), optimisations, valuationSetFactory, factory);
    }

    @Nonnull
    @Override
    public State generateRejectingTrap() {
        return new State(0, null, null, null, new RecurringObligations(equivalenceClassFactory.getTrue(), Collections.emptyList(), Collections.emptyList()));
    }

    private EquivalenceClass and(EquivalenceClass[] classes) {
        EquivalenceClass conjunction = equivalenceClassFactory.getTrue();

        for (EquivalenceClass clazz : classes) {
            conjunction = conjunction.andWith(clazz);
        }

        return conjunction;
    }

    @Override
    State createState(EquivalenceClass remainder, RecurringObligations obligations) {
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
            return new State(0, safety, doEagerOpt(removeCover(current, environment)), EMPTY, obligations);
        }

        EquivalenceClass[] nextBuilder = new EquivalenceClass[obligations.obligations.length];

        if (current.isTrue()) {
            if (obligations.obligations.length > 0) {
                nextBuilder[0] = current;
                current = removeCover(doEagerOpt(obligations.obligations[0]), environment);
            } else {
                current = doEagerOpt(obligations.liveness[0]);
            }
        }

        for (int i = current.isTrue() ? 1 : 0; i < nextBuilder.length; i++) {
            nextBuilder[i] = removeCover(doEagerOpt(obligations.obligations[i]), current);
        }

        // Drop unused representative.
        safety.freeRepresentative();
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
                sensitiveAlphabet = AcceptingComponent.this.getSensitiveAlphabet(current);
                sensitiveAlphabet.or(AcceptingComponent.this.getSensitiveAlphabet(safety));

                for (EquivalenceClass clazz : next) {
                    sensitiveAlphabet.or(AcceptingComponent.this.getSensitiveAlphabet(clazz));
                }

                for (EquivalenceClass clazz : obligations.liveness) {
                    sensitiveAlphabet.or(AcceptingComponent.this.getSensitiveAlphabet(doEagerOpt(clazz)));
                }
            }

            return (BitSet) sensitiveAlphabet.clone();
        }

        @Nullable
        public Edge<State> getSuccessor(BitSet valuation) {
            EquivalenceClass safetySuccessor = AcceptingComponent.this.getSuccessor(safety, valuation).andWith(obligations.safety);

            if (safetySuccessor.isFalse()) {
                return null;
            }

            EquivalenceClass currentSuccessor = AcceptingComponent.this.getSuccessor(current, valuation, safetySuccessor);

            if (currentSuccessor.isFalse()) {
                EquivalenceClass.free(safetySuccessor);
                return null;
            }

            EquivalenceClass assumptions = currentSuccessor.and(safetySuccessor);

            if (assumptions.isFalse()) {
                EquivalenceClass.free(safetySuccessor, currentSuccessor);
                return null;
            }

            EquivalenceClass[] nextSuccessors = AcceptingComponent.this.getSuccessors(next, valuation, assumptions);

            if (nextSuccessors == null) {
                EquivalenceClass.free(safetySuccessor, currentSuccessor, assumptions);
                return null;
            }

            final int obligationsLength = obligations.obligations.length;
            final int livenessLength = obligations.liveness.length;

            BitSet bs = REJECT;

            boolean obtainNewGoal = false;
            int j = index;

            // Scan for new index if currentSuccessor currentSuccessor is true.
            // In this way we can skip several fullfilled break-points at a time and are not bound to slowly check one by one.
            if (currentSuccessor.isTrue()) {
                obtainNewGoal = true;
                int i = index + 1;

                while (0 <= i && i < obligationsLength && nextSuccessors[i].isTrue()) {
                    i++;
                }

                // Wrap around to the liveness obligations.
                if (i >= obligationsLength) {
                    i = -livenessLength;
                }

                while (i < 0) {
                    currentSuccessor = AcceptingComponent.this.getSuccessor(
                            AcceptingComponent.this.getInitialClass(obligations.liveness[livenessLength + i]),
                            valuation,
                            assumptions);

                    if (currentSuccessor.isTrue()) {
                        i++;
                    } else {
                        break;
                    }
                }

                if (i == 0) {
                    bs = ACCEPT;

                    // Continue scanning
                    for (i = 0; i < obligationsLength && nextSuccessors[i].isTrue();) {
                        i++;
                    }
                }

                if (i == obligationsLength) {
                    if (obligationsLength == 0) {
                        j = -livenessLength;
                    } else {
                        j = 0;
                    }
                } else {
                    j = i;
                }
            }

            for (int i = 0; i < nextSuccessors.length; i++) {
                EquivalenceClass nextSuccessor = nextSuccessors[i];

                if (obtainNewGoal && i == j) {
                    currentSuccessor = nextSuccessor.and(removeCover(doEagerOpt(obligations.obligations[i]), assumptions));
                    assumptions = assumptions.and(currentSuccessor);
                    nextSuccessors[i] = equivalenceClassFactory.getTrue();
                } else {
                    nextSuccessors[i] = nextSuccessor.and(removeCover(doEagerOpt(obligations.obligations[i]), assumptions));
                }
            }

            if (obtainNewGoal && j < 0) {
                currentSuccessor = (doEagerOpt(obligations.liveness[livenessLength + j]));
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

            return new Edge<>(new State(j, safetySuccessor, currentSuccessor, nextSuccessors, obligations), bs);
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
            return current;
        }

        private EquivalenceClass label = null;

        public EquivalenceClass getLabel() {
            if (label == null) {
                label = current.and(safety);

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
            final StringBuilder sb = new StringBuilder("State{");
            sb.append("obligations=").append(obligations);
            sb.append(", safety=").append(safety);
            sb.append(", index=").append(index);
            sb.append(", current=").append(current);
            sb.append(", next=").append(Arrays.toString(next));
            sb.append(", sensitiveAlphabet=").append(sensitiveAlphabet);
            sb.append('}');
            return sb.toString();
        }
    }
}
