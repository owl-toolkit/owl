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
        if (obligations.initialStates.size() > acceptance.getSize()) {
            acceptance = new GeneralisedBuchiAcceptance(obligations.initialStates.size());
        }

        EquivalenceClass xFragment = obligations.xFragment;
        Builder<EquivalenceClass> currentBuilder = ImmutableList.builder();
        Iterator<EquivalenceClass> iterator = obligations.initialStates.iterator();

        if (remainder.getRepresentative().accept(XFragmentPredicate.INSTANCE)) {
            xFragment = remainder.andWith(xFragment);
        } else {
            EquivalenceClass current = remainder;

            if (iterator.hasNext()) {
                current = current.andWith(iterator.next());
            }

            currentBuilder.add(doEagerOpt(current));
        }

        iterator.forEachRemaining(eq -> currentBuilder.add(doEagerOpt(eq)));
        return new State(obligations, xFragment, currentBuilder.build(), Collections.nCopies(obligations.initialStates.size(), True));
    }

    public class State extends ImmutableObject implements AutomatonState<GeneralisedAcceptingComponent.State> {

        private final RecurringObligations obligations;
        private final EquivalenceClass xFragment;
        private final ImmutableList<EquivalenceClass> current;
        private final ImmutableList<EquivalenceClass> next;

        private State(RecurringObligations obligations, EquivalenceClass xFragment, List<EquivalenceClass> current, List<EquivalenceClass> next) {
            this.obligations = obligations;
            this.xFragment = xFragment;
            this.current = ImmutableList.copyOf(current);
            this.next = ImmutableList.copyOf(next);
        }

        @Nullable
        public Edge<State> getSuccessor(BitSet valuation) {
            EquivalenceClass nextXFragment = GeneralisedAcceptingComponent.this.getSuccessor(xFragment, valuation);

            if (nextXFragment == null) {
                return null;
            }

            nextXFragment = nextXFragment.andWith(obligations.xFragment);

            if (nextXFragment.isFalse()) {
                return null;
            }

            ImmutableList.Builder<EquivalenceClass> currentBuilder = ImmutableList.builder();
            ImmutableList.Builder<EquivalenceClass> nextBuilder = ImmutableList.builder();

            final int width = obligations.initialStates.size();

            // Create acceptance set and set all unused indices to 1.
            BitSet bs = new BitSet();
            bs.set(width, acceptance.getSize());

            if (next.isEmpty()) {
                if (current.isEmpty()) {
                    return new Edge<>(new State(obligations, nextXFragment, currentBuilder.build(), nextBuilder.build()), bs);
                } else {
                    EquivalenceClass successor = GeneralisedAcceptingComponent.this.getSuccessor(current.get(0), valuation, nextXFragment);

                    if (successor == null) {
                        return null;
                    }

                    if (successor.isTrue()) {
                        return new Edge<>(new State(obligations, nextXFragment, currentBuilder.build(), nextBuilder.build()), bs);
                    } else {
                        currentBuilder.add(successor);
                        return new Edge<>(new State(obligations, nextXFragment, currentBuilder.build(), nextBuilder.build()), REJECT);
                    }
                }
            }

            for (int i = 0; i < width; i++) {
                EquivalenceClass currentClass = current.get(i);
                EquivalenceClass nextClass = next.get(i);
                EquivalenceClass initialClass = obligations.initialStates.get(i);
                EquivalenceClass successor = GeneralisedAcceptingComponent.this.getSuccessor(currentClass, valuation, nextXFragment);

                if (successor == null) {
                    return null;
                }

                EquivalenceClass assumptions = successor.and(nextXFragment);
                EquivalenceClass nextSuccessor = GeneralisedAcceptingComponent.this.getSuccessor(nextClass, valuation, assumptions);

                if (nextSuccessor == null) {
                    assumptions.free();
                    return null;
                }

                // Do Cover optimisation
                if (!removeCover || !assumptions.implies(doEagerOpt(initialClass))) {
                    nextSuccessor = nextSuccessor.and(doEagerOpt(initialClass));
                }

                // Successor is done and we can switch components.
                if (successor.isTrue()) {
                    bs.set(i);
                    currentBuilder.add(nextSuccessor);
                    nextBuilder.add(True);
                } else {
                    currentBuilder.add(successor);
                    nextBuilder.add(nextSuccessor);
                }

                assumptions.free();
            }

            return new Edge<>(new State(obligations, nextXFragment, currentBuilder.build(), nextBuilder.build()), bs);
        }

        private BitSet sensitiveAlphabet;

        @Nonnull
        public BitSet getSensitiveAlphabet() {
            if (sensitiveAlphabet == null) {
                sensitiveAlphabet = GeneralisedAcceptingComponent.this.getSensitiveAlphabet(xFragment);
                current.forEach(clazz -> sensitiveAlphabet.or(GeneralisedAcceptingComponent.this.getSensitiveAlphabet(clazz)));
                next.forEach(clazz -> sensitiveAlphabet.or(GeneralisedAcceptingComponent.this.getSensitiveAlphabet(clazz)));
            }

            return (BitSet) sensitiveAlphabet.clone();
        }

        @Override
        public ValuationSetFactory getFactory() {
            return valuationSetFactory;
        }

        @Override
        protected int hashCodeOnce() {
            return Objects.hash(xFragment, obligations, current, next);
        }

        @Override
        protected boolean equals2(ImmutableObject o) {
            State that = (State) o;
            return Objects.equals(xFragment, that.xFragment) && Objects.equals(obligations, that.obligations) && Objects.equals(current, that.current) && Objects.equals(next, that.next);
        }

        @Override
        public void free() {
            //current.forEach(EquivalenceClass::free);
            //next.forEach(EquivalenceClass::free);
        }

        @Override
        public String toString() {
            return "State{" + "obligations=" + obligations +
                    ", xFragment=" + xFragment +
                    ", current=" + current +
                    ", next=" + next +
                    '}';
        }
    }
}
