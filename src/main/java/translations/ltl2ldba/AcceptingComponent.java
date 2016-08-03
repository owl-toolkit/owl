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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
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
import java.util.stream.Stream;

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

        Iterator<EquivalenceClass> iterator = obligations.initialStates.iterator();
        Builder<EquivalenceClass> nextBuilder = ImmutableList.builder();

        if (remainder.getRepresentative().accept(XFragmentPredicate.INSTANCE)) {
            xFragment = remainder.andWith(xFragment);

            if (iterator.hasNext()) {
                current = doEagerOpt(iterator.next());
                nextBuilder.add(True);
            }
        } else {
            current = doEagerOpt(remainder);

            if (iterator.hasNext()) {
                nextBuilder.add(removeCover(doEagerOpt(iterator.next()), current));
            }
        }

        iterator.forEachRemaining(eq -> nextBuilder.add(doEagerOpt(eq)));
        return new State(0, xFragment, current, nextBuilder.build(), obligations);
    }

    public class State extends ImmutableObject implements AutomatonState<State> {

        private final RecurringObligations obligations;
        private final EquivalenceClass xFragment;
        private final int index;
        private final EquivalenceClass current;
        private final ImmutableList<EquivalenceClass> next;

        public State(int index, EquivalenceClass xFragment, EquivalenceClass current, ImmutableList<EquivalenceClass> next, RecurringObligations obligations) {
            this.index = index;
            this.current = current;
            this.obligations = obligations;
            this.xFragment = xFragment;
            this.next = next;
        }

        @Nonnull
        @Override
        public BitSet getSensitiveAlphabet() {
            BitSet sensitiveLetters = AcceptingComponent.this.getSensitiveAlphabet(current);
            sensitiveLetters.or(AcceptingComponent.this.getSensitiveAlphabet(xFragment));
            next.forEach(clazz -> sensitiveLetters.or(AcceptingComponent.this.getSensitiveAlphabet(clazz)));
            return sensitiveLetters;
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

            Builder<EquivalenceClass> nextBuilder = ImmutableList.builder();
            final int width = obligations.initialStates.size();

            EquivalenceClass successor = AcceptingComponent.this.getSuccessor(current, valuation, nextXFragment);

            if (successor == null) {
                return null;
            }

            boolean obtainNewGoal = false;
            int j = index;

            if (successor.isTrue()) {
                j++;
                obtainNewGoal = true;


            }

            // Accept if all components were able to satisfy the goal.
            BitSet bs = j < width ? REJECT : ACCEPT;

            if (j >= width) {
                j = 0;
            }

            for (int i = 0; i < width; i++) {
                EquivalenceClass nextClass = next.get(i);
                EquivalenceClass assumptions = current.and(nextXFragment);
                EquivalenceClass nextSuccessor = AcceptingComponent.this.getSuccessor(nextClass, valuation, assumptions);

                if (nextSuccessor == null) {
                    assumptions.free();
                    return null;
                }

                EquivalenceClass initialClass = obligations.initialStates.get(i);

                // Do cover optimisation
                if (!removeCover || !successor.implies(doEagerOpt(initialClass))) {
                    nextSuccessor = nextSuccessor.and(doEagerOpt(initialClass));
                }

                if (obtainNewGoal && i == j) {
                    successor = nextSuccessor;
                    nextBuilder.add(True);
                } else {
                    nextBuilder.add(nextSuccessor);
                }

                assumptions.free();
            }

            return new Edge<>(new State(j, nextXFragment, successor, nextBuilder.build(), obligations), bs);
        }

        @Override
        protected int hashCodeOnce() {
            return Objects.hash(current, next, obligations, xFragment, index);
        }

        @Override
        protected boolean equals2(ImmutableObject o) {
            State that = (State) o;
            return index == that.index && Objects.equals(xFragment, that.xFragment) && Objects.equals(current, that.current) && Objects.equals(next, that.next) && Objects.equals(obligations, that.obligations);
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
            return Stream.concat(next.stream(), Stream.concat(obligations.initialStates.stream(), Stream.of(current, xFragment))).reduce(equivalenceClassFactory.getTrue(), EquivalenceClass::andWith);
        }
    }
}
