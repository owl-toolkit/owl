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

import ltl.*;
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
                nextBuilder[0] = (removeCover(doEagerOpt(obligations.initialStates[0]), current));
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
                sensitiveAlphabet = current.unfold().getAtoms();
                sensitiveAlphabet.or(xFragment.unfold().getAtoms());

                for (EquivalenceClass clazz : next) {
                    sensitiveAlphabet.or(clazz.unfold().getAtoms());
                }
            }

            return (BitSet) sensitiveAlphabet.clone();
        }

        @Override
        public ValuationSetFactory getFactory() {
            return valuationSetFactory;
        }

        @Nullable
        public Edge<State> getSuccessor(BitSet valuation) {
            EquivalenceClass nextXFragment = AcceptingComponent.this.getSuccessor(xFragment, valuation);

            if (nextXFragment == null) {
                return null;
            }

            nextXFragment = nextXFragment.andWith(obligations.xFragment);

            if (nextXFragment.isFalse()) {
                return null;
            }

            EquivalenceClass currentSuccessor = AcceptingComponent.this.getSuccessor(current, valuation, nextXFragment);

            if (currentSuccessor == null) {
                return null;
            }

            boolean obtainNewGoal = false;
            int j = index;

            BitSet bs = REJECT;

            final int length = obligations.initialStates.length;

            if (currentSuccessor.isTrue()) {
                j++;
                obtainNewGoal = true;

                if (j >= length) {
                    bs = ACCEPT;
                    j = 0;
                }
            }

            EquivalenceClass assumptions = currentSuccessor.and(nextXFragment);
            EquivalenceClass[] nextSuccessors = new EquivalenceClass[length];

            for (int i = 0; i < length; i++) {
                EquivalenceClass nextSuccessor = AcceptingComponent.this.getSuccessor(next[i], valuation, assumptions);

                if (nextSuccessor == null) {
                    assumptions.free();
                    return null;
                }

                nextSuccessor = nextSuccessor.andWith(removeCover(doEagerOpt(obligations.initialStates[i]), assumptions));

                if (obtainNewGoal && i == j) {
                    currentSuccessor = nextSuccessor;
                    nextSuccessors[i] = equivalenceClassFactory.getTrue();
                } else {
                    nextSuccessors[i] = nextSuccessor;
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

        public EquivalenceClass getLabel() {
            EquivalenceClass label = current.and(xFragment);

            for (EquivalenceClass clazz : next) {
                label = label.andWith(clazz);
            }

            for (EquivalenceClass clazz : obligations.initialStates) {
                label = label.andWith(clazz);
            }

            return label;
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
