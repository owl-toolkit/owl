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
import omega_automaton.Edge;
import omega_automaton.acceptance.BuchiAcceptance;
import omega_automaton.collections.valuationset.ValuationSetFactory;
import translations.Optimisation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.Objects;

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
        return new State(0, null, null, null, null);
    }

    @Override
    State createState(EquivalenceClass remainder, RecurringObligations obligations) {
        EquivalenceClass xFragment = obligations.xFragment;
        EquivalenceClass current = equivalenceClassFactory.getTrue();

        final int length = obligations.initialStates.length;
        EquivalenceClass[] nextBuilder = new EquivalenceClass[length];

        if (remainder.getRepresentative().accept(XFragmentPredicate.INSTANCE)) {
            xFragment = remainder.andWith(xFragment);

            if (length > 0) {
                nextBuilder[0] = current;
                current = doEagerOpt(obligations.initialStates[0]);
            }
        } else {
            current = doEagerOpt(remainder);

            if (length > 0) {
                nextBuilder[0] = removeCover(doEagerOpt(obligations.initialStates[0]), current);
            }
        }

        for (int i = 1; i < length; i++) {
            nextBuilder[i] = removeCover(doEagerOpt(obligations.initialStates[i]), current);
        }

        return new State(0, xFragment, current, nextBuilder, obligations);
    }

    public final class State extends ImmutableObject implements AutomatonState<State> {

        private final RecurringObligations obligations;
        private final EquivalenceClass xFragment;
        private final int index;
        private final EquivalenceClass current;
        private final EquivalenceClass[] next;

        private State(int index, EquivalenceClass xFragment, EquivalenceClass current, EquivalenceClass[] next, RecurringObligations obligations) {
            this.index = index;
            this.current = current;
            this.obligations = obligations;
            this.xFragment = xFragment;
            this.next = next;
        }

        private BitSet sensitiveAlphabet;

        @Nonnull
        @Override
        public BitSet getSensitiveAlphabet() {
            if (sensitiveAlphabet == null) {
                sensitiveAlphabet = AcceptingComponent.this.getSensitiveAlphabet(current);
                sensitiveAlphabet.or(AcceptingComponent.this.getSensitiveAlphabet(xFragment));

                for (EquivalenceClass clazz : next) {
                    sensitiveAlphabet.or(AcceptingComponent.this.getSensitiveAlphabet(clazz));
                }
            }

            return (BitSet) sensitiveAlphabet.clone();
        }

        @Nullable
        public Edge<State> getSuccessor(BitSet valuation) {
            EquivalenceClass nextXFragment = AcceptingComponent.this.getSuccessor(xFragment, valuation).andWith(obligations.xFragment);

            if (nextXFragment.isFalse()) {
                return null;
            }

            EquivalenceClass currentSuccessor = AcceptingComponent.this.getSuccessor(current, valuation, nextXFragment);

            if (currentSuccessor.isFalse()) {
                freeClasses(nextXFragment);
                return null;
            }

            EquivalenceClass assumptions = currentSuccessor.and(nextXFragment);

            if (assumptions.isFalse()) {
                freeClasses(nextXFragment, currentSuccessor);
                return null;
            }

            EquivalenceClass[] nextSuccessors = AcceptingComponent.this.getSuccessors(next, valuation, assumptions);

            if (nextSuccessors == null) {
                freeClasses(nextXFragment, currentSuccessor, assumptions);
                return null;
            }

            final int length = obligations.initialStates.length;

            BitSet bs = REJECT;

            boolean obtainNewGoal = false;
            int j = index;

            // Scan for new index if currentSuccessor currentSuccessor is true. In this way we can skip several fullfilled break-points at a time and are not bound to slowly check one by one.
            if (currentSuccessor.isTrue()) {
                obtainNewGoal = true;
                int i = index + 1;

                while (i < length && nextSuccessors[i].isTrue()) {
                    i++;
                }

                if (i >= length) {
                    bs = ACCEPT;

                    // Continue scanning
                    for (i = 0; i < length && nextSuccessors[i].isTrue();) {
                        i++;
                    }
                }

                if (i == length) {
                    j = 0;
                } else {
                    j = i;
                }
            }

            for (int i = 0; i < nextSuccessors.length; i++) {
                EquivalenceClass nextSuccessor = nextSuccessors[i];

                if (obtainNewGoal && i == j) {
                    currentSuccessor = nextSuccessor.and(removeCover(doEagerOpt(obligations.initialStates[i]), assumptions));
                    assumptions = assumptions.and(currentSuccessor);
                    nextSuccessors[i] = equivalenceClassFactory.getTrue();
                } else {
                    nextSuccessors[i] = nextSuccessor.and(removeCover(doEagerOpt(obligations.initialStates[i]), assumptions));
                }
            }

            if (currentSuccessor.isFalse()) {
                freeClasses(nextXFragment, currentSuccessor, assumptions);
                freeClasses(nextSuccessors);
                return null;
            }

            for (EquivalenceClass clazz : nextSuccessors) {
                if (clazz.isFalse()) {
                    freeClasses(nextXFragment, currentSuccessor, assumptions);
                    freeClasses(nextSuccessors);
                    return null;
                }
            }

            assumptions.free();
            return new Edge<>(new State(j, nextXFragment, currentSuccessor, nextSuccessors, obligations), bs);
        }

        @Override
        protected int hashCodeOnce() {
            return Objects.hash(current, obligations, xFragment, index, Arrays.hashCode(next));
        }

        @Override
        protected boolean equals2(ImmutableObject o) {
            State that = (State) o;
            return index == that.index && Objects.equals(xFragment, that.xFragment) && Objects.equals(current, that.current) && Arrays.equals(next, that.next) && Objects.equals(obligations, that.obligations);
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

        private EquivalenceClass orLabel = null;

        public EquivalenceClass getOrLabel() {
            if (orLabel == null) {
                orLabel = current.or(xFragment);

                for (EquivalenceClass clazz : next) {
                    orLabel = orLabel.orWith(clazz);
                }

                for (EquivalenceClass clazz : obligations.initialStates) {
                    orLabel = orLabel.orWith(clazz);
                }
            }

            return orLabel;
        }

        private EquivalenceClass andLabel = null;

        public EquivalenceClass getLabel() {
            if (andLabel == null) {
                andLabel = current.and(xFragment);

                for (EquivalenceClass clazz : next) {
                    andLabel = andLabel.andWith(clazz);
                }

                for (EquivalenceClass clazz : obligations.initialStates) {
                    andLabel = andLabel.andWith(clazz);
                }
            }

            return andLabel;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("State{");
            sb.append("obligations=").append(obligations);
            sb.append(", xFragment=").append(xFragment);
            sb.append(", index=").append(index);
            sb.append(", current=").append(current);
            sb.append(", next=").append(Arrays.toString(next));
            sb.append(", sensitiveAlphabet=").append(sensitiveAlphabet);
            sb.append('}');
            return sb.toString();
        }
    }
}
