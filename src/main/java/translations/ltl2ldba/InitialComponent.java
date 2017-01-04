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

import ltl.equivalence.EquivalenceClass;
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
    private final boolean eager;
    private final RecurringObligationsSelector selector;

    protected InitialComponent(@Nonnull EquivalenceClass initialClazz, @Nonnull AbstractAcceptingComponent<S, ? extends GeneralisedBuchiAcceptance> acceptingComponent, ValuationSetFactory valuationSetFactory, Collection<Optimisation> optimisations, RecurringObligationsSelector selector) {
        super(valuationSetFactory);

        this.acceptingComponent = acceptingComponent;

        eager = optimisations.contains(Optimisation.EAGER_UNFOLD);
        this.selector = selector;
        this.initialState = new State(this, eager ? initialClazz.unfold() : initialClazz);
    }

    @Override
    public void generateJumps(@Nonnull State state) {
        selector.selectMonitors(state).forEach((obligation) -> {
            if (obligation.isEmpty()) {
                return;
            }

            EquivalenceClass remainingGoal = selector.getRemainingGoal(state.getClazz(), obligation);
            S successor = acceptingComponent.jump(remainingGoal, obligation);

            if (successor == null) {
                return;
            }

            epsilonJumps.put(state, successor);
        });
    }

    public static class State implements AutomatonState<State> {

        private final InitialComponent<?> parent;
        private final EquivalenceClass clazz;
        private Set<RecurringObligations> jumps;

        public State(InitialComponent<?> parent, EquivalenceClass clazz) {
            this.parent = parent;
            this.clazz = clazz;
        }

        @Nullable
        @Override
        public Edge<State> getSuccessor(@Nonnull BitSet valuation) {
            EquivalenceClass successorClass;

            if (parent.eager) {
                successorClass = clazz.temporalStepUnfold(valuation);
            } else {
                successorClass = clazz.unfoldTemporalStep(valuation);
            }

            if (jumps == null) {
                jumps = parent.selector.selectMonitors(this);
            }

            // Suppress edge, if successor is a non-accepting state
            if (successorClass.isFalse() || jumps.stream().noneMatch(RecurringObligations::isEmpty)) {
                return null;
            }

            State successor = new State(parent, successorClass);
            return successorClass.isTrue() ? Edges.create(successor, ACCEPT) : Edges.create(successor);
        }

        @Nonnull
        @Override
        public BitSet getSensitiveAlphabet() {
            if (parent.eager) {
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
