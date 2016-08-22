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
import ltl.visitors.predicates.XFragmentPredicate;
import omega_automaton.*;
import omega_automaton.acceptance.BuchiAcceptance;
import omega_automaton.acceptance.GeneralisedBuchiAcceptance;
import omega_automaton.collections.valuationset.ValuationSetFactory;
import ltl.equivalence.EquivalenceClass;
import ltl.equivalence.EquivalenceClassFactory;
import translations.Optimisation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class GeneralisedAcceptingComponent extends AbstractAcceptingComponent<GeneralisedAcceptingComponent.State, GeneralisedBuchiAcceptance> {

    GeneralisedAcceptingComponent(EquivalenceClassFactory factory, ValuationSetFactory valuationSetFactory, EnumSet<Optimisation> optimisations) {
        super(new BuchiAcceptance(), optimisations, valuationSetFactory, factory);
    }

    @Override
    State createState(EquivalenceClass remainder, RecurringObligations obligations) {
        // If it is necessary, increase the number of acceptance conditions.
        if (obligations.initialStates.length > acceptance.getSize()) {
            acceptance = new GeneralisedBuchiAcceptance(obligations.initialStates.length);
        }

        final int length = obligations.initialStates.length;

        EquivalenceClass xFragment = obligations.xFragment;
        EquivalenceClass[] currentBuilder = new EquivalenceClass[length];

        if (remainder.getRepresentative().accept(XFragmentPredicate.INSTANCE)) {
            xFragment = remainder.andWith(xFragment);

            if (length > 0) {
                currentBuilder[0] = doEagerOpt(obligations.initialStates[0]);
            }
        } else {
            EquivalenceClass current = remainder;

            if (length > 0) {
                currentBuilder[0] = doEagerOpt(current.andWith(obligations.initialStates[0]));
            } else {
                currentBuilder = new EquivalenceClass[]{doEagerOpt(current)};
            }
        }


        for (int i = 1; i < length; i++) {
            currentBuilder[i] = doEagerOpt(obligations.initialStates[i]);
        }

        EquivalenceClass[] next = new EquivalenceClass[length];
        Arrays.fill(next, equivalenceClassFactory.getTrue());

        return new State(obligations, xFragment, currentBuilder, next);
    }

    public final class State extends ImmutableObject implements AutomatonState<GeneralisedAcceptingComponent.State> {

        private final RecurringObligations obligations;
        private final EquivalenceClass xFragment;
        private final EquivalenceClass[] current;
        private final EquivalenceClass[] next;

        private State(RecurringObligations obligations, EquivalenceClass xFragment, EquivalenceClass[] current, EquivalenceClass[] next) {
            this.obligations = obligations;
            this.xFragment = xFragment;
            this.current = current;
            this.next = next;
        }

        @Nullable
        public Edge<State> getSuccessor(BitSet valuation) {
            // Check the X-Fragment first.
            EquivalenceClass nextXFragment = GeneralisedAcceptingComponent.this.getSuccessor(xFragment, valuation);

            if (nextXFragment == null) {
                return null;
            }

            nextXFragment = nextXFragment.andWith(obligations.xFragment);

            if (nextXFragment.isFalse()) {
                return null;
            }

            final int length = obligations.initialStates.length;
            EquivalenceClass[] currentSuccessors = new EquivalenceClass[length];
            EquivalenceClass[] nextSuccessors = new EquivalenceClass[length];

            // Create acceptance set and set all unused indices to 1.
            BitSet bs = new BitSet();
            bs.set(length, acceptance.getSize());

            // There is only the remainder to be checked.
            if (next.length == 0) {
                if (0 != current.length) {
                    EquivalenceClass successor = GeneralisedAcceptingComponent.this.getSuccessor(current[0], valuation, nextXFragment);

                    if (successor == null) {
                        return null;
                    }

                    if (!successor.isTrue()) {
                        return new Edge<>(new State(obligations, nextXFragment, new EquivalenceClass[]{successor}, nextSuccessors), REJECT);
                    }
                }

                return new Edge<>(new State(obligations, nextXFragment, currentSuccessors, nextSuccessors), bs);
            }

            for (int i = 0; i < length; i++) {
                EquivalenceClass currentSuccessor = GeneralisedAcceptingComponent.this.getSuccessor(current[i], valuation, nextXFragment);

                if (currentSuccessor == null) {
                    return null;
                }

                EquivalenceClass assumptions = currentSuccessor.and(nextXFragment);
                EquivalenceClass nextSuccessor = GeneralisedAcceptingComponent.this.getSuccessor(next[i], valuation, assumptions);

                if (nextSuccessor == null) {
                    assumptions.free();
                    return null;
                }

                nextSuccessor = nextSuccessor.andWith(removeCover(doEagerOpt(obligations.initialStates[i]), assumptions));

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

            return new Edge<>(new State(obligations, nextXFragment, currentSuccessors, nextSuccessors), bs);
        }

        private BitSet sensitiveAlphabet;

        @Nonnull
        public BitSet getSensitiveAlphabet() {
            if (sensitiveAlphabet == null) {
                sensitiveAlphabet = xFragment.unfold().getAtoms();

                for (EquivalenceClass clazz : current) {
                    sensitiveAlphabet.or(clazz.unfold().getAtoms());
                }

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

        @Override
        protected int hashCodeOnce() {
            return Objects.hash(xFragment, obligations, Arrays.hashCode(current), Arrays.hashCode(next));
        }

        @Override
        protected boolean equals2(ImmutableObject o) {
            State that = (State) o;
            return Objects.equals(xFragment, that.xFragment) && Objects.equals(obligations, that.obligations) && Arrays.equals(current, that.current) && Arrays.equals(next, that.next);
        }

        @Override
        public void free() {
            // current.forEach(EquivalenceClass::free);
            // next.forEach(EquivalenceClass::free);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("State{");
            sb.append("obligations=").append(obligations);
            sb.append(", xFragment=").append(xFragment);
            sb.append(", current=").append(Arrays.toString(current));
            sb.append(", next=").append(Arrays.toString(next));
            sb.append(", sensitiveAlphabet=").append(sensitiveAlphabet);
            sb.append('}');
            return sb.toString();
        }
    }
}
