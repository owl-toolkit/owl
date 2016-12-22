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

import ltl.GOperator;
import ltl.equivalence.EquivalenceClass;
import ltl.equivalence.EquivalenceClassFactory;
import omega_automaton.AutomatonState;
import omega_automaton.acceptance.GeneralisedBuchiAcceptance;
import omega_automaton.collections.valuationset.ValuationSetFactory;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import translations.Optimisation;
import translations.ldba.AbstractInitialComponent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class InitialComponent<S extends AutomatonState<S>> extends AbstractInitialComponent<InitialComponent.State, S> {

    private static final BitSet ACCEPT;

    static {
        // FIXME: Increase the number of set bits!
        ACCEPT = new BitSet();
        ACCEPT.set(0);
    }

    @Nonnull
    private final AbstractAcceptingComponent<S, ? extends GeneralisedBuchiAcceptance> acceptingComponent;
    @Nonnull
    private final EquivalenceClass initialClazz;

    private final boolean eager;
    private final boolean impatient;
    private final RecurringObligationsSelector selector;

    InitialComponent(@Nonnull EquivalenceClass initialClazz, @Nonnull AbstractAcceptingComponent<S, ? extends GeneralisedBuchiAcceptance> acceptingComponent, ValuationSetFactory valuationSetFactory, Collection<Optimisation> optimisations, EquivalenceClassFactory factory) {
        super(valuationSetFactory);

        this.acceptingComponent = acceptingComponent;
        this.initialClazz = initialClazz;

        eager = optimisations.contains(Optimisation.EAGER_UNFOLD);
        impatient = optimisations.contains(Optimisation.FORCE_JUMPS);
        selector = new RecurringObligationsSelector(optimisations, factory);
    }

    @Override
    public void generateJumps(@Nonnull State state) {
        Map<Set<GOperator>, RecurringObligations> keys = selector.selectMonitors(state);

        keys.forEach((Gs, obligation) -> {
            if (Gs.isEmpty()) {
                return;
            }

            EquivalenceClass remainingGoal = selector.getRemainingGoal(state.getClazz().getRepresentative(), Gs);
            S successor = acceptingComponent.jump(remainingGoal, obligation);

            if (successor == null) {
                return;
            }

            epsilonJumps.put(state, successor);
        });
    }

    @Override
    protected State generateInitialState() {
        if (eager) {
            return new State(initialClazz.unfold(), true, impatient, valuationSetFactory, selector);
        } else {
            return new State(initialClazz, false, impatient, valuationSetFactory, selector);
        }
    }

    public static class State implements AutomatonState<State> {

        private final EquivalenceClass clazz;
        private final boolean eager;
        private final boolean impatient;
        private final ValuationSetFactory valuationSetFactory;
        private Map<Set<GOperator>, RecurringObligations> jumps;
        private final RecurringObligationsSelector selector;

        public State(EquivalenceClass clazz, boolean eager, boolean impatient, ValuationSetFactory valuationSetFactory, RecurringObligationsSelector selector) {
            this.clazz = clazz;
            this.eager = eager;
            this.impatient = impatient;
            this.valuationSetFactory = valuationSetFactory;
            this.selector = selector;
        }

        @Nullable
        @Override
        public Edge<State> getSuccessor(@Nonnull BitSet valuation) {
            EquivalenceClass successorClass;

            if (eager) {
                successorClass = clazz.temporalStepUnfold(valuation);
            } else {
                successorClass = clazz.unfoldTemporalStep(valuation);
            }

            if (jumps == null) {
                jumps = selector.selectMonitors(this);
            }

            // Suppress edge, if successor is a non-accepting state
            if (successorClass.isFalse() || !jumps.containsKey(Collections.<GOperator>emptySet())) {
                return null;
            }

            State successor = new State(successorClass, eager, impatient, valuationSetFactory, selector);
            return successorClass.isTrue() ? Edges.create(successor, ACCEPT) : Edges.create(successor);
        }

        @Nonnull
        @Override
        public BitSet getSensitiveAlphabet() {
            if (eager) {
                return clazz.getAtoms();
            } else {
                EquivalenceClass unfold = clazz.unfold();
                BitSet atoms = clazz.getAtoms();
                unfold.free();
                return atoms;
            }
        }

        @Override
        public String toString() {
            return clazz.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }

            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            State that = (State) o;
            return Objects.equals(clazz, that.clazz);
        }

        @Override
        public int hashCode() {
            return clazz.hashCode();
        }

        public EquivalenceClass getClazz() {
            return clazz;
        }

        public void free() {
            clazz.free();
        }
    }
}
